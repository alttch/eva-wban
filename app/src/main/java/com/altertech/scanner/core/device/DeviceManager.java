package com.altertech.scanner.core.device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;

import com.altertech.scanner.core.ExceptionCodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by oshevchuk on 26.07.2018
 */
public class DeviceManager {

    /*const*/
    public static final int ACTION_REQUEST_ENABLE = 1436;
    public static final int SCANNING_TTL =10000;

    private Context context;
    private DeviceManagerCallBack deviceManagerCallBack;

    /*fields*/
    private BluetoothAdapter bluetoothAdapter;
    private boolean scanning = false;

    private HashSet<Device> devices = new HashSet<>();
    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            devices.add(new Device(device.getName(), device.getAddress()));
        }
    };

    public DeviceManager(Context context, DeviceManagerCallBack deviceManagerCallBack) {
        this.context = context;
        this.deviceManagerCallBack = deviceManagerCallBack;

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            this.bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }


    public void scan() throws DeviceManagerException {
        this.check();
        if (!scanning) {
            this.scanning = true;
            this.deviceManagerCallBack.startScan();
            this.devices.clear();
            this.bluetoothAdapter.startLeScan(scanCallback);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    DeviceManager.this.scanning = false;
                    DeviceManager.this.deviceManagerCallBack.stopScan();
                    DeviceManager.this.bluetoothAdapter.stopLeScan(scanCallback);
                    DeviceManager.this.deviceManagerCallBack.found(new ArrayList<>(devices));
                }
            }, SCANNING_TTL);

        } else {
            this.deviceManagerCallBack.scanning();
        }
    }

    private void check() throws DeviceManagerException {
        if (this.bluetoothAdapter == null) {
            throw new DeviceManagerException(ExceptionCodes.BLUETOOTH_NOT_SUPPORTED);
        } else {
            if (!this.bluetoothAdapter.isEnabled()) {
                throw new DeviceManagerException(ExceptionCodes.BLUETOOTH_TO_ENABLE);
            }
        }
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return this.bluetoothAdapter;
    }

    public interface DeviceManagerCallBack {
        void startScan();

        void found(List<Device> device);

        void scanning();

        void stopScan();
    }

}
