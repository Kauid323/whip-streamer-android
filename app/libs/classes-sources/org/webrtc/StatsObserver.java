package org.webrtc;

/* loaded from: classes.jar:org/webrtc/StatsObserver.class */
public interface StatsObserver {
    @CalledByNative
    void onComplete(StatsReport[] statsReportArr);
}
