package com.altertech.scanner.core.device;

import com.altertech.scanner.utils.StringUtil;

import java.io.Serializable;
import java.util.Objects;

/**
 * Created by oshevchuk on 26.07.2018
 */
public class Device implements Serializable {
    private String name;
    private String address;

    public Device(String name, String address) {
        this.name = name != null ? name : StringUtil.EMPTY_STRING;
        this.address = address != null ? address : StringUtil.EMPTY_STRING;
    }

    public String getName() {
        return StringUtil.isNotEmpty(name) ? name : "unknown";
    }

    public String getAddress() {
        return StringUtil.isNotEmpty(address) ? address : StringUtil.EMPTY_STRING;
    }

    public String getPair() {
        return getName() + "___" + address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Objects.equals(address, device.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "Device{" +
                "name='" + name + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}