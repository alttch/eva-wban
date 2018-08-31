package com.altertech.scanner.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.helpers.IntentHelper;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by oshevchuk on 29.08.2018
 */
public class SplashActivity extends AppCompatActivity {

    private boolean scheduled = false;
    private Timer splashTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);
        splashTimer = new Timer();
        splashTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                initializationAction();
            }
        }, 1000);
        scheduled = true;
    }

    private void initializationAction() {
        if (BaseApplication.get(this).isFirstStart()) {
            IntentHelper.showSettingsActivity(this, true);
            BaseApplication.get(this).setFirstStart(false);
        } else {
            IntentHelper.showMainActivity(this);
        }

        this.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduled)
            splashTimer.cancel();
        splashTimer.purge();
    }
}