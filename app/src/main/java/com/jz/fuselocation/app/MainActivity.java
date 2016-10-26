package com.jz.fuselocation.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jz.fuselocation.library.FusedLocationManager;
import com.jz.fuselocation.library.LocationResult;
import com.jz.fuselocation.library.OnLocationResponse;
import com.jz.fuselocation.library.OnLocationUpdate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_CODE = 1000;

    private TextView tvLat;
    private TextView tvLat2;
    private TextView tvLng;
    private TextView tvLng2;
    private TextView tvLastUpdate;
    private TextView tvLastUpdate2;
    private TextView tvStatus;
    private Button btnGetLocation;
    private Button btnFetchLocation;
    private FusedLocationManager manager;

    private boolean isToggle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLat = (TextView) findViewById(R.id.tvLat);
        tvLat2 = (TextView) findViewById(R.id.tvLat2);
        tvLng = (TextView) findViewById(R.id.tvLng);
        tvLng2 = (TextView) findViewById(R.id.tvLng2);
        tvLastUpdate = (TextView) findViewById(R.id.tvLastUpdate);
        tvLastUpdate2 = (TextView) findViewById(R.id.tvLastUpdate2);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnGetLocation = (Button) findViewById(R.id.btnGetLocation);
        btnFetchLocation = (Button) findViewById(R.id.btnFetchLocation);

        btnFetchLocation.setOnClickListener(this);
        btnGetLocation.setOnClickListener(this);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        manager = new FusedLocationManager.Builder(this)
                .setIsRequestDistance(false)
                .setRequestDistance(500)        // update every 500 m.
                .setRequestFastInterval(5000)   // fastest possible update 5 sec.
                .setRequestInterval(5000)       // update every 5 sec.
                .setMaxRetry(3)                 // retry 3 time if cannot fetch location
                .setRetryTimeout(1000)          // retry again after 1 sec.
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        manager.start();
    }

    @Override
    protected void onPause() {
        manager.stop();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnFetchLocation: {
                isToggle = !isToggle;
                tvStatus.setText(getString(R.string.status, (isToggle ? "ON" : "OFF")));

                manager.requestUpdateLocation(new OnLocationUpdate() {
                    @Override
                    public void locationResponseUpdate(FusedLocationManager.ServiceInterface manager,
                                                       LocationResult locationResult) {

                        tvLat2.setText(getString(
                                R.string.lat,
                                locationResult.getLocation().getLatitude() + ""));
                        tvLng2.setText(getString(
                                R.string.lng,
                                locationResult.getLocation().getLongitude() + ""));

                        SimpleDateFormat date = new SimpleDateFormat(
                                "yyyy-mm-dd HH:mm:ss",
                                Locale.getDefault());

                        tvLastUpdate2.setText(date.format(new Date()));
                    }

                    @Override
                    public void locationResponseFailure(FusedLocationManager.ServiceInterface manager,
                                                        LocationResult locationResult) {

                        if (manager.isCanResolveLocationManagerService(locationResult)) {
                            manager.resolveLocationManagerService(
                                    MainActivity.this,
                                    REQUEST_CODE,
                                    locationResult);
                        } else {
                            Toast.makeText(
                                    MainActivity.this,
                                    locationResult.getMessage(),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });

                break;
            }
            case R.id.btnGetLocation: {

                manager.requestLastLocation(new OnLocationResponse() {
                    @Override
                    public void locationResponseSuccess(FusedLocationManager.ServiceInterface manager,
                                                        LocationResult locationResult) {

                        tvLat.setText(getString(
                                R.string.lat,
                                locationResult.getLocation().getLatitude() + ""));
                        tvLng.setText(getString(
                                R.string.lng,
                                locationResult.getLocation().getLongitude() + ""));

                        SimpleDateFormat date = new SimpleDateFormat(
                                "yyyy-mm-dd HH:mm:ss",
                                Locale.getDefault());

                        tvLastUpdate.setText(date.format(new Date()));
                    }

                    @Override
                    public void locationResponseFailure(FusedLocationManager.ServiceInterface manager,
                                                        LocationResult locationResult) {

                        if (manager.isCanResolveLocationManagerService(locationResult)) {
                            manager.resolveLocationManagerService(
                                    MainActivity.this,
                                    REQUEST_CODE,
                                    locationResult);
                        } else {
                            Toast.makeText(
                                    MainActivity.this,
                                    locationResult.getMessage(),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });

                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
             if (manager.checkActivityResultCallback(resultCode, data)) {
                 Log.i(TAG, "resolved location successfully");
             } else {
                 Log.i(TAG, "fail to resolved location");
             }
        }
    }
}
