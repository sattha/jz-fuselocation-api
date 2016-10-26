package com.jz.fuselocation.library;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.gson.Gson;
import com.jz.fuselocation.library.utility.GoogleUtils;
import com.jz.rp.library.RP;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.jz.fuselocation.library.utility.Precondition.checkIsIllegalState;

/**
 * Created by Sattha Puangput on 7/23/2015.
 */
public class FusedLocationManager implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {



    private final String TAG = getClass().getSimpleName();

    public static final String EXTRA_REQUEST_MODE = BuildConfig.APPLICATION_ID + ".extra.EXTRA_REQUEST_MODE";
    public static final String EXTRA_PERMISSION = BuildConfig.APPLICATION_ID + ".extra.EXTRA_PERMISSION";
    public static final String EXTRA_RESOLVE_CODE = BuildConfig.APPLICATION_ID + ".extra.EXTRA_RESOLVE_CODE";
    public static final String EXTRA_LOCATION_STATE = BuildConfig.APPLICATION_ID + ".extra.EXTRA_LOCATION_STATE";
    public static final int CODE_ERROR_CANT_FIX = 500;
    public static final int CODE_ERROR_GOOGLE_PLAY = 300;
    public static final int CODE_ERROR_PERMISSION = 310;
    public static final int CODE_ERROR_LOCATION = 320;
    public static final int CODE_RESULT_FAIL = 101;
    public static final int CODE_ERROR_SERVICE_NOT_START = 100;

    private static final int MODE_LAST_LOCATION = 1;
    private static final int MODE_UPDATE_LOCATION = 2;

    private final boolean IS_REQUEST_DISTANCE;
    private final int MIN_RETRY = 1;
    private final int MAX_RETRY;
    private final long RETRY_TIMEOUT;
    private final long REQUEST_INTERVAL;
    private final long REQUEST_FAST_INTERVAL;
    private final float REQUEST_DISTANCE;

    private boolean isShouldBlockCallbackToUser = true; // flag for stop callback result to user.
    private Context context;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private OnLocationUpdate onLocationUpdate;
    private OnLocationResponse onLocationResponse;

    private String[] locationPermissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    public FusedLocationManager(Builder builder) {
        this.context = builder.context;
        this.MAX_RETRY = builder.maxRetry;
        this.RETRY_TIMEOUT = builder.retryTimeOut;
        this.REQUEST_INTERVAL = builder.requestInterval;
        this.REQUEST_FAST_INTERVAL = builder.requestFastInterval;
        this.REQUEST_DISTANCE = builder.requestDistance;
        this.IS_REQUEST_DISTANCE = builder.isRequestDistance;
        configureAPI();
    }

    public static class Builder {

        private Context context;
        private int maxRetry = 3;
        private long retryTimeOut = 10 * 1000; // 10 sec.
        private long requestInterval = 30 * 60 * 1000; // 30 min.
        private long requestFastInterval = 30 * 60 * 1000; // 30 min.
        private float requestDistance = 500; // 500 m.
        private boolean isRequestDistance = true;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setRequestInterval(long milliSec) {
            this.requestInterval = milliSec;
            return this;
        }

        public Builder setRequestFastInterval(long milliSec) {
            this.requestFastInterval = milliSec;
            return this;
        }

        public Builder setRequestDistance(int m) {
            this.requestDistance = m;
            return this;
        }

        public Builder setRetryTimeout(long milliSec) {
            this.retryTimeOut = milliSec;
            return this;
        }

        public Builder setMaxRetry(int times) {
            this.maxRetry = times;
            return this;
        }

        public Builder setIsRequestDistance(boolean b) {
            this.isRequestDistance = b;
            return this;
        }

        public FusedLocationManager build() {
            return new FusedLocationManager(this);
        }
    }

    /**
     * Configure API
     * Calling this method to let's library configure its location spec
     */
    private void configureAPI() {
        // setup google API client
        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // setup location request criteria
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(REQUEST_INTERVAL);
        locationRequest.setFastestInterval(REQUEST_FAST_INTERVAL);
        if (IS_REQUEST_DISTANCE) {
            locationRequest.setSmallestDisplacement(REQUEST_DISTANCE);
        }
    }


    /**
     * Start using GPS listener
     * Calling this function will start using GPS in your app
     */
    public void start() {
        isShouldBlockCallbackToUser = false;
        if (!hasStartApi()) {
            googleApiClient.connect();
        }
    }

    /**
     * Stop using GPS listener
     * Calling this function will stop using GPS in your app
     */
    public void stop() {
        isShouldBlockCallbackToUser = true;
        if (hasStartApi()) {
            try {
                // disconnect google api
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
                googleApiClient.disconnect();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {
        // GoogleApiClient will automatically attempt to restore the connection.
        // Applications should disable UI components that require the service,
        // and wait for a call to onConnected(Bundle) to re-enable them.
        Log.e(TAG, "onConnectionSuspended() = " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //result	A ConnectionResult that can be used for resolving the error,
        // and deciding what sort of error occurred. To resolve the error,
        // the resolution must be started from an activity with a non-negative
        // requestCode passed to startResolutionForResult(Activity, int).
        // Applications should implement onActivityResult in their Activity to call connect()
        // again if the user has resolved the issue (resultCode is RESULT_OK).

        Log.e(TAG, connectionResult.getErrorCode() + ": " + connectionResult.getErrorMessage());
        stop();
    }

    @Override
    public void onLocationChanged(Location location) {
        // sent to onLocationUpdateListener.
        updateSuccessResultToCaller(MODE_UPDATE_LOCATION, location, "success");
    }

    /**
     * On Location Update
     * An interface to receive location update from google api
     */
    public void requestUpdateLocation(OnLocationUpdate l) {
        onLocationUpdate = l;

        hasGoogleApiPrompt(MODE_UPDATE_LOCATION)
                .flatMap(isPrompt -> hasLocationSettingsPrompt(MODE_UPDATE_LOCATION))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(isCanStart -> {
                    if (isCanStart) {
                        LocationServices.FusedLocationApi.requestLocationUpdates(
                                googleApiClient,
                                locationRequest,
                                this);
                    }
                });
    }

    public void removeUpdateLocationListener() {
        try {
            onLocationUpdate = null;
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get Last Location
     * Calling this method to get user last update location
     */

    public void requestLastLocation(OnLocationResponse l) {
        onLocationResponse = l;

        hasGoogleApiPrompt(MODE_LAST_LOCATION)
                .flatMap(new Func1<Boolean, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Boolean isGooglePrompt) {
                        if (isGooglePrompt) {
                            return hasLocationSettingsPrompt(MODE_LAST_LOCATION);
                        } else {
                            return Observable.error(
                                    new LocationConfigureThrowable(
                                            "configuration don't meet requirement"));
                        }
                    }
                })
                .flatMap(new Func1<Boolean, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Boolean isSettingsPrompt) {
                        if (isSettingsPrompt) {
                            return isWaitForConnection();
                        } else {
                            return Observable.error(
                                    new LocationConfigureThrowable(
                                            "configuration don't meet requirement"));
                        }
                    }
                })
                .flatMap(new Func1<Boolean, Observable<Location>>() {
                    @Override
                    public Observable<Location> call(Boolean isWaiting) {
                        if (!isWaiting) {
                            return fetchLocationData();
                        } else {
                            return Observable.error(new Throwable());
                        }
                    }
                })
                .repeatWhen(complete -> complete
                        .zipWith(Observable.range(MIN_RETRY, MAX_RETRY), (v, i) -> i)
                        .flatMap((Integer repeatCount) -> {
                            Log.i(TAG, "Repeat attempt: " + repeatCount);
                            if (repeatCount >= MAX_RETRY) {
                                return Observable.error(
                                        new LocationFetchThrowable("Repeat exceeds 3 times"));
                            } else {
                                return Observable.timer(RETRY_TIMEOUT, TimeUnit.MILLISECONDS);
                            }
                        }))
                .takeUntil(location -> location != null)
                .filter(location -> location != null)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        (result) -> {
                            Log.e(TAG, "onNext()");
                            updateSuccessResultToCaller(MODE_LAST_LOCATION, result, "success");
                        },
                        (error) -> {
                            Log.e(TAG, "onError(), " + error.getMessage());
                            if (error instanceof LocationFetchThrowable) {
                                updateFailureResultToCaller(
                                        MODE_LAST_LOCATION,
                                        error.getMessage()+ "",
                                        CODE_RESULT_FAIL);
                            } else if (error instanceof LocationConfigureThrowable) {
                                // do nothings. its method have already handle it.
                            }
                        },
                        () -> {
                            Log.e(TAG, "onComplete()");
                        });
    }

    private Observable<Boolean> hasGoogleApiPrompt(int mode) {

        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {

                if (!hasStartApi()) {
                    Log.e(TAG, context.getString(R.string.location_service_not_start));
                    subscriber.onNext(updateFailureResultToCaller(
                            mode,
                            context.getString(R.string.location_service_not_start),
                            CODE_ERROR_SERVICE_NOT_START));
                    subscriber.onCompleted();
                    return;
                }

                if (!GoogleUtils.isGooglePlayServicesAvailable(context)) {
                    Log.e(TAG, context.getString(R.string.google_service_fail));
                    subscriber.onNext(updateFailureResultToCaller(
                            mode,
                            context.getString(R.string.google_service_fail),
                            CODE_ERROR_GOOGLE_PLAY));
                    subscriber.onCompleted();
                    return;
                }

                if (!RP.isPermissionGranted(context, locationPermissions)) {
                    Log.e(TAG, context.getString(R.string.android_permission_fail));
                    subscriber.onNext(updateFailureResultToCaller(
                            mode,
                            context.getString(R.string.android_permission_fail),
                            CODE_ERROR_PERMISSION));
                    subscriber.onCompleted();
                    return;
                }

                subscriber.onNext(true);
                subscriber.onCompleted();
            }
        });
    }

    private Observable<Boolean> hasLocationSettingsPrompt(int mode) {

        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {

                LocationSettingsRequest locationSettingsRequest =
                        new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest)
                                .setAlwaysShow(true) //this is show settings dialog
                                .build();

                PendingResult<LocationSettingsResult> result =
                        LocationServices.SettingsApi.checkLocationSettings(
                                googleApiClient,
                                locationSettingsRequest);

                result.setResultCallback(settings -> {
                    Status status = settings.getStatus();
                    LocationSettingsStates state = settings.getLocationSettingsStates();

                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS: {
                            // All location settings are satisfied.
                            // The client can initialize location requests here.
                            subscriber.onNext(true);
                            subscriber.onCompleted();
                            break;
                        }
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED: {
                            // Location settings are not satisfied. but, we can fix the.
                            subscriber.onNext(updateFailureResultToCaller(
                                    mode,
                                    context.getString(R.string.location_settings_need_fix),
                                    CODE_ERROR_LOCATION,
                                    status));
                            subscriber.onCompleted();
                            break;
                        }
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE: {
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            // default: prevent unknown case later.
                            subscriber.onNext(updateFailureResultToCaller(
                                    mode,
                                    context.getString(R.string.location_settings_fail),
                                    CODE_ERROR_CANT_FIX));
                            subscriber.onCompleted();
                            break;
                        }
                        default: {
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            // default: prevent unknown case later.
                            subscriber.onNext(updateFailureResultToCaller(
                                    mode,
                                    context.getString(R.string.location_settings_fail),
                                    CODE_ERROR_CANT_FIX));
                            subscriber.onCompleted();
                        }
                    }
                });
            }
        });
    }

    private Observable<Boolean> isWaitForConnection() {

        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                Log.e(TAG, "isWait()" + Boolean.toString((googleApiClient != null) && (googleApiClient.isConnecting())));
                subscriber.onNext((googleApiClient != null) && (googleApiClient.isConnecting()));
                subscriber.onCompleted();
            }
        }).repeatWhen(complete -> complete
                .zipWith(Observable.range(MIN_RETRY, MAX_RETRY), (v, i) -> i)
                .flatMap((Integer repeatCount) -> {
                    Log.i(TAG, "Repeat attempt: " + repeatCount);
                    if (repeatCount >= MAX_RETRY) {
                        return Observable.error(
                                new LocationFetchThrowable("Repeat exceeds 3 times"));
                    } else {
                        return Observable.timer(RETRY_TIMEOUT, TimeUnit.SECONDS);
                    }
                }))
                .takeUntil(isWait -> !isWait)
                .retryWhen(error -> error
                        .zipWith(Observable.range(MIN_RETRY, MAX_RETRY), (v, i) -> i)
                        .flatMap((Integer retryCount) -> {
                            Log.i(TAG, "Retry attempt: " + retryCount);
                            if (retryCount >= MIN_RETRY) {
                                return Observable.error(
                                        new LocationFetchThrowable("Retry exceeds 3 times"));
                            } else {
                                return Observable.timer(RETRY_TIMEOUT, TimeUnit.SECONDS);
                            }
                        }))
                .filter(isWait -> !isWait);
    }

    private Observable<Location> fetchLocationData() {

        return Observable.create(new Observable.OnSubscribe<Location>() {
            @Override
            public void call(Subscriber<? super Location> subscriber) {
                Location lastLocation =
                        LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                Log.e(TAG, "location() is null >> " + Boolean.toString(lastLocation == null));
                subscriber.onNext(lastLocation);
                subscriber.onCompleted();
            }
        });
    }

    /**
     * Is Connect
     * Calling this function for check this instance was call start() or not.
     */
    public boolean hasStartApi() {
        checkIsIllegalState(
                googleApiClient,
                context.getString(R.string.iilegal_state_start_location));

        return (googleApiClient.isConnected() || googleApiClient.isConnecting());
    }

    private boolean updateSuccessResultToCaller(int mode, Location loc, String msg) {
        return updateLocationResultToCaller(mode, true, loc, msg, 0, null);
    }

    private boolean updateFailureResultToCaller(int mode, String msg, int code) {
        return updateLocationResultToCaller(mode, false, null, msg, code, null);
    }

    private boolean updateFailureResultToCaller(int mode, String msg, int code, Status status) {
        return updateLocationResultToCaller(mode, false, null, msg, code, status);
    }

    private boolean updateLocationResultToCaller(int mode,
                                                 boolean isSuccess,
                                                 Location loc,
                                                 String msg,
                                                 int code,
                                                 Status status) {

        LocationResult locationResult = new LocationResult();
        locationResult.setCode(code);
        locationResult.setLocation(loc);
        locationResult.setSuccessfully(isSuccess);
        locationResult.setMessage(msg);
        locationResult.setLocationSetting(status);
        locationResult.setMode(mode);

        if (mode == MODE_LAST_LOCATION) {

            if (isSuccess) {
                if (onLocationResponse != null && !isShouldBlockCallbackToUser)
                    onLocationResponse.locationResponseSuccess(serviceInterface, locationResult);
            } else {
                if (onLocationResponse != null && !isShouldBlockCallbackToUser)
                    onLocationResponse.locationResponseFailure(serviceInterface, locationResult);
            }

        } else if (mode == MODE_UPDATE_LOCATION) {

            if (isSuccess) {
                if (onLocationUpdate != null && !isShouldBlockCallbackToUser)
                    onLocationUpdate.locationResponseUpdate(serviceInterface, locationResult);
            } else {
                if (onLocationUpdate != null && !isShouldBlockCallbackToUser)
                    onLocationUpdate.locationResponseFailure(serviceInterface, locationResult);
            }

        }

        return isSuccess;
    }


    private ServiceInterface serviceInterface = new ServiceInterface() {
        @Override
        public boolean isCanResolveLocationManagerService(LocationResult result) {
            return isCanResolveLocationInternal(result.getCode());
        }

        @Override
        public void resolveLocationManagerService(Activity activity,
                                                  int requestCode,
                                                  LocationResult result) {

            FusedLocationManager.this.resolveLocationManagerService(
                    result.getMode(),
                    activity,
                    requestCode,
                    result.getCode(),
                    result.getLocationSetting());
        }

        @Override
        public void resolveLocationManagerService(Fragment fragment,
                                                  int requestCode,
                                                  LocationResult result) {

            FusedLocationManager.this.resolveLocationManagerService(
                    result.getMode(),
                    fragment,
                    requestCode,
                    result.getCode(),
                    result.getLocationSetting());
        }

        @Override
        public void stop() {
            FusedLocationManager.this.stop();
        }
    };

    private boolean isCanResolveLocationInternal(int code) {
        return (code == CODE_ERROR_GOOGLE_PLAY
                || code == CODE_ERROR_LOCATION
                || code == CODE_ERROR_PERMISSION);
    }

    private void resolveLocationManagerService(int mode,
                                               Activity activity,
                                               int requestCode,
                                               int errorCode,
                                               Status status) {
        if (errorCode == CODE_ERROR_GOOGLE_PLAY) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_GOOGLE_PLAY);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            activity.startActivityForResult(i, requestCode);
        } else if (errorCode == CODE_ERROR_PERMISSION) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_PERMISSION);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            i.putExtra(EXTRA_PERMISSION, locationPermissions);
            activity.startActivityForResult(i, requestCode);
        } else if (errorCode == CODE_ERROR_LOCATION) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_LOCATION);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            i.putExtra(EXTRA_LOCATION_STATE, status);
            activity.startActivityForResult(i, requestCode);
        }
    }

    private void resolveLocationManagerService(int mode,
                                               Fragment fragment,
                                               int requestCode,
                                               int errorCode,
                                               Status status) {
        if (errorCode == CODE_ERROR_GOOGLE_PLAY) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_GOOGLE_PLAY);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            fragment.startActivityForResult(i, requestCode);
        } else if (errorCode == CODE_ERROR_PERMISSION) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_PERMISSION);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            i.putExtra(EXTRA_PERMISSION, locationPermissions);
            fragment.startActivityForResult(i, requestCode);
        } else if (errorCode == CODE_ERROR_LOCATION) {
            Intent i = new Intent(context, FixLocationPermissionActivity.class);
            i.putExtra(EXTRA_RESOLVE_CODE, CODE_ERROR_LOCATION);
            i.putExtra(EXTRA_REQUEST_MODE, mode);
            i.putExtra(EXTRA_LOCATION_STATE, status);
            fragment.startActivityForResult(i, requestCode);
        }
    }

    public boolean checkActivityResultCallback(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {

            if (!hasStartApi()) {
                start();
            }

            if (data.hasExtra(EXTRA_REQUEST_MODE)) {
                int mode = data.getIntExtra(EXTRA_REQUEST_MODE, 0);
                if (mode == (MODE_LAST_LOCATION | MODE_UPDATE_LOCATION)) {
                    requestLastLocation(onLocationResponse);
                    requestUpdateLocation(onLocationUpdate);
                } else if (mode == MODE_LAST_LOCATION) {
                    Log.e(TAG, "checkActivityResultCallback, mode: " + mode);
                    requestLastLocation(onLocationResponse);
                } else if (mode == MODE_UPDATE_LOCATION) {
                    requestUpdateLocation(onLocationUpdate);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * To String
     * A method which convert Location object to json string
     */
    public String toString(Location loc) {
        Gson gson = new Gson();
        return gson.toJson(loc);
    }

    /**
     * To Location
     * A method which convert Json String of Location object object to Location Object
     */
    public Location toLocation(String sLocation) {
        Gson gson = new Gson();
        return gson.fromJson(sLocation, Location.class);
    }

    /**
     * Clean Up
     * Calling this function will clean up all flag in this class
     */
    public void cleanUp() {
        if (googleApiClient != null) {
            googleApiClient.unregisterConnectionCallbacks(this);
            googleApiClient.unregisterConnectionFailedListener(this);
            if (hasStartApi()) {
                googleApiClient.disconnect();
            }
            googleApiClient = null;
        }

        if (locationRequest != null) {
            locationRequest = null;
        }
    }

    public interface ServiceInterface {
        boolean isCanResolveLocationManagerService(LocationResult result);

        void resolveLocationManagerService(Activity activity, int requestCode, LocationResult result);

        void resolveLocationManagerService(Fragment fragment, int requestCode, LocationResult result);

        void stop();
    }
}
