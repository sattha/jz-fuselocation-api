package com.jz.fuselocation.library;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.common.api.Status;

/**
 * Created by X-tivity on 10/5/2016 AD.
 */

public class LocationResult implements Parcelable {

    private boolean isSuccessfully;
    private int code;
    private Location location;
    private String message;
    private Status locationSetting;
    private int mode;

    public LocationResult() {

    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccessfully() {
        return isSuccessfully;
    }

    public void setSuccessfully(boolean successfully) {
        isSuccessfully = successfully;
    }

    public Status getLocationSetting() {
        return locationSetting;
    }

    public void setLocationSetting(Status locationSetting) {
        this.locationSetting = locationSetting;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    protected LocationResult(Parcel in) {
        isSuccessfully = in.readByte() != 0;
        code = in.readInt();
        location = in.readParcelable(Location.class.getClassLoader());
        message = in.readString();
        locationSetting = in.readParcelable(Status.class.getClassLoader());
        mode = in.readInt();
    }

    public static final Creator<LocationResult> CREATOR = new Creator<LocationResult>() {
        @Override
        public LocationResult createFromParcel(Parcel in) {
            return new LocationResult(in);
        }

        @Override
        public LocationResult[] newArray(int size) {
            return new LocationResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeByte((byte) (isSuccessfully ? 1 : 0));
        parcel.writeInt(code);
        parcel.writeParcelable(location, i);
        parcel.writeString(message);
        parcel.writeParcelable(locationSetting, i);
        parcel.writeInt(mode);
    }

    // IMPLEMENT PARCELABLE


}
