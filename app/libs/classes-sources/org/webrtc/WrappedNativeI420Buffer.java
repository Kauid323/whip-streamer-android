package org.webrtc;

import java.nio.ByteBuffer;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/WrappedNativeI420Buffer.class */
class WrappedNativeI420Buffer implements VideoFrame.I420Buffer {
    private final int width;
    private final int height;
    private final ByteBuffer dataY;
    private final int strideY;
    private final ByteBuffer dataU;
    private final int strideU;
    private final ByteBuffer dataV;
    private final int strideV;
    private final long nativeBuffer;

    @CalledByNative
    WrappedNativeI420Buffer(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV, long nativeBuffer) {
        this.width = width;
        this.height = height;
        this.dataY = dataY;
        this.strideY = strideY;
        this.dataU = dataU;
        this.strideU = strideU;
        this.dataV = dataV;
        this.strideV = strideV;
        this.nativeBuffer = nativeBuffer;
        retain();
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public int getWidth() {
        return this.width;
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public int getHeight() {
        return this.height;
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public ByteBuffer getDataY() {
        return this.dataY.slice();
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public ByteBuffer getDataU() {
        return this.dataU.slice();
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public ByteBuffer getDataV() {
        return this.dataV.slice();
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public int getStrideY() {
        return this.strideY;
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public int getStrideU() {
        return this.strideU;
    }

    @Override // org.webrtc.VideoFrame.I420Buffer
    public int getStrideV() {
        return this.strideV;
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.I420Buffer toI420() {
        retain();
        return this;
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void retain() {
        JniCommon.nativeAddRef(this.nativeBuffer);
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void release() {
        JniCommon.nativeReleaseRef(this.nativeBuffer);
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
        return JavaI420Buffer.cropAndScaleI420(this, cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight);
    }
}
