package org.webrtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpParameters;

/* loaded from: classes.jar:org/webrtc/RtpTransceiver.class */
public class RtpTransceiver {
    private long nativeRtpTransceiver;
    private RtpSender cachedSender;
    private RtpReceiver cachedReceiver;

    private static native MediaStreamTrack.MediaType nativeGetMediaType(long j);

    private static native String nativeGetMid(long j);

    private static native RtpSender nativeGetSender(long j);

    private static native RtpReceiver nativeGetReceiver(long j);

    private static native boolean nativeStopped(long j);

    private static native RtpTransceiverDirection nativeDirection(long j);

    private static native RtpTransceiverDirection nativeCurrentDirection(long j);

    private static native void nativeStopInternal(long j);

    private static native void nativeStopStandard(long j);

    private static native boolean nativeSetDirection(long j, RtpTransceiverDirection rtpTransceiverDirection);

    /* loaded from: classes.jar:org/webrtc/RtpTransceiver$RtpTransceiverDirection.class */
    public enum RtpTransceiverDirection {
        SEND_RECV(0),
        SEND_ONLY(1),
        RECV_ONLY(2),
        INACTIVE(3);

        private final int nativeIndex;

        RtpTransceiverDirection(int nativeIndex) {
            this.nativeIndex = nativeIndex;
        }

        @CalledByNative("RtpTransceiverDirection")
        int getNativeIndex() {
            return this.nativeIndex;
        }

        @CalledByNative("RtpTransceiverDirection")
        static RtpTransceiverDirection fromNativeIndex(int nativeIndex) {
            for (RtpTransceiverDirection type : values()) {
                if (type.getNativeIndex() == nativeIndex) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Uknown native RtpTransceiverDirection type" + nativeIndex);
        }
    }

    /* loaded from: classes.jar:org/webrtc/RtpTransceiver$RtpTransceiverInit.class */
    public static final class RtpTransceiverInit {
        private final RtpTransceiverDirection direction;
        private final List<String> streamIds;
        private final List<RtpParameters.Encoding> sendEncodings;

        public RtpTransceiverInit() {
            this(RtpTransceiverDirection.SEND_RECV);
        }

        public RtpTransceiverInit(RtpTransceiverDirection direction) {
            this(direction, Collections.emptyList(), Collections.emptyList());
        }

        public RtpTransceiverInit(RtpTransceiverDirection direction, List<String> streamIds) {
            this(direction, streamIds, Collections.emptyList());
        }

        public RtpTransceiverInit(RtpTransceiverDirection direction, List<String> streamIds, List<RtpParameters.Encoding> sendEncodings) {
            this.direction = direction;
            this.streamIds = new ArrayList(streamIds);
            this.sendEncodings = new ArrayList(sendEncodings);
        }

        @CalledByNative("RtpTransceiverInit")
        int getDirectionNativeIndex() {
            return this.direction.getNativeIndex();
        }

        @CalledByNative("RtpTransceiverInit")
        List<String> getStreamIds() {
            return new ArrayList(this.streamIds);
        }

        @CalledByNative("RtpTransceiverInit")
        List<RtpParameters.Encoding> getSendEncodings() {
            return new ArrayList(this.sendEncodings);
        }
    }

    @CalledByNative
    protected RtpTransceiver(long nativeRtpTransceiver) {
        this.nativeRtpTransceiver = nativeRtpTransceiver;
        this.cachedSender = nativeGetSender(nativeRtpTransceiver);
        this.cachedReceiver = nativeGetReceiver(nativeRtpTransceiver);
    }

    public MediaStreamTrack.MediaType getMediaType() {
        checkRtpTransceiverExists();
        return nativeGetMediaType(this.nativeRtpTransceiver);
    }

    public String getMid() {
        checkRtpTransceiverExists();
        return nativeGetMid(this.nativeRtpTransceiver);
    }

    public RtpSender getSender() {
        return this.cachedSender;
    }

    public RtpReceiver getReceiver() {
        return this.cachedReceiver;
    }

    public boolean isStopped() {
        checkRtpTransceiverExists();
        return nativeStopped(this.nativeRtpTransceiver);
    }

    public RtpTransceiverDirection getDirection() {
        checkRtpTransceiverExists();
        return nativeDirection(this.nativeRtpTransceiver);
    }

    public RtpTransceiverDirection getCurrentDirection() {
        checkRtpTransceiverExists();
        return nativeCurrentDirection(this.nativeRtpTransceiver);
    }

    public boolean setDirection(RtpTransceiverDirection rtpTransceiverDirection) {
        checkRtpTransceiverExists();
        return nativeSetDirection(this.nativeRtpTransceiver, rtpTransceiverDirection);
    }

    public void stop() {
        checkRtpTransceiverExists();
        nativeStopInternal(this.nativeRtpTransceiver);
    }

    public void stopInternal() {
        checkRtpTransceiverExists();
        nativeStopInternal(this.nativeRtpTransceiver);
    }

    public void stopStandard() {
        checkRtpTransceiverExists();
        nativeStopStandard(this.nativeRtpTransceiver);
    }

    @CalledByNative
    public void dispose() {
        checkRtpTransceiverExists();
        this.cachedSender.dispose();
        this.cachedReceiver.dispose();
        JniCommon.nativeReleaseRef(this.nativeRtpTransceiver);
        this.nativeRtpTransceiver = 0L;
    }

    private void checkRtpTransceiverExists() {
        if (this.nativeRtpTransceiver == 0) {
            throw new IllegalStateException("RtpTransceiver has been disposed.");
        }
    }
}
