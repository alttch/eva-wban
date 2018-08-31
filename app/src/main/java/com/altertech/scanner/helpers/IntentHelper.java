package com.altertech.scanner.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.altertech.scanner.ui.DeviceActivity;
import com.altertech.scanner.ui.MainActivity;
import com.altertech.scanner.ui.SettingsActivity;

/**
 * Created by oshevchuk on 26.07.2018
 */
public class IntentHelper {

    public enum REQUEST_CODES {
        DEVICE_ACTIVITY(1001),
        BAR_CODE_ACTIVITY(1002);
        int code;

        REQUEST_CODES(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public static void showMainActivity(Context ctx) {
        ctx.startActivity(new Intent(ctx, MainActivity.class));
        ((Activity) ctx).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void showDeviceActivity(Context ctx) {
        ((Activity) ctx).startActivityForResult(new Intent(ctx, DeviceActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_CODES.DEVICE_ACTIVITY.getCode());
        ((Activity) ctx).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void showSettingsActivity(Context ctx, boolean from_start) {
        ctx.startActivity(new Intent(ctx, SettingsActivity.class).putExtra("from_start", from_start));
        ((Activity) ctx).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
