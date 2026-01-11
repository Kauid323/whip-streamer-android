package org.webrtc;

import java.util.IdentityHashMap;
import java.util.Iterator;

/* loaded from: classes.jar:org/webrtc/VideoTrack.class */
public class VideoTrack extends MediaStreamTrack {
    private final IdentityHashMap<VideoSink, Long> sinks;

    private static native void nativeAddSink(long j, long j2);

    private static native void nativeRemoveSink(long j, long j2);

    private static native long nativeWrapSink(VideoSink videoSink);

    private static native void nativeFreeSink(long j);

    public VideoTrack(long nativeTrack) {
        super(nativeTrack);
        this.sinks = new IdentityHashMap<>();
    }

    public void addSink(VideoSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("The VideoSink is not allowed to be null");
        }
        if (!this.sinks.containsKey(sink)) {
            long nativeSink = nativeWrapSink(sink);
            this.sinks.put(sink, Long.valueOf(nativeSink));
            nativeAddSink(getNativeMediaStreamTrack(), nativeSink);
        }
    }

    public void removeSink(VideoSink sink) {
        Long nativeSink = this.sinks.remove(sink);
        if (nativeSink != null) {
            nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink.longValue());
            nativeFreeSink(nativeSink.longValue());
        }
    }

    @Override // org.webrtc.MediaStreamTrack
    public void dispose() {
        Iterator<Long> it = this.sinks.values().iterator();
        while (it.hasNext()) {
            long nativeSink = it.next().longValue();
            nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink);
            nativeFreeSink(nativeSink);
        }
        this.sinks.clear();
        super.dispose();
    }

    long getNativeVideoTrack() {
        return getNativeMediaStreamTrack();
    }
}
