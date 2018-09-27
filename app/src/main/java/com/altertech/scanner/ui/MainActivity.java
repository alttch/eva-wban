package com.altertech.scanner.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.core.ExceptionCodes;
import com.altertech.scanner.core.device.Device;
import com.altertech.scanner.core.device.DeviceManager;
import com.altertech.scanner.core.device.DeviceManagerException;
import com.altertech.scanner.helpers.IntentHelper;
import com.altertech.scanner.helpers.ToastHelper;
import com.altertech.scanner.service.BluetoothLeService;
import com.altertech.scanner.utils.StringUtil;


public class MainActivity extends AppCompatActivity {


    /*base*/
    private BaseApplication application;
    /*service*/
    private BluetoothLeService bluetoothLeService;
    /*controls*/
    private TextView fragment_a_main_connection_block_id;
    private Button fragment_a_main_connection_block_connect_disconnect_button;
    private Button fragment_a_main_connection_block_choose_other_devices_button;
    private TextView a_main_connection_status;
    private TextView fragment_a_main_connection_block_device_name;
    private TextView fragment_a_main_connection_block_device_address;

    /*debug*/
    private TextView a_main_debug_data;

    private boolean needToConnectAfterActivityAction = true;

    private boolean isDebugEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.initializationControls();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.registerReceiver(broadcastReceiver, BluetoothLeService.StatusPair.getIntentFilter());
        this.bindService(new Intent(this, BluetoothLeService.class), serviceConnection, BIND_AUTO_CREATE);
        /*if (!this.bound) {
            //this.bindService(new Intent(this, BluetoothLeService.class), serviceConnection, BIND_AUTO_CREATE);
        } else {
            if (MainActivity.this.bluetoothLeService != null) {
                MainActivity.this.bluetoothLeService.sendUIStatus();
            }
            if (this.needToConnectAfterActivityAction && MainActivity.this.bluetoothLeService != null && MainActivity.this.bluetoothLeService.getStatusProgressUI().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTED) && StringUtil.isNotEmpty(MainActivity.this.application.getAddress())) {
                this.needToConnectAfterActivityAction = false;
                MainActivity.this.tryToConnect();
            }
        }
        if (MainActivity.this.bluetoothLeService != null) {
            MainActivity.this.bluetoothLeService.setOnline(true);
        }*/
    }

    public void onPause() {
        super.onPause();
        this.unregisterReceiver(broadcastReceiver);
        this.unbindService(serviceConnection);
        if (MainActivity.this.bluetoothLeService != null) {
            MainActivity.this.bluetoothLeService.setOnline(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DeviceManager.ACTION_REQUEST_ENABLE && resultCode == Activity.RESULT_OK) {
            this.needToConnectAfterActivityAction = true;
        }
        if (requestCode == IntentHelper.REQUEST_CODES.DEVICE_ACTIVITY.getCode() && resultCode == Activity.RESULT_OK) {
            this.application.setDevice(((Device) data.getSerializableExtra(BluetoothLeService.EXTRA_DATA)).getPair());

            this.fragment_a_main_connection_block_device_name.setText(this.application.getName());
            this.fragment_a_main_connection_block_device_address.setText(this.application.getAddress());
            this.fragment_a_main_connection_block_device_name.setVisibility(this.application.existDevice() ? View.VISIBLE : View.GONE);
            this.fragment_a_main_connection_block_device_address.setVisibility(this.application.existDevice() ? View.VISIBLE : View.GONE);

            this.needToConnectAfterActivityAction = true;
        }
        if (requestCode == IntentHelper.REQUEST_CODES.SETTINGS_ACTIVITY.getCode() && resultCode == Activity.RESULT_OK) {
            this.fragment_a_main_connection_block_id.setText(this.application.getServerID());
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == 23424 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            IntentHelper.showDeviceActivity(MainActivity.this);
        }
    }

    private void initializationControls() {
        this.application = BaseApplication.get(this);

        this.fragment_a_main_connection_block_id = findViewById(R.id.fragment_a_main_connection_block_id);
        this.fragment_a_main_connection_block_id.setText(this.application.getServerID());

        this.fragment_a_main_connection_block_connect_disconnect_button = findViewById(R.id.fragment_a_main_connection_block_connect_disconnect_button);
        this.fragment_a_main_connection_block_connect_disconnect_button.setOnClickListener(connect_disconnect_listener);
        this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(StringUtil.isNotEmpty(this.application.getAddress()));

        this.fragment_a_main_connection_block_choose_other_devices_button = findViewById(R.id.fragment_a_main_connection_block_choose_other_devices_button);
        this.fragment_a_main_connection_block_choose_other_devices_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int permissionCheckACCESS_FINE_LOCATION = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
                if (permissionCheckACCESS_FINE_LOCATION != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 23424);
                } else {
                    IntentHelper.showDeviceActivity(MainActivity.this);
                }
            }
        });

        this.a_main_connection_status = findViewById(R.id.a_main_connection_status);

        this.fragment_a_main_connection_block_device_name = findViewById(R.id.fragment_a_main_connection_block_device_name);
        this.fragment_a_main_connection_block_device_name.setVisibility(this.application.existDevice() ? View.VISIBLE : View.GONE);
        this.fragment_a_main_connection_block_device_name.setText(this.application.getName());

        this.fragment_a_main_connection_block_device_address = findViewById(R.id.fragment_a_main_connection_block_device_address);
        this.fragment_a_main_connection_block_device_address.setVisibility(this.application.existDevice() ? View.VISIBLE : View.GONE);
        this.fragment_a_main_connection_block_device_address.setText(this.application.getAddress());

        findViewById(R.id.title_bar_controls_settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IntentHelper.showSettingsActivity(MainActivity.this, false);
            }
        });

        this.a_main_debug_data = findViewById(R.id.a_main_debug_data);
        this.a_main_debug_data.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.a_main_debug_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.isDebugEnabled = !MainActivity.this.isDebugEnabled;
                MainActivity.this.a_main_debug_data.setVisibility(MainActivity.this.isDebugEnabled ? View.VISIBLE : View.GONE);
                MainActivity.this.bluetoothLeService.setLogEnabled(MainActivity.this.isDebugEnabled);
                if (MainActivity.this.isDebugEnabled) {
                    MainActivity.this.a_main_debug_data.setText(MainActivity.this.bluetoothLeService.getLog());
                }

                // NotificationUtils.show(MainActivity.this, NotificationUtils.ChannelId.DISCONNECTED, NotificationUtils.ChannelId.DISCONNECTED.getName());

            }
        });

        findViewById(R.id.fragment_a_main_quit_block_quit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.bluetoothLeService.disconnect();
                MainActivity.this.finish();
            }
        });
    }

    private View.OnClickListener connect_disconnect_listener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (MainActivity.this.bluetoothLeService.getStatusProgressUI().equals(BluetoothLeService.StatusPair.ACTION_GATT_CONNECTED)) {
                MainActivity.this.bluetoothLeService.disconnect();
            } else {
                MainActivity.this.tryToConnect();
            }
        }
    };

    private void tryToConnect() {
        try {
            MainActivity.this.bluetoothLeService.check();
            MainActivity.this.bluetoothLeService.connect(MainActivity.this.application.getAddress(), BluetoothLeService.StatusPair.ACTION_GATT_CONNECTING);
        } catch (DeviceManagerException e) {
            if (e.getCode() == ExceptionCodes.BLUETOOTH_TO_ENABLE.getCode()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), DeviceManager.ACTION_REQUEST_ENABLE);
            } else {
                ToastHelper.toast(MainActivity.this, e.getDescription());
            }
        }
    }

    private void updateDeviceStateColorAndStatusText(boolean connected) {

        MainActivity.this.a_main_connection_status.setText(connected ? R.string.app_connection_status_active : R.string.app_connection_status_enactive);

        MainActivity.this.fragment_a_main_connection_block_device_name.setTextColor(ContextCompat.getColor(MainActivity.this, connected ? R.color.app_connection_connected : R.color.app_connection_disconnected));
        MainActivity.this.fragment_a_main_connection_block_device_address.setTextColor(ContextCompat.getColor(MainActivity.this, connected ? R.color.app_connection_connected : R.color.app_connection_disconnected));
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_CONNECTING.getAction())) {
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_connecting);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(false);
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.GONE);

                MainActivity.this.updateDeviceStateColorAndStatusText(false);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_CONNECTED.getAction())) {
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_disconnect);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(true);
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.GONE);

                MainActivity.this.updateDeviceStateColorAndStatusText(true);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTING.getAction())) {
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_disconnecting);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(false);
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.GONE);

                MainActivity.this.updateDeviceStateColorAndStatusText(false);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTED.getAction())) {
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_connect);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(StringUtil.isNotEmpty(MainActivity.this.application.getAddress()));
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.VISIBLE);

                MainActivity.this.updateDeviceStateColorAndStatusText(false);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCOVERING.getAction())) {

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCOVERED.getAction())) {

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_RECONNECT.getAction())) {

                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_reconnect);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(false);
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.GONE);

                MainActivity.this.updateDeviceStateColorAndStatusText(false);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_UNKNOWN.getAction())) {
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setText(R.string.app_connect);
                MainActivity.this.fragment_a_main_connection_block_connect_disconnect_button.setEnabled(true);
                MainActivity.this.fragment_a_main_connection_block_choose_other_devices_button.setVisibility(View.VISIBLE);

                MainActivity.this.updateDeviceStateColorAndStatusText(false);

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DATA_AVAILABLE.getAction())) {
                MainActivity.this.updateDeviceStateColorAndStatusText(true);
            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_ERROR.getAction())) {
                //MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_connection_disconnected));
            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_KEEP_ONLINE.getAction())) {

            } /*else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE.getAction())) {
                MainActivity.this.updateDeviceStateColorAndStatusText(false);
            }*/ else {

            }

            if (isDebugEnabled) {
                MainActivity.this.a_main_debug_data.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.a_main_debug_data.setText(MainActivity.this.bluetoothLeService.getLog());
                    }
                });
            }
        }
    };


    private boolean bound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            MainActivity.this.bound = true;
            MainActivity.this.bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            MainActivity.this.bluetoothLeService.setOnline(true);
            MainActivity.this.bluetoothLeService.sendUIStatus();
            if (MainActivity.this.needToConnectAfterActivityAction && MainActivity.this.bluetoothLeService.getStatusProgressUI().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTED) && StringUtil.isNotEmpty(MainActivity.this.application.getAddress())) {
                MainActivity.this.needToConnectAfterActivityAction = false;
                MainActivity.this.tryToConnect();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            MainActivity.this.bound = false;
        }
    };

}
