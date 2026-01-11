package org.webrtc;

import java.nio.ByteBuffer;

/* loaded from: classes.jar:org/webrtc/YuvHelper.class */
public class YuvHelper {
    private static native void nativeCopyPlane(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, int i3, int i4);

    private static native void nativeI420Copy(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, ByteBuffer byteBuffer3, int i3, ByteBuffer byteBuffer4, int i4, ByteBuffer byteBuffer5, int i5, ByteBuffer byteBuffer6, int i6, int i7, int i8);

    private static native void nativeI420ToNV12(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, ByteBuffer byteBuffer3, int i3, ByteBuffer byteBuffer4, int i4, ByteBuffer byteBuffer5, int i5, int i6, int i7);

    private static native void nativeI420Rotate(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, ByteBuffer byteBuffer3, int i3, ByteBuffer byteBuffer4, int i4, ByteBuffer byteBuffer5, int i5, ByteBuffer byteBuffer6, int i6, int i7, int i8, int i9);

    private static native void nativeABGRToI420(ByteBuffer byteBuffer, int i, ByteBuffer byteBuffer2, int i2, ByteBuffer byteBuffer3, int i3, ByteBuffer byteBuffer4, int i4, int i5, int i6);

    public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int width, int height) {
        int chromaHeight = (height + 1) / 2;
        int chromaWidth = (width + 1) / 2;
        int minSize = (width * height) + (chromaWidth * chromaHeight * 2);
        if (dst.capacity() < minSize) {
            throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + minSize + " was " + dst.capacity());
        }
        int startU = height * width;
        int startV = startU + (chromaHeight * chromaWidth);
        dst.position(0);
        ByteBuffer dstY = dst.slice();
        dst.position(startU);
        ByteBuffer dstU = dst.slice();
        dst.position(startV);
        ByteBuffer dstV = dst.slice();
        nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, width, dstU, chromaWidth, dstV, chromaWidth, width, height);
    }

    public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int width, int height) {
        int chromaWidth = (width + 1) / 2;
        int chromaHeight = (height + 1) / 2;
        int minSize = (width * height) + (chromaWidth * chromaHeight * 2);
        if (dst.capacity() < minSize) {
            throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + minSize + " was " + dst.capacity());
        }
        int startUV = height * width;
        dst.position(0);
        ByteBuffer dstY = dst.slice();
        dst.position(startUV);
        ByteBuffer dstUV = dst.slice();
        nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, width, dstUV, chromaWidth * 2, width, height);
    }

    public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int srcWidth, int srcHeight, int rotationMode) {
        int dstWidth = rotationMode % 180 == 0 ? srcWidth : srcHeight;
        int dstHeight = rotationMode % 180 == 0 ? srcHeight : srcWidth;
        int dstChromaHeight = (dstHeight + 1) / 2;
        int dstChromaWidth = (dstWidth + 1) / 2;
        int minSize = (dstWidth * dstHeight) + (dstChromaWidth * dstChromaHeight * 2);
        if (dst.capacity() < minSize) {
            throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + minSize + " was " + dst.capacity());
        }
        int startU = dstHeight * dstWidth;
        int startV = startU + (dstChromaHeight * dstChromaWidth);
        dst.position(0);
        ByteBuffer dstY = dst.slice();
        dst.position(startU);
        ByteBuffer dstU = dst.slice();
        dst.position(startV);
        ByteBuffer dstV = dst.slice();
        nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstWidth, dstU, dstChromaWidth, dstV, dstChromaWidth, srcWidth, srcHeight, rotationMode);
    }

    public static void copyPlane(ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
        nativeCopyPlane(src, srcStride, dst, dstStride, width, height);
    }

    public static void ABGRToI420(ByteBuffer src, int srcStride, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
        nativeABGRToI420(src, srcStride, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, width, height);
    }

    public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
        nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, width, height);
    }

    public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstUV, int dstStrideUV, int width, int height) {
        nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV, dstStrideUV, width, height);
    }

    public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int srcWidth, int srcHeight, int rotationMode) {
        nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, srcWidth, srcHeight, rotationMode);
    }
}
