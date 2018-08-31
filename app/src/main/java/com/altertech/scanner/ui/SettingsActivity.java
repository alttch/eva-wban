package com.altertech.scanner.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import com.altertech.scanner.BaseApplication;
import com.altertech.scanner.R;
import com.altertech.scanner.helpers.IntentHelper;
import com.altertech.scanner.helpers.ToastHelper;
import com.altertech.scanner.ui.models.SettingsModel;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 201;

    private EditText a_settings_port;
    private EditText a_settings_address;
    private EditText a_settings_tts;
    private EditText a_settings_id;
    private EditText a_settings_prefix;
    private EditText a_settings_key;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        this.initialization();
    }

    private void initialization() {

        this.a_settings_port = findViewById(R.id.a_settings_port);
        this.a_settings_port.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerPort()));

        this.a_settings_address = findViewById(R.id.a_settings_address);
        this.a_settings_address.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerAddress()));

        this.a_settings_tts = findViewById(R.id.a_settings_tts);
        this.a_settings_tts.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerTTS()));

        this.a_settings_id = findViewById(R.id.a_settings_id);
        this.a_settings_id.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerID()));

        this.a_settings_prefix = findViewById(R.id.a_settings_prefix);
        this.a_settings_prefix.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerPrefix()));

        this.a_settings_key = findViewById(R.id.a_settings_key);
        this.a_settings_key.setText(String.valueOf(BaseApplication.get(SettingsActivity.this).getServerKey()));

        findViewById(R.id.a_settings_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                SettingsModel model = new SettingsModel(a_settings_address.getText().toString(),
                        a_settings_port.getText().toString(),
                        a_settings_tts.getText().toString(),
                        a_settings_prefix.getText().toString(),
                        a_settings_id.getText().toString(),
                        a_settings_key.getText().toString()
                );
                try {
                    model.valid();
                    model.save(SettingsActivity.this);
                    SettingsActivity.this.finish();
                } catch (SettingsModel.SettingsException e) {
                    ToastHelper.toast(SettingsActivity.this, e.getCustomMessage());
                }
            }
        });

        findViewById(R.id.a_settings_barcode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(SettingsActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(new Intent(SettingsActivity.this, ScannedBarcodeActivity.class), IntentHelper.REQUEST_CODES.BAR_CODE_ACTIVITY.getCode());
                } else {
                    ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }
        });

        findViewById(R.id.title_bar_controls_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SettingsActivity.this.onBackPressed();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IntentHelper.REQUEST_CODES.BAR_CODE_ACTIVITY.getCode() && resultCode == RESULT_OK) {
            SettingsActivity.this.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(new Intent(SettingsActivity.this, ScannedBarcodeActivity.class), IntentHelper.REQUEST_CODES.BAR_CODE_ACTIVITY.getCode());
        }
    }

    @Override
    public void onBackPressed() {
        if (getIntent().getBooleanExtra("from_start", false)) {
            IntentHelper.showMainActivity(this);
        }
        this.finish();
    }
}
