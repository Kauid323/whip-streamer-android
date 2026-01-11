package org.webrtc;

import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;

/* loaded from: classes.jar:org/webrtc/NativeCapturerObserver.class */
class NativeCapturerObserver implements CapturerObserver {
    private final NativeAndroidVideoTrackSource nativeAndroidVideoTrackSource;

    @CalledByNative
    public NativeCapturerObserver(long nativeSource) {
        this.nativeAndroidVideoTrackSource = new NativeAndroidVideoTrackSource(nativeSource);
    }

    @Override // org.webrtc.CapturerObserver
    public void onCapturerStarted(boolean success) {
        this.nativeAndroidVideoTrackSource.setState(success);
    }

    @Override // org.webrtc.CapturerObserver
    public void onCapturerStopped() {
        this.nativeAndroidVideoTrackSource.setState(false);
    }

    @Override // org.webrtc.CapturerObserver
    public void onFrameCaptured(VideoFrame frame) {
        VideoProcessor.FrameAdaptationParameters parameters = this.nativeAndroidVideoTrackSource.adaptFrame(frame);
        if (parameters == null) {
            return;
        }
        VideoFrame.Buffer adaptedBuffer = frame.getBuffer().cropAndScale(parameters.cropX, parameters.cropY, parameters.cropWidth, parameters.cropHeight, parameters.scaleWidth, parameters.scaleHeight);
        this.nativeAndroidVideoTrackSource.onFrameCaptured(new VideoFrame(adaptedBuffer, frame.getRotation(), parameters.timestampNs));
        adaptedBuffer.release();
    }
}
