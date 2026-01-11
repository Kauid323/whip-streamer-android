package org.webrtc;

import org.webrtc.Logging;

/* loaded from: classes.jar:org/webrtc/CallSessionFileRotatingLogSink.class */
public class CallSessionFileRotatingLogSink {
    private long nativeSink;

    private static native long nativeAddSink(String str, int i, int i2);

    private static native void nativeDeleteSink(long j);

    private static native byte[] nativeGetLogData(String str);

    public static byte[] getLogData(String dirPath) {
        if (dirPath == null) {
            throw new IllegalArgumentException("dirPath may not be null.");
        }
        return nativeGetLogData(dirPath);
    }

    public CallSessionFileRotatingLogSink(String dirPath, int maxFileSize, Logging.Severity severity) {
        if (dirPath == null) {
            throw new IllegalArgumentException("dirPath may not be null.");
        }
        this.nativeSink = nativeAddSink(dirPath, maxFileSize, severity.ordinal());
    }

    public void dispose() {
        if (this.nativeSink != 0) {
            nativeDeleteSink(this.nativeSink);
            this.nativeSink = 0L;
        }
    }
}
