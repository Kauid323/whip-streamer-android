package org.webrtc;

/* loaded from: classes.jar:org/webrtc/CandidatePairChangeEvent.class */
public final class CandidatePairChangeEvent {
    public final IceCandidate local;
    public final IceCandidate remote;
    public final int lastDataReceivedMs;
    public final String reason;
    public final int estimatedDisconnectedTimeMs;

    @CalledByNative
    CandidatePairChangeEvent(IceCandidate local, IceCandidate remote, int lastDataReceivedMs, String reason, int estimatedDisconnectedTimeMs) {
        this.local = local;
        this.remote = remote;
        this.lastDataReceivedMs = lastDataReceivedMs;
        this.reason = reason;
        this.estimatedDisconnectedTimeMs = estimatedDisconnectedTimeMs;
    }
}
