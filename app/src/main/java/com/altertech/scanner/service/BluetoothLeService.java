package com.altertech.scanner.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.core.ExceptionCodes;
import com.altertech.scanner.core.device.Device;
import com.altertech.scanner.core.device.DeviceManagerException;
import com.altertech.scanner.core.service.enums.BLEServiceException;
import com.altertech.scanner.core.service.enums.CharacteristicInstruction;
import com.altertech.scanner.core.service.enums.ServiceInstruction;
import com.altertech.scanner.cryptography.fernet.Crypt;
import com.altertech.scanner.helpers.TaskHelper;
import com.altertech.scanner.utils.NotificationUtils;
import com.altertech.scanner.utils.StringUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class BluetoothLeService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public final static String EXTRA_DATA = "com.altertech.scanner.le.EXTRA_DATA";

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        this.partialServiceState = true;

        LocationRequest mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(0);

        this.keepPartialStop();
        this.keepPartial = new KEEP_PARTIAL();
        TaskHelper.execute(this.keepPartial);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, new LocationListener() {

            private Location last_location;

            @Override
            public void onLocationChanged(Location location) {
                double speed = 0;
                if (this.last_location != null) {
                    double elapsedTime = (location.getTime() - this.last_location.getTime()) / 1000;
                    speed = this.last_location.distanceTo(location) / elapsedTime;
                }
                this.last_location = location;
                speed = (int) (Math.round(3.6 * (location.hasSpeed() ? location.getSpeed() : speed)));
                receiveLocationData = new ReceiveLocationData((int) speed, location.getLatitude(), location.getLongitude());
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        this.partialServiceState = false;
    }

    public enum StatusPair {

        ACTION_GATT_CONNECTING(BluetoothProfile.STATE_CONNECTING, "com.altertech.scanner.le.ACTION_GATT_CONNECTING"),
        ACTION_GATT_CONNECTED(BluetoothProfile.STATE_CONNECTED, "com.altertech.scanner.le.ACTION_GATT_CONNECTED"),
        ACTION_GATT_DISCONNECTING(BluetoothProfile.STATE_DISCONNECTING, "com.altertech.scanner.le.ACTION_GATT_DISCONNECTING"),
        ACTION_GATT_DISCONNECTED(BluetoothProfile.STATE_DISCONNECTED, "com.altertech.scanner.le.ACTION_GATT_DISCONNECTED"),
        ACTION_GATT_DISCOVERING(1000, "com.altertech.scanner.le.ACTION_GATT_DISCOVERING"),
        ACTION_GATT_DISCOVERED(1001, "com.altertech.scanner.le.ACTION_GATT_DISCOVERED"),
        ACTION_GATT_UNKNOWN(1002, "com.altertech.scanner.le.ACTION_GATT_UNKNOWN"),
        ACTION_GATT_DATA_AVAILABLE(1003, "com.altertech.scanner.le.ACTION_GATT_DATA_AVAILABLE"),
        ACTION_GATT_ERROR(1004, "com.altertech.scanner.le.ACTION_GATT_ERROR"),
        ACTION_GATT_KEEP_ONLINE(1005, "com.altertech.scanner.le.ACTION_GATT_KEEP_ONLINE"),
        ACTION_GATT_RECONNECT(1006, "com.altertech.scanner.le.ACTION_GATT_RECONNECT"),
        ACTION_GATT_RECEIVING(1007, "com.altertech.scanner.le.ACTION_GATT_RECEIVING"),
        ACTION_LOCATION_SERVICE_DATA(1008, "com.altertech.scanner.le.ACTION_GATT_LOCATION_SERVICE_DATA"),
        ACTION_LOCATION_SERVICE_ENABLED(1009, "com.altertech.scanner.le.ACTION_GATT_LOCATION_SERVICE_ENABLED"),
        ACTION_LOCATION_SERVICE_DISABLED(10010, "com.altertech.scanner.le.ACTION_GATT_LOCATION_SERVICE_DISABLED");
        /* ACTION_GATT_NEW_DATA_NOT_AVAILABLE(1008, "com.altertech.scanner.le.ACTION_GATT_NEW_DATA_NOT_AVAILABLE");*/

        int id;
        String action;

        StatusPair(int id, String action) {
            this.id = id;
            this.action = action;
        }

        public int getId() {
            return id;
        }

        public String getAction() {
            return action;
        }

        public static StatusPair getById(int id) {
            for (StatusPair item : values()) {
                if (item.id == id) {
                    return item;
                }
            }
            return ACTION_GATT_UNKNOWN;
        }

        public static IntentFilter getIntentFilter() {
            final IntentFilter filter = new IntentFilter();
            for (StatusPair item : values()) {
                filter.addAction(item.getAction());
            }
            return filter;
        }
    }

    private final static byte[] DATA_HEART_RATE_READ_PULSE = new byte[]{0x15, 0x01, 0x01};
    private final static byte[] DATA_HEART_RATE_KEEP_ONLINE = new byte[]{0x16};
    private final static byte[] DATA_AUTH_KEY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private final IBinder mBinder = new LocalBinder();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt gatt;
    private KEEP_ONLINE keepOnline;
    private UDP_CLIENT_SENDER udpClientSender;

    private KEEP_PARTIAL keepPartial;

    /*data*/
    private ReceiveData receiveData = new ReceiveData(0, new Date());
    private StatusPair status = StatusPair.ACTION_GATT_DISCONNECTED;
    private StatusPair statusProgressUI = StatusPair.ACTION_GATT_DISCONNECTED;

    private ReceiveLocationData receiveLocationData = new ReceiveLocationData(-1, -1, -1);

    private AccelerometerBuffer accelerometerBuffer = new AccelerometerBuffer();

    private boolean isOnline = false;

    public boolean logEnabled = true;

    private StringBuilder log = new StringBuilder();

    public void setLogEnabled(boolean logEnabled) {
        this.log = new StringBuilder();
        this.log.append("DEBUG STARTED");
        this.logEnabled = logEnabled;
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BaseApplication.get(BluetoothLeService.this).setDevice(new Device(gatt.getDevice().getName(), gatt.getDevice().getAddress()).getPair());
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_CONNECTED, false);
                BluetoothLeService.this.gatt.discoverServices();
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DISCOVERING, false);
            } else {

                if (newState == BluetoothProfile.STATE_DISCONNECTED && BluetoothLeService.this.keepOnline != null && BluetoothLeService.this.keepOnline.check) {
                    BluetoothLeService.this.reconnect();
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.getById(newState), false);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DISCOVERED, false);
                try {
                    BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_HEART_RATE, CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT);
                    BluetoothLeService.this.setCharacteristicNotification(characteristic, true);
                } catch (BLEServiceException e) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED) && (e.getCode() == ExceptionCodes.GATT_SERVICE_NOT_FOUND.getCode() || e.getCode() == ExceptionCodes.GATT_CHARACTERISTIC_NOT_FOUND.getCode())) {
                        BluetoothLeService.this.disconnect();
                    }
                }
            } else {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onServicesDiscovered(), BluetoothGatt.STATUS = " + status, false);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT.getUuid())) {
                if (characteristic.getValue() != null && characteristic.getValue().length >= 2) {
                    int data = parsePulseValue(characteristic);
                    if (new Date().getTime() - BluetoothLeService.this.receiveData.dateSend.getTime() >= (BaseApplication.get(BluetoothLeService.this).getServerTTS() * 1000)) {
                        BluetoothLeService.this.receiveData = new ReceiveData(data, new Date());
                        try {
                            BluetoothLeService.this.send(getReceiveData().getDataMessage(1));
                        } catch (Exception e) {
                            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getMessage(), false);
                        }
                    } else {
                        BluetoothLeService.this.receiveData = new ReceiveData(data, BluetoothLeService.this.receiveData.getDateSend());
                    }
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DATA_AVAILABLE, String.valueOf(data), false);
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DATA_AVAILABLE, "error -> case -> (value != null && value.length >= 2) -> value == " + (characteristic.getValue() != null ? StringUtil.arrayAsString(characteristic.getValue()) : "null"), false);
                }
            } else if (characteristic.getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid())) {
                if (Arrays.equals(characteristic.getValue(), new byte[]{16, 3, 4})) { /*bad secret*/
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "trying to pairing devices", false);
                    try {
                        BluetoothLeService.this.writeCharacteristic(characteristic, concat(new byte[]{1, 8}, DATA_AUTH_KEY));
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    }
                } else if (Arrays.equals(characteristic.getValue(), new byte[]{16, 1, 1})) {
                    try {
                        BluetoothLeService.this.writeCharacteristic(characteristic, new byte[]{2, 8});
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    }
                } else if (characteristic.getValue().length == 19 && Arrays.equals(Arrays.copyOfRange(characteristic.getValue(), 0, 3), new byte[]{16, 2, 1})) {
                    byte[] b16 = Arrays.copyOfRange(characteristic.getValue(), characteristic.getValue().length - 16, characteristic.getValue().length);
                    try {
                        BluetoothLeService.this.writeCharacteristic(characteristic, getEncryptKey(new byte[]{3, 8}, b16));
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    }
                } else if (Arrays.equals(characteristic.getValue(), new byte[]{16, 3, 1})) {
                    try {
                        BluetoothGattCharacteristic hr_characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_HEART_RATE, CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT);
                        BluetoothLeService.this.setCharacteristicNotification(hr_characteristic, true);
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                        if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED) && (e.getCode() == ExceptionCodes.GATT_SERVICE_NOT_FOUND.getCode() || e.getCode() == ExceptionCodes.GATT_CHARACTERISTIC_NOT_FOUND.getCode())) {
                            BluetoothLeService.this.disconnect();
                        }
                    }
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onCharacteristicChanged wrong auth code = " + StringUtil.arrayAsString(characteristic.getValue()), false);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_DATA_WRITE.getUuid())) {
                if (characteristic.getValue() == DATA_HEART_RATE_KEEP_ONLINE) {
                    //BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline = new Date();
                } else if (characteristic.getValue() == DATA_HEART_RATE_READ_PULSE) {
                    //BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline = new Date();
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_RECEIVING, false);
                }
            } else if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid())) {

            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onCharacteristicWrite(), BluetoothGatt.STATUS = " + status + " data -> " + StringUtil.arrayAsString(characteristic.getValue()), false);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.getCharacteristic().getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT.getUuid())) {
                try {
                    BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_HEART_RATE, CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_DATA_WRITE);
                    BluetoothLeService.this.writeCharacteristic(characteristic, DATA_HEART_RATE_READ_PULSE);
                    BluetoothLeService.this.runAfterSuccessfulConnection(characteristic);
                } catch (BLEServiceException e) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED) && (e.getCode() == ExceptionCodes.GATT_SERVICE_NOT_FOUND.getCode() || e.getCode() == ExceptionCodes.GATT_CHARACTERISTIC_NOT_FOUND.getCode())) {
                        //BluetoothLeService.this.disconnect();
                        BluetoothLeService.this.runAfterSuccessfulConnection(null);
                    }
                }
                //BluetoothLeService.this.runAfterSuccessfulConnection(null);
            } else if (status == BluetoothGatt.GATT_SUCCESS && descriptor.getCharacteristic().getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid())) {
                try {
                    BluetoothLeService.this.writeCharacteristic(descriptor.getCharacteristic(), new byte[]{2, 8});
                } catch (BLEServiceException e) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                if (status == 3) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "trying to authenticate", false);
                    try {
                        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_AUTH, CharacteristicInstruction.CHARACTERISTIC_AUTH);
                        BluetoothLeService.this.setCharacteristicNotification(characteristic, true);
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                        if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED) && (e.getCode() == ExceptionCodes.GATT_SERVICE_NOT_FOUND.getCode() || e.getCode() == ExceptionCodes.GATT_CHARACTERISTIC_NOT_FOUND.getCode())) {
                            BluetoothLeService.this.disconnect();
                        }
                    }
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onDescriptorWrite(), BluetoothGatt.STATUS = " + status, false);
                }
            }
        }
    };

    @SuppressLint("StaticFieldLeak")
    private class KEEP_PARTIAL extends AsyncTask<Void, Void, Void> {

        private boolean check = true;

        void setCheck(boolean check) {
            this.check = check;
        }

        protected void onPreExecute() {
            BluetoothLeService.this.startForegroundNotification(false);
        }

        protected Void doInBackground(Void... voids) {
            int i = 0;
            while (check) {
                sleep(1000);
                if (check /*&& !BluetoothLeService.this.foregroundNotificationEnabled*/) {
                    NotificationUtils.show(BluetoothLeService.this, NotificationUtils.ChannelId.DEFAULT, getStatusDescriptionByStatusUI(), receiveLocationData.getData());
                }
                if (i == 1) {
                    try {
                        BluetoothLeService.this.send(receiveLocationData.getTrLat(),
                                receiveLocationData.getTrLon(),
                                receiveLocationData.getTrSpeed(),
                                accelerometerBuffer.getTrMaxDelta(0),
                                accelerometerBuffer.getTrMaxDelta(1),
                                accelerometerBuffer.getTrMaxDelta(2)
                        );
                        accelerometerBuffer = new AccelerometerBuffer();
                    } catch (Exception e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getMessage(), false);
                    }
                    i = 0;
                } else {
                    i++;
                }
            }
            return null;
        }
    }

    public boolean partialServiceState = false;

    private GoogleApiClient mGoogleApiClient;

    @SuppressLint("MissingPermission")
    public void startPartialService() throws DeviceManagerException {
        if (this.mGoogleApiClient == null || !this.mGoogleApiClient.isConnected()) {
            this.mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            this.mGoogleApiClient.connect();
        }
        try {
            this.initializationAccelerometer();
        } catch (BLEServiceException e) {
            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
        }
    }

    public void stopPartialService() {
        this.disconnect_partial();
    }

    private SensorManager sensorManager;

    private void initializationAccelerometer() throws BLEServiceException {
        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (this.sensorManager != null) {
            Sensor accelerometer = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                this.accelerometerBuffer = new AccelerometerBuffer();
                this.sensorManager.registerListener(this.sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                throw new BLEServiceException(ExceptionCodes.SENSOR_ACCELEROMETER_NOT_SUPPORTED);
            }

        } else {
            throw new BLEServiceException(ExceptionCodes.SENSOR_NOT_SUPPORTED);

        }
    }

    private void disposeAccelerometer() {
        if (this.sensorManager != null) {
            this.sensorManager.unregisterListener(sensorEventListener);
        }
        this.accelerometerBuffer = new AccelerometerBuffer();
    }

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            Sensor mySensor = sensorEvent.sensor;
            if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float x = sensorEvent.values[0];
                float y = sensorEvent.values[1];
                float z = sensorEvent.values[2];
                BluetoothLeService.this.accelerometerBuffer.put(0, x);
                BluetoothLeService.this.accelerometerBuffer.put(1, y);
                BluetoothLeService.this.accelerometerBuffer.put(2, z);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    @Override
    public void onCreate() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "onRebind()");
    }

    @Override
    public void onDestroy() {
        this.stopForegroundNotification_sensor();
        this.stopForegroundNotification_partial();
    }

    public void check() throws DeviceManagerException {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            this.bluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (this.bluetoothAdapter == null) {
            throw new DeviceManagerException(ExceptionCodes.BLUETOOTH_NOT_SUPPORTED);
        } else {
            if (!this.bluetoothAdapter.isEnabled()) {
                throw new DeviceManagerException(ExceptionCodes.BLUETOOTH_TO_ENABLE);
            }
        }
    }

    public void connect(final String address, StatusPair status) {
        this.setStatusAndSendBroadcast(status, false);
        if (this.gatt != null) {
            this.gatt.close();
        }
        this.gatt = bluetoothAdapter.getRemoteDevice(address).connectGatt(this, false, bluetoothGattCallback);
    }

    public void reconnect() {
        BluetoothLeService.this.getReceiveData().setupDateToNow();
        BluetoothLeService.this.connect(BaseApplication.get(BluetoothLeService.this).getAddress(), StatusPair.ACTION_GATT_RECONNECT);
    }

    public void disconnect() {
        this.keepOnlineStop();
        this.stopForegroundNotification_sensor();
        if (this.gatt != null) {
            this.gatt.disconnect();
            this.gatt.close();
        }
        this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DISCONNECTED, false);
    }

    public void disconnect_partial() {
        this.partialServiceState = false;
        this.keepPartialStop();
        this.stopForegroundNotification_partial();
        if (this.mGoogleApiClient != null) {
            this.mGoogleApiClient.disconnect();
            this.mGoogleApiClient = null;
        }
        this.disposeAccelerometer();
    }

    public void quit() {
        this.disconnect();
        this.disconnect_partial();
    }

    public void runAfterSuccessfulConnection(BluetoothGattCharacteristic characteristic) {
        BluetoothLeService.this.keepOnlineStartIfNeed(characteristic);
        BluetoothLeService.this.startForegroundNotification(true);
    }

    private BluetoothGattCharacteristic getBluetoothGattCharacteristic(BluetoothGatt gatt, ServiceInstruction serviceInstruction, CharacteristicInstruction characteristicInstruction) throws BLEServiceException {
        BluetoothGattService service = gatt.getService(serviceInstruction.getUuid());
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicInstruction.getUuid());
            if (characteristic != null) {
                return characteristic;
            } else {
                throw new BLEServiceException(ExceptionCodes.GATT_CHARACTERISTIC_NOT_FOUND, characteristicInstruction.getUuid().toString());
            }
        } else {
            throw new BLEServiceException(ExceptionCodes.GATT_SERVICE_NOT_FOUND, serviceInstruction.getUuid().toString());
        }
    }

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) throws BLEServiceException {
        if (this.gatt == null || !this.status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
            return;
        }
        characteristic.setValue(data);
        boolean result = this.gatt.writeCharacteristic(characteristic);
        if (!result) {
            throw new BLEServiceException(ExceptionCodes.GATT_BAD_ACTION, "write characteristic = " + StringUtil.arrayAsString(data));
        }
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (this.bluetoothAdapter != null && this.gatt != null) {
            if (!this.gatt.setCharacteristicNotification(characteristic, enabled)) {
                this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "set characteristic notification = " + characteristic.getUuid().toString(), false);
            }
            if (CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT.getUuid().equals(characteristic.getUuid())) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_CONFIG.getUuid());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!this.gatt.writeDescriptor(descriptor)) {
                    this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "write descriptor = " + StringUtil.arrayAsString(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE), false);
                }
            } else if (CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid().equals(characteristic.getUuid())) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CharacteristicInstruction.CHARACTERISTIC_AUTH_DESCRIPTOR.getUuid());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!this.gatt.writeDescriptor(descriptor)) {
                    this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "write descriptor = " + StringUtil.arrayAsString(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE), false);
                }
            }

        }
    }

    private void keepOnlineStartIfNeed(BluetoothGattCharacteristic characteristic) {
        if (this.keepOnline != null && keepOnline.getStatus().equals(AsyncTask.Status.RUNNING) && this.keepOnline.check) {
            this.keepOnline.setCharacteristic(characteristic);
        } else if (this.keepOnline == null || (this.keepOnline != null && keepOnline.getStatus().equals(AsyncTask.Status.FINISHED))) {
            this.keepOnline = new KEEP_ONLINE(characteristic);
            TaskHelper.execute(this.keepOnline);
        } else if (this.keepOnline != null && keepOnline.getStatus().equals(AsyncTask.Status.RUNNING) && !this.keepOnline.check) {
            this.keepOnline.setCheck(true);
            this.keepOnline.setCharacteristic(characteristic);
        }
    }

    private void keepOnlineStop() {
        if (this.keepOnline != null && /*keepOnline.getStatus().equals(AsyncTask.Status.RUNNING) &&*/ this.keepOnline.check) {
            this.keepOnline.setCheck(false);
        }
    }

    private void keepPartialStop() {
        if (this.keepPartial != null && /*keepOnline.getStatus().equals(AsyncTask.Status.RUNNING) &&*/ this.keepPartial.check) {
            this.keepPartial.setCheck(false);
        }
    }

    private byte[] getEncryptKey(byte[] prefix, byte[] key) {
        try {
            @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(DATA_AUTH_KEY, "AES"));
            return concat(prefix, cipher.doFinal(key));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            return null;
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public ReceiveData getReceiveData() {
        return receiveData != null ? receiveData : new ReceiveData(0, new Date());
    }

    public StatusPair getStatus() {
        return status;
    }

    public StatusPair getStatusProgressUI() {
        return statusProgressUI;
    }

    private void sleep(int m) {
        try {
            Thread.sleep(m);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean foregroundNotificationEnabled = false;

    private void startForegroundNotification(boolean sensor) {
        this.startForeground(NotificationUtils.NOTIFICATION_ID, NotificationUtils.generateBaseNotification(this, NotificationUtils.ChannelId.DEFAULT, this.getStatusDescriptionByStatusUI(), this.partialServiceState ? this.receiveLocationData.getData() : null));
        if (sensor) {
            this.foregroundNotificationEnabled = true;
        }
    }

    private void stopForegroundNotification_sensor() {
        this.foregroundNotificationEnabled = false;
        if (!this.partialServiceState) {
            this.stopForeground(true);
        }
    }

    private void stopForegroundNotification_partial() {
        this.partialServiceState = false;
        if (!this.foregroundNotificationEnabled) {
            this.stopForeground(true);
        }
    }

    private boolean wasConnected = false;

    private String getStatusDescriptionByStatusUI() {
        String result = StringUtil.EMPTY_STRING;
        if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_DISCONNECTED)) {
            result = getResources().getString(R.string.app_main_notification_disconnected);
        } else if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_CONNECTED)) {
            result = getResources().getString(R.string.app_main_notification_connected);
        } else if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_CONNECTING)) {
            result = getResources().getString(R.string.app_main_notification_connecting);
        } else if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_DISCONNECTING)) {
            result = getResources().getString(R.string.app_main_notification_disconnecting);
        } else if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_RECONNECT)) {
            result = getResources().getString(R.string.app_main_notification_reconnecting);
        } else {
            result = this.statusProgressUI.name();
        }

        return result + (StringUtil.isNotEmpty(this.getDeviceName()) ? " :" + this.getDeviceName() : StringUtil.EMPTY_STRING);

    }

    private String getDeviceName() {
        return BaseApplication.get(this).getName();
    }

    private void setStatusAndSendBroadcast(Intent intent, StatusPair status, boolean UI) {
        if (status.equals(StatusPair.ACTION_GATT_CONNECTED) || status.equals(StatusPair.ACTION_GATT_DISCONNECTED)) {
            this.status = status;
            this.statusProgressUI = status;
        } else if (status.equals(StatusPair.ACTION_GATT_RECONNECT)
                || status.equals(StatusPair.ACTION_GATT_CONNECTING)
                || status.equals(StatusPair.ACTION_GATT_DISCONNECTING)) {
            this.statusProgressUI = status;
        }

        if (status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
            if (this.foregroundNotificationEnabled) {
                NotificationUtils.show(this, !UI && !isOnline && !BluetoothLeService.this.wasConnected ? NotificationUtils.ChannelId.CONNECTED : NotificationUtils.ChannelId.DEFAULT, this.getStatusDescriptionByStatusUI(), this.partialServiceState ? this.receiveLocationData.getData() : null);
            }
            BluetoothLeService.this.wasConnected = true;
        } else if ((status.equals(StatusPair.ACTION_GATT_DISCONNECTED) || status.equals(StatusPair.ACTION_GATT_RECONNECT)) && !UI && BluetoothLeService.this.wasConnected) {
            this.status = StatusPair.ACTION_GATT_DISCONNECTED;
            BluetoothLeService.this.wasConnected = false;
            if (this.foregroundNotificationEnabled) {
                NotificationUtils.show(this, !isOnline ? NotificationUtils.ChannelId.DISCONNECTED : NotificationUtils.ChannelId.DEFAULT, getResources().getString(status.equals(StatusPair.ACTION_GATT_DISCONNECTED) ? R.string.app_main_notification_disconnected : R.string.app_main_notification_reconnecting) + ": " + BaseApplication.get(this).getName(), this.partialServiceState ? this.receiveLocationData.getData() : null);
            }
            try {
                BluetoothLeService.this.send(getReceiveData().getDataMessage(-1));
            } catch (Exception e) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getMessage(), false);
            }
        }

        if (this.foregroundNotificationEnabled) {
            NotificationUtils.show(this, NotificationUtils.ChannelId.DEFAULT, this.getStatusDescriptionByStatusUI(), this.partialServiceState ? this.receiveLocationData.getData() : null);
        }


        String date = android.text.format.DateFormat.format("yyyy-MM-dd hh:mm:ss", new java.util.Date()).toString();
        String message = intent.hasExtra(EXTRA_DATA) ?
                date + " -> " + (status.getAction().replace("com.altertech.scanner.le.ACTION_GATT_", "") + ", data = " + intent.getStringExtra(EXTRA_DATA))
                : date + " -> " + (status.getAction().replace("com.altertech.scanner.le.ACTION_GATT_", ""));
        Log.d(TAG, message + " from UI => " + UI);
        if (logEnabled) {
            if (!status.equals(StatusPair.ACTION_GATT_KEEP_ONLINE))
                this.log.append("\n").append(message).append(" from UI => ").append(UI);
        }

        this.sendBroadcast(intent);
    }

    private void setStatusAndSendBroadcast(StatusPair status, boolean UI) {
        this.setStatusAndSendBroadcast(new Intent(status.getAction()), status, UI);
    }

    private void setStatusAndSendBroadcast(StatusPair status, String data, boolean UI) {
        this.setStatusAndSendBroadcast(new Intent(status.getAction()).putExtra(EXTRA_DATA, data), status, UI);
    }

    public void sendUIStatus() {
        this.setStatusAndSendBroadcast(statusProgressUI, true);
    }

    private int parsePulseValue(BluetoothGattCharacteristic characteristic) {
        if ((characteristic.getProperties() & 0x01) != 0) {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
        } else {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
        }
    }

    public void send(String... message) {
        if (this.udpClientSender == null) {
            this.udpClientSender = new UDP_CLIENT_SENDER();
            this.udpClientSender.setMessage(message);
            TaskHelper.execute(this.udpClientSender);
        } else if (!this.udpClientSender.getStatus().equals(AsyncTask.Status.FINISHED)) {
            this.udpClientSender.cancel(true);
            this.udpClientSender = new UDP_CLIENT_SENDER();
            this.udpClientSender.setMessage(message);
            TaskHelper.execute(this.udpClientSender);
        } else {
            this.udpClientSender = new UDP_CLIENT_SENDER();
            this.udpClientSender.setMessage(message);
            TaskHelper.execute(this.udpClientSender);
        }
    }

    public String getLog() {
        return log.toString();
    }

    private void keep(BluetoothGattCharacteristic characteristic) {
        /*----------------send keep----------------------*/
        if (characteristic != null) {
            try {
                BluetoothLeService.this.writeCharacteristic(characteristic, DATA_HEART_RATE_KEEP_ONLINE);
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "keep", false);
            } catch (BLEServiceException e) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
            }
        }
        /*-----------------------------------------------------------------------------------*/
    }

    @SuppressLint("StaticFieldLeak")
    private class KEEP_ONLINE extends AsyncTask<Void, Void, Void> {
        BluetoothGattCharacteristic characteristic;
        boolean check = true;

        KEEP_ONLINE(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        void setCheck(boolean check) {
            this.check = check;
        }

        void setCharacteristic(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        protected Void doInBackground(Void... voids) {
            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "start", false);
            while (check) {
                BluetoothLeService.this.sleep(5000);
                if (!this.check) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "stop", false);
                } else {

                    boolean isNewDateNotAvailable = new Date().getTime() - getReceiveData().getDate().getTime() > 10000;
                    if (isNewDateNotAvailable
                            && !BluetoothLeService.this.statusProgressUI.equals(StatusPair.ACTION_GATT_RECONNECT)
                            && !BluetoothLeService.this.statusProgressUI.equals(StatusPair.ACTION_GATT_CONNECTING)
                            && !BluetoothLeService.this.statusProgressUI.equals(StatusPair.ACTION_GATT_DISCONNECTING)) {
                        BluetoothLeService.this.reconnect();
                    } else {
                        if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
                            BluetoothLeService.this.keep(characteristic);
                        } else {
                            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "step", false);
                        }
                    }
                }
            }
            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "stop", false);
            return null;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class UDP_CLIENT_SENDER extends AsyncTask<Void, Void, Void> {

        private String[] messages = new String[]{};

        public void setMessage(String... messages) {
            this.messages = messages != null ? messages : new String[]{};
        }

        @Override
        protected Void doInBackground(Void... voids) {

            String host = BaseApplication.get(BluetoothLeService.this).getServerAddress();
            if (StringUtil.isEmpty(host)) {
                return null;
            }

            DatagramSocket socket = null;
            try {
                //String message = action == 0 ? BluetoothLeService.this.receiveData.getDataMessage(1) : BluetoothLeService.this.receiveData.getDataMessage(-1);
                for (String s : messages) {
                    if (StringUtil.isNotEmpty(s)) {
                        DatagramPacket dp = new DatagramPacket(s.getBytes(), s.length(),
                                InetAddress.getByName(host),
                                BaseApplication.get(BluetoothLeService.this).getServerPort());
                        socket = new DatagramSocket();
                        socket.setBroadcast(true);
                        socket.send(dp);
                    }
                }
            } catch (Exception e) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getMessage(), false);
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
            return null;
        }
    }

    public class ReceiveData {
        Date date;
        Date dateSend;
        int data;

        ReceiveData(int data, Date dateSend) {
            this.date = new Date();
            this.data = data;
            this.dateSend = dateSend;
        }

        public Date getDate() {
            return date;
        }

        public int getData() {
            return data;
        }

        public Date getDateSend() {
            return dateSend;
        }

        void setupDateToNow() {
            this.date = new Date();
        }

        private String generateMessage(int action) {
            String prefix = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerPrefix();
            String id = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID();
            if (StringUtil.isNotEmpty(prefix)) {
                return "sensor:" + prefix + "/" + id + "/heartrate u " + action + (action == 1 ? " " + this.data : StringUtil.EMPTY_STRING);
            } else {
                return "sensor:" + id + "/heartrate u " + action + (action == 1 ? " " + this.data : StringUtil.EMPTY_STRING);
            }
        }

        public String getDataMessage(int action) throws Exception {
            String key = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerKey();
            if (StringUtil.isNotEmpty(key)) {
                return "|" + BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID() + "|" + Crypt.encode(key, this.generateMessage(action));
            } else {
                return this.generateMessage(action);
            }
        }
    }

    public class ReceiveLocationData {
        int speed;
        double lat;
        double lon;

        ReceiveLocationData(int speed, double lat, double lon) {
            this.speed = speed;
            this.lat = lat;
            this.lon = lon;
        }

        public String getTrLat() throws Exception {
            return generateMessageEncoded("lat", String.valueOf(lat));
        }

        public String getTrLon() throws Exception {
            return generateMessageEncoded("lon", String.valueOf(lon));
        }

        public String getTrSpeed() throws Exception {
            return generateMessageEncoded("speed", String.valueOf(speed));
        }

        String getData() {
            return lat != -1 && lon != -1 ? String.format("lat:%s, lon:%s, speed:%s", String.valueOf(lat), String.valueOf(lon), String.valueOf(speed)) : StringUtil.EMPTY_STRING;
        }
    }

    private class AccelerometerBuffer {

        boolean lock = false;

        List<Double> buffer_x;
        List<Double> buffer_y;
        List<Double> buffer_z;

        AccelerometerBuffer() {
            this.buffer_x = new ArrayList<Double>(1000);
            this.buffer_y = new ArrayList<Double>(1000);
            this.buffer_z = new ArrayList<Double>(1000);
        }

        public void put(int vector, double value) {
            if (this.lock) {
                return;
            }
            switch (vector) {
                case 0:
                    if (this.buffer_x.size() == 0) {
                        this.buffer_x.add(value);
                    } else {
                        this.buffer_x.add(value - this.buffer_x.get(this.buffer_x.size() - 1));
                    }
                    break;
                case 1:
                    if (this.buffer_y.size() == 0) {
                        this.buffer_y.add(value);
                    } else {
                        this.buffer_y.add(value - this.buffer_y.get(this.buffer_y.size() - 1));
                    }
                    break;
                case 2:
                    if (this.buffer_z.size() == 0) {
                        this.buffer_z.add(value);
                    } else {
                        this.buffer_z.add(value - this.buffer_z.get(this.buffer_z.size() - 1));
                    }
                    break;
            }


        }

        public String getTrMaxDelta(int vector) throws Exception {
            switch (vector) {
                case 0:
                    return generateMessageEncoded("x", String.valueOf(getMaxDelta(vector)));
                case 1:
                    return generateMessageEncoded("y", String.valueOf(getMaxDelta(vector)));
                case 2:
                    return generateMessageEncoded("z", String.valueOf(getMaxDelta(vector)));
                default:
                    return StringUtil.EMPTY_STRING;
            }
        }

        double getMaxDelta(int vector) {
            this.lock = true;
            switch (vector) {
                case 0:
                    return this.getMaxValue(this.buffer_x);
                case 1:
                    return this.getMaxValue(this.buffer_y);
                case 2:
                    return this.getMaxValue(this.buffer_z);
                default:
                    return 0;
            }
        }

        private double getMaxValue(List<Double> buffer) {
            if (buffer == null || buffer.size() == 0) {
                return 0;
            }
            double temp = 0;
            for (Double value : buffer) {
                if (value > temp) {
                    temp = value;
                }
            }
            return temp;
        }
    }

    public String generateMessageEncoded(String command, String value) throws Exception {
        String key = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerKey();
        if (StringUtil.isNotEmpty(key)) {
            return "|" + BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID() + "|" + Crypt.encode(key, this.generateMessage(command, value));
        } else {
            return this.generateMessage(command, value);
        }
    }

    private String generateMessage(String command, String value) {
        String prefix = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerPrefix();
        String id = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID();
        if (StringUtil.isNotEmpty(prefix)) {
            return "sensor:" + prefix + "/" + id + "/" + command + " u " + value;
        } else {
            return "sensor:" + id + "/" + command + " u " + value;
        }
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
}
