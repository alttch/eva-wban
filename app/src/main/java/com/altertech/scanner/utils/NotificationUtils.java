package com.altertech.scanner.utils;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.altertech.scanner.R;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by oshevchuk on 25.06.2018
 */
public class NotificationUtils {

    public final static int NOTIFICATION_ID = 123;

    public enum ChannelId {
        CONNECTED("CONNECTED", "Connected channel"), DISCONNECTED("DISCONNECTED", "Disconnected channel"), ERROR("ERROR", "Error channel"), DEFAULT("DEFAULT", "Default channel");
        String id, name;

        ChannelId(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<String> getListIDs() {
            List<String> strings = new ArrayList<>();
            for (ChannelId id : values()) {
                strings.add(id.getId());
            }
            return strings;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel getChannelByChannelId(ChannelId channelId) {
        NotificationChannel channel = new NotificationChannel(channelId.getId(), channelId.getName(), NotificationManager.IMPORTANCE_HIGH);
        if (channelId.equals(ChannelId.CONNECTED)) {
            channel.enableVibration(true);
        } else if (channelId.equals(ChannelId.DISCONNECTED)) {
            channel.enableVibration(true);
        } else if (channelId.equals(ChannelId.ERROR)) {
            channel.enableVibration(true);
        } else if (channelId.equals(ChannelId.DEFAULT)) {
            channel.enableVibration(false);
        }
        channel.setSound(null, null);
        return channel;
    }

    private static void createChannels(Context context) {
        NotificationManager notificationManager = getNotificationManager(context);
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(getChannelByChannelId(ChannelId.CONNECTED));
                notificationManager.createNotificationChannel(getChannelByChannelId(ChannelId.DISCONNECTED));
                notificationManager.createNotificationChannel(getChannelByChannelId(ChannelId.ERROR));
                notificationManager.createNotificationChannel(getChannelByChannelId(ChannelId.DEFAULT));
            }
        }
    }

    private static NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static void show(Context context, ChannelId channelId, String title) {
        NotificationManager notificationManager = getNotificationManager(context);
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                NotificationUtils.createChannels(context);

                Notification notification = new NotificationCompat.Builder(context, channelId.getId())
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title != null ? title : StringUtil.EMPTY_STRING)
                        .build();
                notificationManager.notify(NOTIFICATION_ID, notification);
            } else {
                notificationManager.notify(NOTIFICATION_ID, generateSimpleNotification(context, channelId, title));
            }
        }
    }

    private static Notification generateSimpleNotification(Context context, ChannelId channelId, String title) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : StringUtil.EMPTY_STRING);

        if (channelId.equals(ChannelId.CONNECTED)) {
            notification.setVibrate(new long[]{0, 500});
        } else if (channelId.equals(ChannelId.DISCONNECTED)) {
            notification.setVibrate(new long[]{0, 500});
        } else if (channelId.equals(ChannelId.ERROR)) {
            notification.setVibrate(new long[]{0, 500});
        } else if (channelId.equals(ChannelId.DEFAULT)) {
        }

        return notification.build();
    }

    public static Notification generateBaseNotification(Context context, ChannelId channelId, String title) {
        return new NotificationCompat.Builder(context, channelId.getId())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : StringUtil.EMPTY_STRING).build();
    }

}
