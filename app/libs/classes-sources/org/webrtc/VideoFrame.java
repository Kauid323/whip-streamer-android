package org.webrtc;

import android.graphics.Matrix;
import java.nio.ByteBuffer;

/* loaded from: classes.jar:org/webrtc/VideoFrame.class */
public class VideoFrame implements RefCounted {
    private final Buffer buffer;
    private final int rotation;
    private final long timestampNs;

    /* loaded from: classes.jar:org/webrtc/VideoFrame$Buffer.class */
    public interface Buffer extends RefCounted {
        @CalledByNative("Buffer")
        int getWidth();

        @CalledByNative("Buffer")
        int getHeight();

        @CalledByNative("Buffer")
        I420Buffer toI420();

        @Override // org.webrtc.RefCounted
        @CalledByNative("Buffer")
        void retain();

        @Override // org.webrtc.RefCounted
        @CalledByNative("Buffer")
        void release();

        @CalledByNative("Buffer")
        Buffer cropAndScale(int i, int i2, int i3, int i4, int i5, int i6);
    }

    /* loaded from: classes.jar:org/webrtc/VideoFrame$I420Buffer.class */
    public interface I420Buffer extends Buffer {
        @CalledByNative("I420Buffer")
        ByteBuffer getDataY();

        @CalledByNative("I420Buffer")
        ByteBuffer getDataU();

        @CalledByNative("I420Buffer")
        ByteBuffer getDataV();

        @CalledByNative("I420Buffer")
        int getStrideY();

        @CalledByNative("I420Buffer")
        int getStrideU();

        @CalledByNative("I420Buffer")
        int getStrideV();
    }

    /* loaded from: classes.jar:org/webrtc/VideoFrame$TextureBuffer.class */
    public interface TextureBuffer extends Buffer {
        Type getType();

        int getTextureId();

        Matrix getTransformMatrix();

        /* loaded from: classes.jar:org/webrtc/VideoFrame$TextureBuffer$Type.class */
        public enum Type {
            OES(36197),
            RGB(3553);

            private final int glTarget;

            Type(int glTarget) {
                this.glTarget = glTarget;
            }

            public int getGlTarget() {
                return this.glTarget;
            }
        }
    }

    @CalledByNative
    public VideoFrame(Buffer buffer, int rotation, long timestampNs) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer not allowed to be null");
        }
        if (rotation % 90 != 0) {
            throw new IllegalArgumentException("rotation must be a multiple of 90");
        }
        this.buffer = buffer;
        this.rotation = rotation;
        this.timestampNs = timestampNs;
    }

    @CalledByNative
    public Buffer getBuffer() {
        return this.buffer;
    }

    @CalledByNative
    public int getRotation() {
        return this.rotation;
    }

    @CalledByNative
    public long getTimestampNs() {
        return this.timestampNs;
    }

    public int getRotatedWidth() {
        if (this.rotation % 180 == 0) {
            return this.buffer.getWidth();
        }
        return this.buffer.getHeight();
    }

    public int getRotatedHeight() {
        if (this.rotation % 180 == 0) {
            return this.buffer.getHeight();
        }
        return this.buffer.getWidth();
    }

    @Override // org.webrtc.RefCounted
    public void retain() {
        this.buffer.retain();
    }

    @Override // org.webrtc.RefCounted
    @CalledByNative
    public void release() {
        this.buffer.release();
    }
}
