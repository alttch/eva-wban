package com.altertech.scanner.service;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
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
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.core.ExceptionCodes;
import com.altertech.scanner.core.device.Device;
import com.altertech.scanner.core.device.DeviceManagerException;
import com.altertech.scanner.core.service.enums.BLEServiceException;
import com.altertech.scanner.core.service.enums.CharacteristicInstruction;
import com.altertech.scanner.core.service.enums.ServiceInstruction;
import com.altertech.scanner.helpers.TaskHelper;
import com.altertech.scanner.utils.AES256Cipher;
import com.altertech.scanner.utils.NotificationUtils;
import com.altertech.scanner.utils.StringUtil;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class BluetoothLeService extends Service {

    public final static boolean logEnabled = true;
    public final static String EXTRA_DATA = "com.altertech.scanner.le.EXTRA_DATA";
    public final static int DEFAULT_NOTIFICATION_ID = 1;

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
        ACTION_GATT_NEW_DATA_NOT_AVAILABLE(1008, "com.altertech.scanner.le.ACTION_GATT_NEW_DATA_NOT_AVAILABLE");

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

    /*data*/
    private ReceiveData receiveData = new ReceiveData(0, new Date());
    private Date systemDateTimeOfLastSuccessKeepOnline = new Date();
    private StatusPair status = StatusPair.ACTION_GATT_DISCONNECTED;
    private StatusPair statusProgress = StatusPair.ACTION_GATT_DISCONNECTED;
    private StatusPair statusProgressUI = StatusPair.ACTION_GATT_DISCONNECTED;

    private boolean isOnline = false;

    private List<String> log = new LinkedList<>();

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BaseApplication.get(BluetoothLeService.this).setDevice(new Device(gatt.getDevice().getName(), gatt.getDevice().getAddress()).getPair());
                BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline = new Date();
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_CONNECTED, false);
                BluetoothLeService.this.gatt.discoverServices();
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DISCOVERING, false);
            } else {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.getById(newState), false);
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
                        BluetoothLeService.this.send();
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
                    BluetoothLeService.this.writeCharacteristic(characteristic, concat(new byte[]{1, 8}, DATA_AUTH_KEY));
                } else if (Arrays.equals(characteristic.getValue(), new byte[]{16, 1, 1})) {
                    BluetoothLeService.this.writeCharacteristic(characteristic, new byte[]{2, 8});
                } else if (characteristic.getValue().length == 19 && Arrays.equals(Arrays.copyOfRange(characteristic.getValue(), 0, 3), new byte[]{16, 2, 1})) {
                    byte[] b16 = Arrays.copyOfRange(characteristic.getValue(), characteristic.getValue().length - 16, characteristic.getValue().length);
                    BluetoothLeService.this.writeCharacteristic(characteristic, getEncryptKey(new byte[]{3, 8}, b16));
                } else if (Arrays.equals(characteristic.getValue(), new byte[]{16, 3, 1})) {
                    try {
                        BluetoothGattCharacteristic hr_characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_HEART_RATE, CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT);
                        BluetoothLeService.this.setCharacteristicNotification(hr_characteristic, true);
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
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
                    BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline = new Date();
                } else if (characteristic.getValue() == DATA_HEART_RATE_READ_PULSE) {
                    BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline = new Date();
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_RECEIVING, false);
                }
            } else if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid())) {

            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onCharacteristicWrite(), BluetoothGatt.STATUS = " + status, false);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.getCharacteristic().getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_MEASUREMENT.getUuid())) {
                try {
                    BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_HEART_RATE, CharacteristicInstruction.CHARACTERISTIC_HEART_RATE_DATA_WRITE);
                    BluetoothLeService.this.writeCharacteristic(characteristic, DATA_HEART_RATE_READ_PULSE);
                    BluetoothLeService.this.keepOnlineStartIfNeed(characteristic);
                } catch (BLEServiceException e) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                }
            } else if (status == BluetoothGatt.GATT_SUCCESS && descriptor.getCharacteristic().getUuid().equals(CharacteristicInstruction.CHARACTERISTIC_AUTH.getUuid())) {
                BluetoothLeService.this.writeCharacteristic(descriptor.getCharacteristic(), new byte[]{2, 8});
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                if (status == 3) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "trying to authenticate", false);
                    try {
                        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(gatt, ServiceInstruction.SERVICE_AUTH, CharacteristicInstruction.CHARACTERISTIC_AUTH);
                        BluetoothLeService.this.setCharacteristicNotification(characteristic, true);
                    } catch (BLEServiceException e) {
                        BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, e.getDescription() + "( data -> " + e.getData() + ")", false);
                    }
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "onDescriptorWrite(), BluetoothGatt.STATUS = " + status, false);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        NotificationUtils.getNotificationManager(this, 2);
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
        this.stopForegroundNotification();
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
        this.systemDateTimeOfLastSuccessKeepOnline = new Date();
        if (this.gatt != null) {
            this.gatt.close();
        }
        this.gatt = bluetoothAdapter.getRemoteDevice(address).connectGatt(this, false, bluetoothGattCallback);
    }

    public void disconnect() {
        if (this.gatt != null) {
            this.gatt.disconnect();
            this.gatt.close();
        }
        this.keepOnlineStop();
        this.stopForegroundNotification();
        this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_DISCONNECTED, false);
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

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        characteristic.setValue(data);
        boolean result = this.gatt.writeCharacteristic(characteristic);
        if (!result) {
            this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_ERROR, "write characteristic = " + StringUtil.arrayAsString(data), false);
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
        if (this.keepOnline != null && keepOnline.getStatus().equals(AsyncTask.Status.RUNNING) && this.keepOnline.check) {
            this.keepOnline.setCheck(false);
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

    public StatusPair getStatusProgress() {
        return statusProgress;
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

    private void startForegroundNotification() {
        this.startForeground(DEFAULT_NOTIFICATION_ID, NotificationUtils.generateNotification(this, getStatusProgressUI().name()));
        this.foregroundNotificationEnabled = true;
    }

    private void stopForegroundNotification() {
        this.stopForeground(true);
        this.foregroundNotificationEnabled = false;
    }

    private void setStatusAndSendBroadcast(Intent intent, StatusPair status, boolean UI) {
        if (status.equals(StatusPair.ACTION_GATT_CONNECTED) || status.equals(StatusPair.ACTION_GATT_DISCONNECTED)) {
            if (status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
                this.startForegroundNotification();
            }
            this.status = status;
            this.statusProgressUI = status;
        } else if (status.equals(StatusPair.ACTION_GATT_RECONNECT)
                || status.equals(StatusPair.ACTION_GATT_CONNECTING)
                || status.equals(StatusPair.ACTION_GATT_DISCONNECTING)) {
            this.statusProgress = status;
            this.statusProgressUI = status;
        }

        this.sendBroadcast(intent);

        if (this.foregroundNotificationEnabled) {
            if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_DISCONNECTED) || this.statusProgressUI.equals(StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE)) {
                NotificationManager notificationManager = NotificationUtils.getNotificationManager(this, !UI && !isOnline() ? 4 : 2);
                if (notificationManager != null) {
                    if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_DISCONNECTED)) {
                        notificationManager.notify(DEFAULT_NOTIFICATION_ID, NotificationUtils.generateNotification(this, getResources().getString(R.string.app_main_notification_disconnected) + " " + BaseApplication.get(this).getName()));
                    } else if (this.statusProgressUI.equals(StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE)) {
                        notificationManager.notify(DEFAULT_NOTIFICATION_ID, NotificationUtils.generateNotification(this, getResources().getString(R.string.app_main_notification_receive_error) + " " + BaseApplication.get(this).getName()));
                    }
                }
            } else {
                NotificationManager notificationManager = NotificationUtils.getNotificationManager(this, 2);
                if (notificationManager != null) {
                    notificationManager.notify(DEFAULT_NOTIFICATION_ID, NotificationUtils.generateNotification(this, this.statusProgressUI.name()));
                }
            }
        }


        /*if (!UI && !isOnline()) {
            if (status.equals(StatusPair.ACTION_GATT_DISCONNECTED)) {
                NotificationUtils.showNotification(this, getResources().getString(R.string.app_main_notification_disconnected) + " " + BaseApplication.get(this).getName());
            } else if (status.equals(StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE)) {
                NotificationUtils.showNotification(this, getResources().getString(R.string.app_main_notification_receive_error) + " " + BaseApplication.get(this).getName());
            } else if (status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
                NotificationUtils.cancel(this);
            }
        } */

        String date = android.text.format.DateFormat.format("yyyy-MM-dd hh:mm:ss", new java.util.Date()).toString();

        String message = intent.hasExtra(EXTRA_DATA) ?
                date + " -> " + (status.getAction().replace("com.altertech.scanner.le.ACTION_GATT_", "") + ", data = " + intent.getStringExtra(EXTRA_DATA))
                : date + " -> " + (status.getAction().replace("com.altertech.scanner.le.ACTION_GATT_", ""));


        Log.d(TAG, message + " from UI => " + UI);

        if (logEnabled) {
            this.log.add(message + " from UI => " + UI);
        }

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

    public void send() {
        if (this.udpClientSender == null) {
            this.udpClientSender = new UDP_CLIENT_SENDER();
            TaskHelper.execute(this.udpClientSender);
        } else if (!this.udpClientSender.getStatus().equals(AsyncTask.Status.FINISHED)) {
            this.udpClientSender.cancel(true);
            this.udpClientSender = new UDP_CLIENT_SENDER();
            TaskHelper.execute(this.udpClientSender);
        } else {
            this.udpClientSender = new UDP_CLIENT_SENDER();
            TaskHelper.execute(this.udpClientSender);
        }
    }

    public List<String> getLog() {
        return log;
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
                sleep(10000);
                boolean isNewDateNotAvailable = new Date().getTime() - getReceiveData().getDate().getTime() > 10000;
                if (isNewDateNotAvailable && BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE, false);
                }
                if (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED)) {
                    BluetoothLeService.this.writeCharacteristic(characteristic, DATA_HEART_RATE_KEEP_ONLINE);
                }
                long sub = new Date().getTime() - BluetoothLeService.this.systemDateTimeOfLastSuccessKeepOnline.getTime();
                if (sub >= 60000 || (BluetoothLeService.this.status.equals(StatusPair.ACTION_GATT_CONNECTED) && isNewDateNotAvailable)) {
                    BluetoothLeService.this.connect(BaseApplication.get(BluetoothLeService.this).getAddress(), StatusPair.ACTION_GATT_RECONNECT);
                } else {
                    BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "step", false);
                }

            }
            BluetoothLeService.this.setStatusAndSendBroadcast(StatusPair.ACTION_GATT_KEEP_ONLINE, "stop", false);
            return null;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class UDP_CLIENT_SENDER extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            DatagramSocket socket = null;
            try {
                String message = BluetoothLeService.this.receiveData.getDataMessage();
                DatagramPacket dp = new DatagramPacket(message.getBytes(), message.length(),
                        InetAddress.getByName(BaseApplication.get(BluetoothLeService.this).getServerAddress()),
                        BaseApplication.get(BluetoothLeService.this).getServerPort());
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.send(dp);
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

        private String generateMessage() {
            String prefix = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerPrefix();
            String id = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID();
            if (StringUtil.isNotEmpty(prefix)) {
                return "sensor:" + prefix + "/" + id + "/heartrate u 1 " + data;
            } else {
                return "sensor:" + id + "/heartrate u 1 " + data;
            }
        }

        public String getDataMessage() throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
            String key = BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerKey();
            if (StringUtil.isNotEmpty(key)) {
                return "|" + BaseApplication.get(BluetoothLeService.this.getBaseContext()).getServerID() + "|" + new String(AES256Cipher.encrypt(key.getBytes(), this.generateMessage().getBytes()));
            } else {
                return this.generateMessage();
            }
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
