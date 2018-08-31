package com.altertech.scanner.core.service.enums;

import com.altertech.scanner.core.ExceptionCodes;

/**
 * Created by oshevchuk on 31.07.2018
 */
public class BLEServiceException extends Exception {

    private ExceptionCodes exceptionCodes;

    private String data;

    public BLEServiceException(ExceptionCodes exceptionCodes) {
        this.exceptionCodes = exceptionCodes;
    }

    public BLEServiceException(ExceptionCodes exceptionCodes, String data) {
        this.exceptionCodes = exceptionCodes;
        this.data = data;
    }

    public int getCode() {
        return this.exceptionCodes.getCode();
    }

    public String getDescription() {
        return this.exceptionCodes.getDescription();
    }

    public String getData() {
        return data != null ? data : "";
    }
}
