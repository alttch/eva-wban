package com.altertech.scanner.utils;

/**
 * Created by oshevchuk on 28.08.2018
 */

import android.annotation.SuppressLint;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AES256Cipher {

    public static String encrypt(String key, String text)
            throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException {

        @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes("UTF-8"), "AES"));
        return Base64.encodeToString(cipher.doFinal(text.getBytes("UTF-8")), Base64.DEFAULT);
    }
}