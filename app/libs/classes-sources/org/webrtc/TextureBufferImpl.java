package org.webrtc;

import android.graphics.Matrix;
import android.os.Handler;
import android.support.annotation.Nullable;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/TextureBufferImpl.class */
public class TextureBufferImpl implements VideoFrame.TextureBuffer {
    private final int unscaledWidth;
    private final int unscaledHeight;
    private final int width;
    private final int height;
    private final VideoFrame.TextureBuffer.Type type;

    /* renamed from: id */
    private final int f6id;
    private final Matrix transformMatrix;
    private final Handler toI420Handler;
    private final YuvConverter yuvConverter;
    private final RefCountDelegate refCountDelegate;
    private final RefCountMonitor refCountMonitor;

    /* loaded from: classes.jar:org/webrtc/TextureBufferImpl$RefCountMonitor.class */
    interface RefCountMonitor {
        void onRetain(TextureBufferImpl textureBufferImpl);

        void onRelease(TextureBufferImpl textureBufferImpl);

        void onDestroy(TextureBufferImpl textureBufferImpl);
    }

    public TextureBufferImpl(int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, @Nullable final Runnable releaseCallback) {
        this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter, new RefCountMonitor() { // from class: org.webrtc.TextureBufferImpl.1
            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRetain(TextureBufferImpl textureBuffer) {
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRelease(TextureBufferImpl textureBuffer) {
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onDestroy(TextureBufferImpl textureBuffer) {
                if (releaseCallback != null) {
                    releaseCallback.run();
                }
            }
        });
    }

    TextureBufferImpl(int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, RefCountMonitor refCountMonitor) {
        this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter, refCountMonitor);
    }

    private TextureBufferImpl(int unscaledWidth, int unscaledHeight, int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, RefCountMonitor refCountMonitor) {
        this.unscaledWidth = unscaledWidth;
        this.unscaledHeight = unscaledHeight;
        this.width = width;
        this.height = height;
        this.type = type;
        this.f6id = id;
        this.transformMatrix = transformMatrix;
        this.toI420Handler = toI420Handler;
        this.yuvConverter = yuvConverter;
        this.refCountDelegate = new RefCountDelegate(() -> {
            refCountMonitor.onDestroy(this);
        });
        this.refCountMonitor = refCountMonitor;
    }

    @Override // org.webrtc.VideoFrame.TextureBuffer
    public VideoFrame.TextureBuffer.Type getType() {
        return this.type;
    }

    @Override // org.webrtc.VideoFrame.TextureBuffer
    public int getTextureId() {
        return this.f6id;
    }

    @Override // org.webrtc.VideoFrame.TextureBuffer
    public Matrix getTransformMatrix() {
        return this.transformMatrix;
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
        return (VideoFrame.I420Buffer) ThreadUtils.invokeAtFrontUninterruptibly(this.toI420Handler, () -> {
            return this.yuvConverter.convert(this);
        });
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void retain() {
        this.refCountMonitor.onRetain(this);
        this.refCountDelegate.retain();
    }

    @Override // org.webrtc.VideoFrame.Buffer, org.webrtc.RefCounted
    public void release() {
        this.refCountMonitor.onRelease(this);
        this.refCountDelegate.release();
    }

    @Override // org.webrtc.VideoFrame.Buffer
    public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
        Matrix cropAndScaleMatrix = new Matrix();
        int cropYFromBottom = this.height - (cropY + cropHeight);
        cropAndScaleMatrix.preTranslate(cropX / this.width, cropYFromBottom / this.height);
        cropAndScaleMatrix.preScale(cropWidth / this.width, cropHeight / this.height);
        return applyTransformMatrix(cropAndScaleMatrix, Math.round((this.unscaledWidth * cropWidth) / this.width), Math.round((this.unscaledHeight * cropHeight) / this.height), scaleWidth, scaleHeight);
    }

    public int getUnscaledWidth() {
        return this.unscaledWidth;
    }

    public int getUnscaledHeight() {
        return this.unscaledHeight;
    }

    public Handler getToI420Handler() {
        return this.toI420Handler;
    }

    public YuvConverter getYuvConverter() {
        return this.yuvConverter;
    }

    public TextureBufferImpl applyTransformMatrix(Matrix transformMatrix, int newWidth, int newHeight) {
        return applyTransformMatrix(transformMatrix, newWidth, newHeight, newWidth, newHeight);
    }

    private TextureBufferImpl applyTransformMatrix(Matrix transformMatrix, int unscaledWidth, int unscaledHeight, int scaledWidth, int scaledHeight) {
        Matrix newMatrix = new Matrix(this.transformMatrix);
        newMatrix.preConcat(transformMatrix);
        retain();
        return new TextureBufferImpl(unscaledWidth, unscaledHeight, scaledWidth, scaledHeight, this.type, this.f6id, newMatrix, this.toI420Handler, this.yuvConverter, new RefCountMonitor() { // from class: org.webrtc.TextureBufferImpl.2
            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRetain(TextureBufferImpl textureBuffer) {
                TextureBufferImpl.this.refCountMonitor.onRetain(TextureBufferImpl.this);
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRelease(TextureBufferImpl textureBuffer) {
                TextureBufferImpl.this.refCountMonitor.onRelease(TextureBufferImpl.this);
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onDestroy(TextureBufferImpl textureBuffer) {
                TextureBufferImpl.this.release();
            }
        });
    }
}
