package com.altertech.scanner.core.service.enums;

import java.util.UUID;

/**
 * Created by oshevchuk on 31.07.2018
 */
public enum CharacteristicInstruction {

    CHARACTERISTIC_HEART_RATE_MEASUREMENT(UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"), "Heart Rate measurement"),
    CHARACTERISTIC_HEART_RATE_DATA_WRITE(UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb"), "Heart Rate data write"),
    CHARACTERISTIC_HEART_RATE_CONFIG(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), "Heart Rate config"),

    CHARACTERISTIC_AUTH(UUID.fromString("00000009-0000-3512-2118-0009af100700"), "Auth"),
    CHARACTERISTIC_AUTH_DESCRIPTOR(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), "Auth descriptor");

    UUID uuid;
    String name;

    CharacteristicInstruction(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }
}
