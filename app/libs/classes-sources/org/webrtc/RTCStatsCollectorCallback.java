package org.webrtc;

/* loaded from: classes.jar:org/webrtc/RTCStatsCollectorCallback.class */
public interface RTCStatsCollectorCallback {
    @CalledByNative
    void onStatsDelivered(RTCStatsReport rTCStatsReport);
}
