package com.jz.fuselocation.library;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Status;
import com.jz.rp.library.RP;
import com.jz.rp.library.RPResult;

public class FixLocationPermissionActivity extends Activity {

    private static final int REQUEST_CODE_LOCATION_PROVIDERS_SETTING = 1000;
    private static final int REQUEST_CODE_PLAY_SERVICES = 2000;
    private static final int REQUEST_CODE_PERMISSION = 3000;

    private int mode = 0;
    private int resolveCode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (savedInstanceState != null) {
            mode = savedInstanceState.getInt("mode", 0);
            resolveCode = savedInstanceState.getInt("code", 0);
        }

        Intent i = getIntent();
        if (i != null) {

            mode = i.getIntExtra(FusedLocationManager.EXTRA_REQUEST_MODE, 0);
            resolveCode = i.getIntExtra(FusedLocationManager.EXTRA_RESOLVE_CODE, 0);

            if (resolveCode == FusedLocationManager.CODE_ERROR_GOOGLE_PLAY) {
                resolveGooglePlay();
            } else if (resolveCode == FusedLocationManager.CODE_ERROR_PERMISSION) {
                String[] permissions =
                        i.getStringArrayExtra(FusedLocationManager.EXTRA_PERMISSION);
                resolvePermission(permissions);
            } else if (resolveCode == FusedLocationManager.CODE_ERROR_LOCATION) {
                Status status =
                        i.getParcelableExtra(FusedLocationManager.EXTRA_LOCATION_STATE);
                resolveLocationSettings(status);
            } else {
                dismissActivity(false);
            }

        } else {
            dismissActivity(false);
        }
    }



    private void dismissActivity(boolean isSuccess) {
        Intent i = new Intent();
        i.putExtra(FusedLocationManager.EXTRA_REQUEST_MODE, mode);
        if (isSuccess) {
            setResult(RESULT_OK, i);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void resolveGooglePlay() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                Dialog errorDialog = googleApiAvailability
                        .getErrorDialog(this, status, REQUEST_CODE_PLAY_SERVICES);

                errorDialog.setCancelable(false);
                errorDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_TOAST);
                errorDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        resolveGooglePlay();
                    }
                });
                errorDialog.show();
            }
        } else {
            dismissActivity(false);
        }
    }

    private void resolvePermission(String[] permissions) {
        if (permissions != null && permissions.length != 0) {
            RP.requestPermission(this, permissions, REQUEST_CODE_PERMISSION);
        } else {
            dismissActivity(false);
        }
    }

    private void resolveLocationSettings(Status status) {
        try {
            // Show the dialog by calling startResolutionForResult(),
            // and check the result in onActivityResult().
            status.startResolutionForResult(this, REQUEST_CODE_LOCATION_PROVIDERS_SETTING);
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
            dismissActivity(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSION) {

            RPResult rpResult = RP.validateOnRequestPermissionsResult(
                    FixLocationPermissionActivity.this,
                    permissions,
                    grantResults);

            if (rpResult.isSuccess()) {
                dismissActivity(true);
            } else {
                if (rpResult.isSomePermissionDisabled()) {
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.permission_disable)
                            .setPositiveButton(R.string.fix, (dialog, w) -> {
                                RP.openAppDetailsActivity(this);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, w) -> {
                                dialog.dismiss();
                                dismissActivity(false);
                            });
                } else {
                    dismissActivity(false);
                }
            }
        } else {
            dismissActivity(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_LOCATION_PROVIDERS_SETTING) {
            dismissActivity(resultCode == Activity.RESULT_OK);
        } else if (requestCode == REQUEST_CODE_PLAY_SERVICES) {
            if (resultCode == Activity.RESULT_CANCELED) {
                new AlertDialog.Builder(this)
                        .setMessage("unable to resolve Google Play services.")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dismissActivity(false);
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                dismissActivity(false);
                            }
                        })
                        .show();
            } else if (resultCode == Activity.RESULT_OK) {
                dismissActivity(true);
            }  else {
                dismissActivity(false);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Intent i = getIntent();
        if (i != null) {
            mode = mode | i.getIntExtra(FusedLocationManager.EXTRA_REQUEST_MODE, 0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mode", mode);
        outState.putInt("code", resolveCode);
    }
}
