package org.webrtc;

import android.support.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/NV12Buffer.class */
public class NV12Buffer implements VideoFrame.Buffer {
    private final int width;
    private final int height;
    private final int stride;
    private final int sliceHeight;
    private final ByteBuffer buffer;
    private final RefCountDelegate refCountDelegate;

    private static native void nativeCropAndScale(int i, int i2, int i3, int i4, int i5, int i6, ByteBuffer byteBuffer, int i7, int i8, int i9, int i10, ByteBuffer byteBuffer2, int i11, ByteBuffer byteBuffer3, int i12, ByteBuffer byteBuffer4, int i13);

    public NV12Buffer(int width, int height, int stride, int sliceHeight, ByteBuffer buffer, @Nullable Runnable releaseCallback) {
        this.width = width;
        this.height = height;
        this.stride = stride;
        this.sliceHeight = sliceHeight;
        this.buffer = buffer;
        this.refCountDelegate = new RefCountDelegate(releaseCallback);
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public int getWidth() {
        return this.width;
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public int getHeight() {
        return this.height;
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.I420Buffer toI420() {
        return (VideoFrame.I420Buffer) cropAndScale(0, 0, this.width, this.height, this.width, this.height);
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void retain() {
        this.refCountDelegate.retain();
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void release() {
        this.refCountDelegate.release();
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
        JavaI420Buffer newBuffer = JavaI420Buffer.allocate(scaleWidth, scaleHeight);
        nativeCropAndScale(cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight, this.buffer, this.width, this.height, this.stride, this.sliceHeight, newBuffer.getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(), newBuffer.getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV());
        return newBuffer;
    }
}
