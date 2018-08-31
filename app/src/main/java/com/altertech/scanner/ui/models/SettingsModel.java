package com.altertech.scanner.ui.models;

import android.content.Context;
import android.support.annotation.StringRes;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.utils.StringUtil;

/**
 * Created by oshevchuk on 28.08.2018
 */
public class SettingsModel {

    public class SettingsException extends Exception {
        private @StringRes
        int message;

        public SettingsException(int message) {
            this.message = message;
        }

        public @StringRes
        int getCustomMessage() {
            return message;
        }
    }

    private String host;

    private String port;

    private String interval;

    private String prefix;

    private String id;

    private String key;

    public SettingsModel() {
    }

    public SettingsModel(String host, String port, String interval, String prefix, String id, String key) {
        this.host = host;
        this.port = port;
        this.interval = interval;
        this.prefix = prefix;
        this.id = id;
        this.key = key;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public void parse(String data) throws SettingsException {
        if (StringUtil.isNotEmpty(data)) {
            String[] parts = data.split("\\|");
            for (String part : parts) {
                if (StringUtil.isNotEmpty(part)) {
                    String[] pair = part.split(":");
                    if (pair.length == 2) {
                        switch (pair[0]) {
                            case "udp_api_host":
                                this.host = pair[1];
                                break;
                            case "udp_api_port":
                                this.port = pair[1];
                                break;
                            case "interval":
                                this.interval = pair[1];
                                break;
                            case "id":
                                this.id = pair[1];
                                break;
                            case "prefix":
                                this.prefix = pair[1];
                                break;
                            case "key":
                                this.key = pair[1];
                                break;
                        }
                    }
                }

            }

            this.valid();

        } else {
            throw new SettingsException(R.string.app_settings_exception_bad_input_code);
        }
    }

    public void valid() throws SettingsException {
        if (!StringUtil.isNotEmpty(host) || !host.matches("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})$")) {
            throw new SettingsException(R.string.app_settings_exception_bad_host);
        } else if (!StringUtil.isNotEmpty(port) || !port.matches("^(\\d{0,4})$")) {
            throw new SettingsException(R.string.app_settings_exception_bad_port);
        } else if (StringUtil.isNotEmpty(interval) && (!StringUtil.isInteger(interval) || Integer.valueOf(interval) < 1)) {
            throw new SettingsException(R.string.app_settings_exception_bad_interval);
        } else if (!StringUtil.isNotEmpty(id)) {
            throw new SettingsException(R.string.app_settings_exception_bad_id);
        } else if (StringUtil.isNotEmpty(key) && key.length() != 16) {
            throw new SettingsException(R.string.app_settings_exception_bad_key);
        }
    }

    public void save(Context context) {
        BaseApplication application = BaseApplication.get(context);
        application.setServerAddress(host);
        application.setServerPort(Integer.valueOf(port));

        if (StringUtil.isNotEmpty(interval) && StringUtil.isInteger(interval) && Integer.valueOf(interval) >= 1) {
            application.setServerTTS(Integer.valueOf(interval));
        }

        application.setServerPrefix(prefix);
        application.setServerID(id);
        application.setServerKey(key);

    }
}
