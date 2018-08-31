package com.altertech.scanner.core;

/**
 * Created by oshevchuk on 26.07.2018
 */
public enum ExceptionCodes {

    BLUETOOTH_TO_ENABLE(1, "BLUETOOTH_TO_ENABLE"),
    BLUETOOTH_NOT_SUPPORTED(2, "BLUETOOTH_NOT_SUPPORTED"),
    BLUETOOTH_DEVICE_NOT_FOUND(3, "BLUETOOTH_DEVICE_NOT_FOUND"),

    GATT_SERVICE_NOT_FOUND(4, "GATT_SERVICE_NOT_FOUND"),
    GATT_CHARACTERISTIC_NOT_FOUND(5, "GATT_CHARACTERISTIC_NOT_FOUND");

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
