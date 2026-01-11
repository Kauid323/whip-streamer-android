package org.webrtc;

import org.webrtc.Logging;

/* loaded from: classes.jar:org/webrtc/JNILogging.class */
class JNILogging {
    private final Loggable loggable;

    public JNILogging(Loggable loggable) {
        this.loggable = loggable;
    }

    @CalledByNative
    public void logToInjectable(String message, Integer severity, String tag) {
        this.loggable.onLogMessage(message, Logging.Severity.values()[severity.intValue()], tag);
    }
}
