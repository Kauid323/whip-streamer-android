package org.webrtc;

import android.support.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/JavaI420Buffer.class */
public class JavaI420Buffer implements VideoFrame.I420Buffer {
    private final int width;
    private final int height;
    private final ByteBuffer dataY;
    private final ByteBuffer dataU;
    private final ByteBuffer dataV;
    private final int strideY;
    private final int strideU;
    private final int strideV;
    private final RefCountDelegate refCountDelegate;

    private static native void nativeCropAndScaleI420(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, ByteBuffer byteBuffer3, int i3, int i4, int i5, int i6, int i7, ByteBuffer byteBuffer4, int i8, ByteBuffer byteBuffer5, int i9, ByteBuffer byteBuffer6, int i10, int i11, int i12);

    private JavaI420Buffer(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV, @Nullable Runnable releaseCallback) {
        this.width = width;
        this.height = height;
        this.dataY = dataY;
        this.dataU = dataU;
        this.dataV = dataV;
        this.strideY = strideY;
        this.strideU = strideU;
        this.strideV = strideV;
        this.refCountDelegate = new RefCountDelegate(releaseCallback);
    }

    private static void checkCapacity(ByteBuffer data, int width, int height, int stride) {
        int minCapacity = (stride * (height - 1)) + width;
        if (data.capacity() < minCapacity) {
            throw new IllegalArgumentException("Buffer must be at least " + minCapacity + " bytes, but was " + data.capacity());
        }
    }

    public static JavaI420Buffer wrap(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV, @Nullable Runnable releaseCallback) {
        if (dataY == null || dataU == null || dataV == null) {
            throw new IllegalArgumentException("Data buffers cannot be null.");
        }
        if (!dataY.isDirect() || !dataU.isDirect() || !dataV.isDirect()) {
            throw new IllegalArgumentException("Data buffers must be direct byte buffers.");
        }
        ByteBuffer dataY2 = dataY.slice();
        ByteBuffer dataU2 = dataU.slice();
        ByteBuffer dataV2 = dataV.slice();
        int chromaWidth = (width + 1) / 2;
        int chromaHeight = (height + 1) / 2;
        checkCapacity(dataY2, width, height, strideY);
        checkCapacity(dataU2, chromaWidth, chromaHeight, strideU);
        checkCapacity(dataV2, chromaWidth, chromaHeight, strideV);
        return new JavaI420Buffer(width, height, dataY2, strideY, dataU2, strideU, dataV2, strideV, releaseCallback);
    }

    public static JavaI420Buffer allocate(int width, int height) {
        int chromaHeight = (height + 1) / 2;
        int strideUV = (width + 1) / 2;
        int uPos = 0 + (width * height);
        int vPos = uPos + (strideUV * chromaHeight);
        ByteBuffer buffer = JniCommon.nativeAllocateByteBuffer((width * height) + (2 * strideUV * chromaHeight));
        buffer.position(0);
        buffer.limit(uPos);
        ByteBuffer dataY = buffer.slice();
        buffer.position(uPos);
        buffer.limit(vPos);
        ByteBuffer dataU = buffer.slice();
        buffer.position(vPos);
        buffer.limit(vPos + (strideUV * chromaHeight));
        ByteBuffer dataV = buffer.slice();
        return new JavaI420Buffer(width, height, dataY, width, dataU, strideUV, dataV, strideUV, () -> {
            JniCommon.nativeFreeByteBuffer(buffer);
        });
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
        this.refCountDelegate.retain();
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void release() {
        this.refCountDelegate.release();
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
        return cropAndScaleI420(this, cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight);
    }

    public static VideoFrame.Buffer cropAndScaleI420(VideoFrame.I420Buffer buffer, int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
        if (cropWidth == scaleWidth && cropHeight == scaleHeight) {
            ByteBuffer dataY = buffer.getDataY();
            ByteBuffer dataU = buffer.getDataU();
            ByteBuffer dataV = buffer.getDataV();
            dataY.position(cropX + (cropY * buffer.getStrideY()));
            dataU.position((cropX / 2) + ((cropY / 2) * buffer.getStrideU()));
            dataV.position((cropX / 2) + ((cropY / 2) * buffer.getStrideV()));
            buffer.retain();
            ByteBuffer byteBufferSlice = dataY.slice();
            int strideY = buffer.getStrideY();
            ByteBuffer byteBufferSlice2 = dataU.slice();
            int strideU = buffer.getStrideU();
            ByteBuffer byteBufferSlice3 = dataV.slice();
            int strideV = buffer.getStrideV();
            Objects.requireNonNull(buffer);
            return wrap(scaleWidth, scaleHeight, byteBufferSlice, strideY, byteBufferSlice2, strideU, byteBufferSlice3, strideV, buffer::release);
        }
        JavaI420Buffer newBuffer = allocate(scaleWidth, scaleHeight);
        nativeCropAndScaleI420(buffer.getDataY(), buffer.getStrideY(), buffer.getDataU(), buffer.getStrideU(), buffer.getDataV(), buffer.getStrideV(), cropX, cropY, cropWidth, cropHeight, newBuffer.getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(), newBuffer.getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV(), scaleWidth, scaleHeight);
        return newBuffer;
    }
}
