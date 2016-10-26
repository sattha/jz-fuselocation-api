package com.jz.fuselocation.library;

/**
 * Created by Sattha Puangput on 7/23/2015.
 */
public interface OnLocationUpdate {

    void locationResponseUpdate(FusedLocationManager.ServiceInterface manager,
                                LocationResult locationResult);

    void locationResponseFailure(FusedLocationManager.ServiceInterface manager,
                                 LocationResult locationResult);
}
