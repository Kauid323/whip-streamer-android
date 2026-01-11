package org.webrtc;

import java.nio.ByteBuffer;

/* loaded from: classes.jar:org/webrtc/DataChannel.class */
public class DataChannel {
    private long nativeDataChannel;
    private long nativeObserver;

    /* loaded from: classes.jar:org/webrtc/DataChannel$Observer.class */
    public interface Observer {
        @CalledByNative("Observer")
        void onBufferedAmountChange(long j);

        @CalledByNative("Observer")
        void onStateChange();

        @CalledByNative("Observer")
        void onMessage(Buffer buffer);
    }

    private native long nativeRegisterObserver(Observer observer);

    private native void nativeUnregisterObserver(long j);

    private native String nativeLabel();

    private native int nativeId();

    private native State nativeState();

    private native long nativeBufferedAmount();

    private native void nativeClose();

    private native boolean nativeSend(byte[] bArr, boolean z);

    /* loaded from: classes.jar:org/webrtc/DataChannel$Init.class */
    public static class Init {
        public boolean negotiated;
        public boolean ordered = true;
        public int maxRetransmitTimeMs = -1;
        public int maxRetransmits = -1;
        public String protocol = "";

        /* renamed from: id */
        public int f0id = -1;

        @CalledByNative("Init")
        boolean getOrdered() {
            return this.ordered;
        }

        @CalledByNative("Init")
        int getMaxRetransmitTimeMs() {
            return this.maxRetransmitTimeMs;
        }

        @CalledByNative("Init")
        int getMaxRetransmits() {
            return this.maxRetransmits;
        }

        @CalledByNative("Init")
        String getProtocol() {
            return this.protocol;
        }

        @CalledByNative("Init")
        boolean getNegotiated() {
            return this.negotiated;
        }

        @CalledByNative("Init")
        int getId() {
            return this.f0id;
        }
    }

    /* loaded from: classes.jar:org/webrtc/DataChannel$Buffer.class */
    public static class Buffer {
        public final ByteBuffer data;
        public final boolean binary;

        @CalledByNative("Buffer")
        public Buffer(ByteBuffer data, boolean binary) {
            this.data = data;
            this.binary = binary;
        }
    }

    /* loaded from: classes.jar:org/webrtc/DataChannel$State.class */
    public enum State {
        CONNECTING,
        OPEN,
        CLOSING,
        CLOSED;

        @CalledByNative("State")
        static State fromNativeIndex(int nativeIndex) {
            return values()[nativeIndex];
        }
    }

    @CalledByNative
    public DataChannel(long nativeDataChannel) {
        this.nativeDataChannel = nativeDataChannel;
    }

    public void registerObserver(Observer observer) {
        checkDataChannelExists();
        if (this.nativeObserver != 0) {
            nativeUnregisterObserver(this.nativeObserver);
        }
        this.nativeObserver = nativeRegisterObserver(observer);
    }

    public void unregisterObserver() {
        checkDataChannelExists();
        nativeUnregisterObserver(this.nativeObserver);
    }

    public String label() {
        checkDataChannelExists();
        return nativeLabel();
    }

    /* renamed from: id */
    public int m0id() {
        checkDataChannelExists();
        return nativeId();
    }

    public State state() {
        checkDataChannelExists();
        return nativeState();
    }

    public long bufferedAmount() {
        checkDataChannelExists();
        return nativeBufferedAmount();
    }

    public void close() {
        checkDataChannelExists();
        nativeClose();
    }

    public boolean send(Buffer buffer) {
        checkDataChannelExists();
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        return nativeSend(data, buffer.binary);
    }

    public void dispose() {
        checkDataChannelExists();
        JniCommon.nativeReleaseRef(this.nativeDataChannel);
        this.nativeDataChannel = 0L;
    }

    @CalledByNative
    long getNativeDataChannel() {
        return this.nativeDataChannel;
    }

    private void checkDataChannelExists() {
        if (this.nativeDataChannel == 0) {
            throw new IllegalStateException("DataChannel has been disposed.");
        }
    }
}
