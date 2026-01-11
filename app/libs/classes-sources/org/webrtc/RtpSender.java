package org.webrtc;

import android.support.annotation.Nullable;
import java.util.List;

/* loaded from: classes.jar:org/webrtc/RtpSender.class */
public class RtpSender {
    private long nativeRtpSender;

    @Nullable
    private MediaStreamTrack cachedTrack;
    private boolean ownsTrack = true;

    @Nullable
    private final DtmfSender dtmfSender;

    private static native boolean nativeSetTrack(long j, long j2);

    private static native long nativeGetTrack(long j);

    private static native void nativeSetStreams(long j, List<String> list);

    private static native List<String> nativeGetStreams(long j);

    private static native long nativeGetDtmfSender(long j);

    private static native boolean nativeSetParameters(long j, RtpParameters rtpParameters);

    private static native RtpParameters nativeGetParameters(long j);

    private static native String nativeGetId(long j);

    private static native void nativeSetFrameEncryptor(long j, long j2);

    @CalledByNative
    public RtpSender(long nativeRtpSender) {
        this.nativeRtpSender = nativeRtpSender;
        long nativeTrack = nativeGetTrack(nativeRtpSender);
        this.cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);
        long nativeDtmfSender = nativeGetDtmfSender(nativeRtpSender);
        this.dtmfSender = nativeDtmfSender != 0 ? new DtmfSender(nativeDtmfSender) : null;
    }

    public boolean setTrack(@Nullable MediaStreamTrack track, boolean takeOwnership) {
        checkRtpSenderExists();
        if (!nativeSetTrack(this.nativeRtpSender, track == null ? 0L : track.getNativeMediaStreamTrack())) {
            return false;
        }
        if (this.cachedTrack != null && this.ownsTrack) {
            this.cachedTrack.dispose();
        }
        this.cachedTrack = track;
        this.ownsTrack = takeOwnership;
        return true;
    }

    @Nullable
    public MediaStreamTrack track() {
        return this.cachedTrack;
    }

    public void setStreams(List<String> streamIds) {
        checkRtpSenderExists();
        nativeSetStreams(this.nativeRtpSender, streamIds);
    }

    public List<String> getStreams() {
        checkRtpSenderExists();
        return nativeGetStreams(this.nativeRtpSender);
    }

    public boolean setParameters(RtpParameters parameters) {
        checkRtpSenderExists();
        return nativeSetParameters(this.nativeRtpSender, parameters);
    }

    public RtpParameters getParameters() {
        checkRtpSenderExists();
        return nativeGetParameters(this.nativeRtpSender);
    }

    /* renamed from: id */
    public String m10id() {
        checkRtpSenderExists();
        return nativeGetId(this.nativeRtpSender);
    }

    @Nullable
    public DtmfSender dtmf() {
        return this.dtmfSender;
    }

    public void setFrameEncryptor(FrameEncryptor frameEncryptor) {
        checkRtpSenderExists();
        nativeSetFrameEncryptor(this.nativeRtpSender, frameEncryptor.getNativeFrameEncryptor());
    }

    public void dispose() {
        checkRtpSenderExists();
        if (this.dtmfSender != null) {
            this.dtmfSender.dispose();
        }
        if (this.cachedTrack != null && this.ownsTrack) {
            this.cachedTrack.dispose();
        }
        JniCommon.nativeReleaseRef(this.nativeRtpSender);
        this.nativeRtpSender = 0L;
    }

    long getNativeRtpSender() {
        checkRtpSenderExists();
        return this.nativeRtpSender;
    }

    private void checkRtpSenderExists() {
        if (this.nativeRtpSender == 0) {
            throw new IllegalStateException("RtpSender has been disposed.");
        }
    }
}
