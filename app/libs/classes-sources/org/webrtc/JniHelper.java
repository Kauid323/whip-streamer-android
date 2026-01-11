package org.webrtc;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/* loaded from: classes.jar:org/webrtc/JniHelper.class */
class JniHelper {
    JniHelper() {
    }

    @CalledByNative
    static byte[] getStringBytes(String s) {
        try {
            return s.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("ISO-8859-1 is unsupported");
        }
    }

    @CalledByNative
    static Object getStringClass() {
        return String.class;
    }

    @CalledByNative
    static Object getKey(Map.Entry entry) {
        return entry.getKey();
    }

    @CalledByNative
    static Object getValue(Map.Entry entry) {
        return entry.getValue();
    }
}
