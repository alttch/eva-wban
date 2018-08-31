package com.altertech.scanner.core.service.enums;

import java.util.UUID;

/**
 * Created by oshevchuk on 31.07.2018
 */
public enum ServiceInstruction {

    SERVICE_HEART_RATE(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"), "Heart Rate Service"),
    SERVICE_AUTH(UUID.fromString("0000FEE1-0000-1000-8000-00805f9b34fb"), "Auth Service");

    UUID uuid;
    String name;

    ServiceInstruction(UUID uuid, String name) {
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
