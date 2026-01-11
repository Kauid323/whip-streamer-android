package org.webrtc;

import android.content.Context;

/* loaded from: classes.jar:org/webrtc/ContextUtils.class */
public class ContextUtils {
    private static final String TAG = "ContextUtils";
    private static Context applicationContext;

    public static void initialize(Context applicationContext2) {
        if (applicationContext2 == null) {
            throw new IllegalArgumentException("Application context cannot be null for ContextUtils.initialize.");
        }
        applicationContext = applicationContext2;
    }

    @Deprecated
    public static Context getApplicationContext() {
        return applicationContext;
    }
}
