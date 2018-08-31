package com.altertech.scanner.utils;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.altertech.scanner.R;


/**
 * Created by oshevchuk on 25.06.2018
 */
public class NotificationUtils {

    private static final String NOTIFICATION_CHANNEL_ID = "com.altertech.scanner.custom";

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel generateNotificationChannel(int importance) {
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "custom", importance);
        notificationChannel.setVibrationPattern(new long[]{500, 1000});
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.RED);
        return notificationChannel;
    }

    public static NotificationManager getNotificationManager(Context context, int importance) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(generateNotificationChannel(importance));
            }
        }
        return notificationManager;
    }

    public static Notification generateNotification(Context context, String title) {
        return new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(context.getResources().getColor(R.color.app_white))
                .setContentTitle(title != null ? title : StringUtil.EMPTY_STRING)
                .setLights(Color.WHITE, 1000, 3000).build();
    }

}
