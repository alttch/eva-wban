package com.altertech.scanner.ui;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.altertech.scanner.R;
import com.altertech.scanner.core.ExceptionCodes;
import com.altertech.scanner.core.device.Device;
import com.altertech.scanner.core.device.DeviceManager;
import com.altertech.scanner.core.device.DeviceManagerException;
import com.altertech.scanner.helpers.ToastHelper;
import com.altertech.scanner.ui.devices.DevicesAdapter;

import java.util.ArrayList;
import java.util.List;

public class DeviceActivity extends AppCompatActivity {

    private DeviceManager deviceManager;
    private DevicesAdapter devicesAdapter;
    private List<Device> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        this.initialization();
        this.initializationDevicesListView();

        this.changeControlsState(false);

        findViewById(R.id.title_bar_controls_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DeviceActivity.this.onBackPressed();
            }
        });

        this.scan();

    }

    private void initialization() {

        this.deviceManager = new DeviceManager(this, new DeviceManager.DeviceManagerCallBack() {
            @Override
            public void startScan() {
                DeviceActivity.this.changeControlsState(true);
                DeviceActivity.this.updateDevicesListView(true);
            }

            @Override
            public void found(List<Device> devices) {
                DeviceActivity.this.devices.addAll(devices);
            }

            @Override
            public void scanning() {
            }

            @Override
            public void stopScan() {
                DeviceActivity.this.changeControlsState(false);
                DeviceActivity.this.updateDevicesListView(false);

            }
        });


        findViewById(R.id.a_device_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scan();
            }
        });
    }

    private void scan() {
        try {
            DeviceActivity.this.deviceManager.scan();
        } catch (DeviceManagerException e) {
            if (e.getCode() == ExceptionCodes.BLUETOOTH_TO_ENABLE.getCode()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), DeviceManager.ACTION_REQUEST_ENABLE);
            } else {
                ToastHelper.toast(DeviceActivity.this, e.getDescription());
            }
        }
    }

    private void initializationDevicesListView() {
        this.devicesAdapter = new DevicesAdapter(this, devices);
        RecyclerView recyclerView = findViewById(R.id.devices_list_view);
        recyclerView.setAdapter(this.devicesAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void updateDevicesListView(boolean clear) {
        if (clear) {
            this.devices.clear();
        }
        this.devicesAdapter.notifyDataSetChanged();
    }

    private void changeControlsState(boolean scanning) {
        findViewById(R.id.a_device_scan).setEnabled(!scanning);
        findViewById(R.id.a_device_progress).setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DeviceManager.ACTION_REQUEST_ENABLE == requestCode && resultCode == RESULT_OK) {
            scan();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}
