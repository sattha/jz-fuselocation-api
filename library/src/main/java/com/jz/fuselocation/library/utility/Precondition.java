package com.jz.fuselocation.library.utility;

/**
 * Created by sattha on 5/9/2559.
 */

public class Precondition {

    public static boolean checkIsNull(Object object, String error) {
        if (object == null) {
            throw new NullPointerException(error);
        } else {
            return true;
        }
    }

    public static boolean checkIsIllegalState(Object object, String error) {
        if (object == null) {
            throw new IllegalStateException(error);
        } else {
            return true;
        }
    }
}
