package com.altertech.scanner;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.altertech.scanner.service.BluetoothLeService;
import com.altertech.scanner.utils.StringUtil;

/**
 * Created by oshevchuk on 31.07.2018
 */
public class BaseApplication extends Application implements AppConstants {

    private SharedPreferences preferences;

    public static final int DEFAULT_INTERVAL = 2;

    public static BaseApplication get(Context context) {
        return (BaseApplication) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        this.preferences = PreferenceManager.getDefaultSharedPreferences(this);

        startService(new Intent(this, BluetoothLeService.class));

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, BluetoothLeService.class));
        } else {
            startService(new Intent(this, BluetoothLeService.class));
        }*/
    }


    public boolean isFirstStart() {
        return this.preferences.getBoolean(APP_FIRST_START, true);
    }

    public void setFirstStart(boolean b) {
        this.preferences.edit().putBoolean(APP_FIRST_START, b).apply();
    }

    public String getAddress() {
        return preferences.getString(BL_ADDRESS, StringUtil.EMPTY_STRING);
    }

    public String getName() {
        return preferences.getString(BL_NAME, StringUtil.EMPTY_STRING);
    }

    public void setDevice(String devicePair) {
        String[] pair = devicePair.split("___");
        this.preferences.edit().putString(BL_NAME, pair[0]).apply();
        this.preferences.edit().putString(BL_ADDRESS, pair[1]).apply();
    }

    public void setServerAddress(String address) {
        this.preferences.edit().putString(SERVER_ADDRESS, address).apply();
    }

    public String getServerAddress() {
        return preferences.getString(SERVER_ADDRESS, "192.168.0.1");
    }

    public void setServerPort(int port) {
        this.preferences.edit().putInt(SERVER_PORT, port).apply();
    }

    public int getServerPort() {
        return preferences.getInt(SERVER_PORT, 8881);
    }

    public void setServerTTS(int tts) {
        this.preferences.edit().putInt(SERVER_TTS, tts).apply();
    }

    public int getServerTTS() {
        return preferences.getInt(SERVER_TTS, DEFAULT_INTERVAL);
    }

    public void setServerID(String id) {
        this.preferences.edit().putString(SERVER_ID, id).apply();
    }

    public String getServerID() {
        return preferences.getString(SERVER_ID, "USERNAME");
    }

    public void setServerPrefix(String prefix) {
        this.preferences.edit().putString(SERVER_PREFIX, prefix).apply();
    }

    public String getServerPrefix() {
        return preferences.getString(SERVER_PREFIX, "USERNAME");
    }

    public void setServerKey(String key) {
        this.preferences.edit().putString(SERVER_KEY, key).apply();
    }

    public String getServerKey() {
        return preferences.getString(SERVER_KEY, StringUtil.EMPTY_STRING);
    }
}
