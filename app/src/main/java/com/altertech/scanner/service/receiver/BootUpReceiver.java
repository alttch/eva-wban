package com.altertech.scanner.service.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.ui.SplashActivity;
import com.altertech.scanner.utils.AutoStartUtils;

/**
 * Created by oshevchuk on 25.09.2018
 */
public class BootUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && (AutoStartUtils.isUnAutoStartOS() || BaseApplication.get(context).getAutoStartState())) {
            context.startActivity(new Intent(context, SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}