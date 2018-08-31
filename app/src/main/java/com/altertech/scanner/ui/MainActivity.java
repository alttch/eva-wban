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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import com.altertech.scanner.ui.log.LogAdapter;
import com.altertech.scanner.utils.StringUtil;

import java.util.LinkedList;


public class MainActivity extends AppCompatActivity {


    /*base*/
    private BaseApplication application;
    /*service*/
    private BluetoothLeService bluetoothLeService;
    /*controls*/
    private Button a_main_connect_disconnect_button;
    private Button a_main_choose_device;
    private TextView a_main_last_device;

    /*adapter*/
    private LogAdapter logAdapter;
    private RecyclerView recyclerView;

    private boolean needToConnectAfterActivityAction = true;

    private boolean isDebugEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.initializationControls();
        this.initializationListLog();
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
            this.a_main_last_device.setText(StringUtil.isNotEmpty(this.application.getAddress()) ? this.application.getName() + "\n" + this.application.getAddress() : StringUtil.EMPTY_STRING);
            this.a_main_last_device.setVisibility(StringUtil.isNotEmpty(this.application.getAddress()) ? View.VISIBLE : View.GONE);

            this.needToConnectAfterActivityAction = true;
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

        this.a_main_connect_disconnect_button = findViewById(R.id.a_main_connect_disconnect_button);
        this.a_main_connect_disconnect_button.setOnClickListener(connect_disconnect_listener);
        this.a_main_connect_disconnect_button.setEnabled(StringUtil.isNotEmpty(this.application.getAddress()));

        this.a_main_choose_device = findViewById(R.id.a_main_choose_device);
        this.a_main_choose_device.setOnClickListener(new View.OnClickListener() {
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

        this.a_main_last_device = findViewById(R.id.a_main_last_device);
        this.a_main_last_device.setVisibility(StringUtil.isNotEmpty(this.application.getAddress()) ? View.VISIBLE : View.GONE);
        this.a_main_last_device.setText(StringUtil.isNotEmpty(this.application.getAddress()) ? this.application.getName() + "\n" + this.application.getAddress() : StringUtil.EMPTY_STRING);

        findViewById(R.id.title_bar_controls_settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IntentHelper.showSettingsActivity(MainActivity.this, false);
            }
        });

        ((TextView) findViewById(R.id.a_main_id)).setText(this.application.getServerID());

        findViewById(R.id.a_main_debug_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.isDebugEnabled = !MainActivity.this.isDebugEnabled;
                MainActivity.this.recyclerView.setVisibility(MainActivity.this.isDebugEnabled ? View.VISIBLE : View.GONE);
                if (MainActivity.this.isDebugEnabled) {
                    MainActivity.this.logAdapter.setDataSource(MainActivity.this.bluetoothLeService.getLog());
                    MainActivity.this.recyclerView.smoothScrollToPosition(MainActivity.this.bluetoothLeService.getLog().size() - 1);
                }
            }
        });
    }

    private void initializationListLog() {
        this.logAdapter = new LogAdapter(this, new LinkedList<String>());
        recyclerView = findViewById(R.id.a_main_log);
        recyclerView.setAdapter(this.logAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
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

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_CONNECTING.getAction())) {
                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_connecting_to);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(false);
                MainActivity.this.a_main_choose_device.setVisibility(View.GONE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_CONNECTED.getAction())) {
                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_disconnect_from);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(true);
                MainActivity.this.a_main_choose_device.setVisibility(View.GONE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_green));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTING.getAction())) {
                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_disconnecting_from);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(false);
                MainActivity.this.a_main_choose_device.setVisibility(View.GONE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCONNECTED.getAction())) {
                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_connect_to);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(StringUtil.isNotEmpty(MainActivity.this.application.getAddress()));
                MainActivity.this.a_main_choose_device.setVisibility(View.VISIBLE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCOVERING.getAction())) {

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DISCOVERED.getAction())) {

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_RECONNECT.getAction())) {

                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_reconnect_to);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(false);
                MainActivity.this.a_main_choose_device.setVisibility(View.GONE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_UNKNOWN.getAction())) {
                MainActivity.this.a_main_connect_disconnect_button.setText(R.string.app_connect_to);
                MainActivity.this.a_main_connect_disconnect_button.setEnabled(true);
                MainActivity.this.a_main_choose_device.setVisibility(View.VISIBLE);
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_DATA_AVAILABLE.getAction())) {
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_green));
            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_ERROR.getAction())) {
                //MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));
            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_KEEP_ONLINE.getAction())) {

            } else if (intent.getAction().equals(BluetoothLeService.StatusPair.ACTION_GATT_NEW_DATA_NOT_AVAILABLE.getAction())) {
                MainActivity.this.a_main_last_device.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.app_red));
            } else {

            }

            if (isDebugEnabled) {
                MainActivity.this.logAdapter.setDataSource(MainActivity.this.bluetoothLeService.getLog());
                MainActivity.this.recyclerView.smoothScrollToPosition(MainActivity.this.bluetoothLeService.getLog().size() - 1);
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
