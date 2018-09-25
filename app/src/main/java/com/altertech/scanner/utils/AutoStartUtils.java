package com.altertech.scanner.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.altertech.scanner.R;
import com.altertech.scanner.helpers.ToastHelper;

/**
 * Created by oshevchuk on 25.09.2018
 */
public class AutoStartUtils {

    public static boolean isUnAutoStartOS() {
        return android.os.Build.MANUFACTURER.equalsIgnoreCase("xiaomi")
                || android.os.Build.MANUFACTURER.equalsIgnoreCase("oppo")
                || android.os.Build.MANUFACTURER.equalsIgnoreCase("vivo")
                || android.os.Build.MANUFACTURER.equalsIgnoreCase("Letv")
                || android.os.Build.MANUFACTURER.equalsIgnoreCase("Honor");
    }

    public static void showAutoStartSettings(Context context) {
        try {
            if ("xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
                context.startActivity(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")));
            } else if ("oppo".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
                context.startActivity(new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")));
            } else if ("vivo".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
                context.startActivity(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")));
            } else if ("Letv".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
                context.startActivity(new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")));
            } else if ("Honor".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
                context.startActivity(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")));
            } else {
                ToastHelper.toast(context, R.string.app_settings_auto_start_exception_unsupportable_action);
            }
        } catch (Exception e) {
            ToastHelper.toast(context, R.string.app_settings_auto_start_exception_error);
        }
    }

}
