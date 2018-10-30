package com.altertech.scanner.core;

/**
 * Created by oshevchuk on 26.07.2018
 */
public enum ExceptionCodes {

    BLUETOOTH_TO_ENABLE(1, "BLUETOOTH_TO_ENABLE"),
    BLUETOOTH_NOT_SUPPORTED(2, "BLUETOOTH_NOT_SUPPORTED"),
    BLUETOOTH_DEVICE_NOT_FOUND(3, "BLUETOOTH_DEVICE_NOT_FOUND"),

    GATT_SERVICE_NOT_FOUND(4, "GATT_SERVICE_NOT_FOUND"),
    GATT_CHARACTERISTIC_NOT_FOUND(5, "GATT_CHARACTERISTIC_NOT_FOUND"),
    GATT_BAD_ACTION(6, "GATT_BAD_ACTION"),

    LOCATION_SERVICE_NOT_SUPPORTED(7, "LOCATION_SERVICE_NOT_SUPPORTED"),
    LOCATION_SERVICE_TO_ENABLE_P_GPS(8, "LOCATION_SERVICE_TO_ENABLE_P_GPS"),

    SENSOR_NOT_SUPPORTED(9, "SENSOR_NOT_SUPPORTED"),
    SENSOR_ACCELEROMETER_NOT_SUPPORTED(10, "SENSOR_ACCELEROMETER_NOT_SUPPORTED");

    int code;
    String description;

    ExceptionCodes(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
