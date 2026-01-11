package org.webrtc;

/* loaded from: classes.jar:org/webrtc/SdpObserver.class */
public interface SdpObserver {
    @CalledByNative
    void onCreateSuccess(SessionDescription sessionDescription);

    @CalledByNative
    void onSetSuccess();

    @CalledByNative
    void onCreateFailure(String str);

    @CalledByNative
    void onSetFailure(String str);
}
