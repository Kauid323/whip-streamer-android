package org.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.MediaStreamTrack;

/* loaded from: classes.jar:org/webrtc/RtpReceiver.class */
public class RtpReceiver {
    private long nativeRtpReceiver;
    private long nativeObserver;

    @Nullable
    private MediaStreamTrack cachedTrack;

    /* loaded from: classes.jar:org/webrtc/RtpReceiver$Observer.class */
    public interface Observer {
        @CalledByNative("Observer")
        void onFirstPacketReceived(MediaStreamTrack.MediaType mediaType);
    }

    private static native long nativeGetTrack(long j);

    private static native RtpParameters nativeGetParameters(long j);

    private static native String nativeGetId(long j);

    private static native long nativeSetObserver(long j, Observer observer);

    private static native void nativeUnsetObserver(long j, long j2);

    private static native void nativeSetFrameDecryptor(long j, long j2);

    @CalledByNative
    public RtpReceiver(long nativeRtpReceiver) {
        this.nativeRtpReceiver = nativeRtpReceiver;
        long nativeTrack = nativeGetTrack(nativeRtpReceiver);
        this.cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);
    }

    @Nullable
    public MediaStreamTrack track() {
        return this.cachedTrack;
    }

    public RtpParameters getParameters() {
        checkRtpReceiverExists();
        return nativeGetParameters(this.nativeRtpReceiver);
    }

    /* renamed from: id */
    public String m9id() {
        checkRtpReceiverExists();
        return nativeGetId(this.nativeRtpReceiver);
    }

    @CalledByNative
    public void dispose() {
        checkRtpReceiverExists();
        this.cachedTrack.dispose();
        if (this.nativeObserver != 0) {
            nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
            this.nativeObserver = 0L;
        }
        JniCommon.nativeReleaseRef(this.nativeRtpReceiver);
        this.nativeRtpReceiver = 0L;
    }

    public void SetObserver(Observer observer) {
        checkRtpReceiverExists();
        if (this.nativeObserver != 0) {
            nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
        }
        this.nativeObserver = nativeSetObserver(this.nativeRtpReceiver, observer);
    }

    public void setFrameDecryptor(FrameDecryptor frameDecryptor) {
        checkRtpReceiverExists();
        nativeSetFrameDecryptor(this.nativeRtpReceiver, frameDecryptor.getNativeFrameDecryptor());
    }

    private void checkRtpReceiverExists() {
        if (this.nativeRtpReceiver == 0) {
            throw new IllegalStateException("RtpReceiver has been disposed.");
        }
    }
}
