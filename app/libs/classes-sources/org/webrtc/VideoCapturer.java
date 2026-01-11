package org.webrtc;

import android.content.Context;

/* loaded from: classes.jar:org/webrtc/VideoCapturer.class */
public interface VideoCapturer {
    void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver);

    void startCapture(int i, int i2, int i3);

    void stopCapture() throws InterruptedException;

    void changeCaptureFormat(int i, int i2, int i3);

    void dispose();

    boolean isScreencast();
}
