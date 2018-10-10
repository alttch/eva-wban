package com.altertech.scanner.cryptography.fernet;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Created by oshevchuk on 10.10.2018
 */
public class Crypt {

    public static String encode(String key, String message) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] sha256_key = generateSHA256WithKey(key);
        Key fernet_key = Key.generateKey(getDiapason(sha256_key, 0, 16), getDiapason(sha256_key, 16, 32));
        return Token.generate(new Random(), fernet_key, message).serialise();
    }

    private static byte[] generateSHA256WithKey(String key) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(key.getBytes("UTF-8"));
        return digest.digest();
    }

    private static byte[] getDiapason(byte[] input, int s_index, int e_index) {
        byte[] part = new byte[e_index - s_index];
        for (int i = s_index; i < e_index; i++) {
            part[i - s_index] = input[i];
        }
        return part;
    }


}
