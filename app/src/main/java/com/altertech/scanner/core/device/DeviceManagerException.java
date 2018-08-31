package com.altertech.scanner.core.device;

import com.altertech.scanner.core.ExceptionCodes;

/**
 * Created by oshevchuk on 26.07.2018
 */
public class DeviceManagerException extends Exception {

    private ExceptionCodes exceptionCodes;

    public DeviceManagerException(ExceptionCodes exceptionCodes) {
        this.exceptionCodes = exceptionCodes;
    }

    public int getCode() {
        return this.exceptionCodes.getCode();
    }

    public String getDescription() {
        return this.exceptionCodes.getDescription();
    }
}
