package org.webrtc;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import java.util.concurrent.Callable;
import org.webrtc.EglBase;
import org.webrtc.TextureBufferImpl;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/SurfaceTextureHelper.class */
public class SurfaceTextureHelper {
    private static final String TAG = "SurfaceTextureHelper";
    private final TextureBufferImpl.RefCountMonitor textureRefCountMonitor;
    private final Handler handler;
    private final EglBase eglBase;
    private final SurfaceTexture surfaceTexture;
    private final int oesTextureId;
    private final YuvConverter yuvConverter;

    @Nullable
    private final TimestampAligner timestampAligner;
    private final FrameRefMonitor frameRefMonitor;

    @Nullable
    private VideoSink listener;
    private boolean hasPendingTexture;
    private volatile boolean isTextureInUse;
    private boolean isQuitting;
    private int frameRotation;
    private int textureWidth;
    private int textureHeight;

    @Nullable
    private VideoSink pendingListener;
    final Runnable setListenerRunnable;

    /* loaded from: classes.jar:org/webrtc/SurfaceTextureHelper$FrameRefMonitor.class */
    public interface FrameRefMonitor {
        void onNewBuffer(VideoFrame.TextureBuffer textureBuffer);

        void onRetainBuffer(VideoFrame.TextureBuffer textureBuffer);

        void onReleaseBuffer(VideoFrame.TextureBuffer textureBuffer);

        void onDestroyBuffer(VideoFrame.TextureBuffer textureBuffer);
    }

    public static SurfaceTextureHelper create(final String threadName, final EglBase.Context sharedContext, final boolean alignTimestamps, final YuvConverter yuvConverter, final FrameRefMonitor frameRefMonitor) {
        HandlerThread thread = new HandlerThread(threadName);
        thread.start();
        final Handler handler = new Handler(thread.getLooper());
        return (SurfaceTextureHelper) ThreadUtils.invokeAtFrontUninterruptibly(handler, new Callable<SurfaceTextureHelper>() { // from class: org.webrtc.SurfaceTextureHelper.1
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // java.util.concurrent.Callable
            @Nullable
            public SurfaceTextureHelper call() {
                try {
                    return new SurfaceTextureHelper(sharedContext, handler, alignTimestamps, yuvConverter, frameRefMonitor);
                } catch (RuntimeException e) {
                    Logging.m4e(SurfaceTextureHelper.TAG, threadName + " create failure", e);
                    return null;
                }
            }
        });
    }

    public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext) {
        return create(threadName, sharedContext, false, new YuvConverter(), null);
    }

    public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext, boolean alignTimestamps) {
        return create(threadName, sharedContext, alignTimestamps, new YuvConverter(), null);
    }

    public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext, boolean alignTimestamps, YuvConverter yuvConverter) {
        return create(threadName, sharedContext, alignTimestamps, yuvConverter, null);
    }

    private SurfaceTextureHelper(EglBase.Context sharedContext, Handler handler, boolean alignTimestamps, YuvConverter yuvConverter, FrameRefMonitor frameRefMonitor) {
        this.textureRefCountMonitor = new TextureBufferImpl.RefCountMonitor() { // from class: org.webrtc.SurfaceTextureHelper.2
            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRetain(TextureBufferImpl textureBuffer) {
                if (SurfaceTextureHelper.this.frameRefMonitor != null) {
                    SurfaceTextureHelper.this.frameRefMonitor.onRetainBuffer(textureBuffer);
                }
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onRelease(TextureBufferImpl textureBuffer) {
                if (SurfaceTextureHelper.this.frameRefMonitor != null) {
                    SurfaceTextureHelper.this.frameRefMonitor.onReleaseBuffer(textureBuffer);
                }
            }

            @Override // org.webrtc.TextureBufferImpl.RefCountMonitor
            public void onDestroy(TextureBufferImpl textureBuffer) {
                SurfaceTextureHelper.this.returnTextureFrame();
                if (SurfaceTextureHelper.this.frameRefMonitor != null) {
                    SurfaceTextureHelper.this.frameRefMonitor.onDestroyBuffer(textureBuffer);
                }
            }
        };
        this.setListenerRunnable = new Runnable() { // from class: org.webrtc.SurfaceTextureHelper.3
            @Override // java.lang.Runnable
            public void run() {
                Logging.m1d(SurfaceTextureHelper.TAG, "Setting listener to " + SurfaceTextureHelper.this.pendingListener);
                SurfaceTextureHelper.this.listener = SurfaceTextureHelper.this.pendingListener;
                SurfaceTextureHelper.this.pendingListener = null;
                if (SurfaceTextureHelper.this.hasPendingTexture) {
                    SurfaceTextureHelper.this.updateTexImage();
                    SurfaceTextureHelper.this.hasPendingTexture = false;
                }
            }
        };
        if (handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("SurfaceTextureHelper must be created on the handler thread");
        }
        this.handler = handler;
        this.timestampAligner = alignTimestamps ? new TimestampAligner() : null;
        this.yuvConverter = yuvConverter;
        this.frameRefMonitor = frameRefMonitor;
        this.eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
        try {
            this.eglBase.createDummyPbufferSurface();
            this.eglBase.makeCurrent();
            this.oesTextureId = GlUtil.generateTexture(36197);
            this.surfaceTexture = new SurfaceTexture(this.oesTextureId);
            setOnFrameAvailableListener(this.surfaceTexture, st -> {
                if (this.hasPendingTexture) {
                    Logging.m1d(TAG, "A frame is already pending, dropping frame.");
                }
                this.hasPendingTexture = true;
                tryDeliverTextureFrame();
            }, handler);
        } catch (RuntimeException e) {
            this.eglBase.release();
            handler.getLooper().quit();
            throw e;
        }
    }

    @TargetApi(21)
    private static void setOnFrameAvailableListener(SurfaceTexture surfaceTexture, SurfaceTexture.OnFrameAvailableListener listener, Handler handler) {
        if (Build.VERSION.SDK_INT >= 21) {
            surfaceTexture.setOnFrameAvailableListener(listener, handler);
        } else {
            surfaceTexture.setOnFrameAvailableListener(listener);
        }
    }

    public void startListening(VideoSink listener) {
        if (this.listener != null || this.pendingListener != null) {
            throw new IllegalStateException("SurfaceTextureHelper listener has already been set.");
        }
        this.pendingListener = listener;
        this.handler.post(this.setListenerRunnable);
    }

    public void stopListening() {
        Logging.m1d(TAG, "stopListening()");
        this.handler.removeCallbacks(this.setListenerRunnable);
        ThreadUtils.invokeAtFrontUninterruptibly(this.handler, () -> {
            this.listener = null;
            this.pendingListener = null;
        });
    }

    public void setTextureSize(int textureWidth, int textureHeight) {
        if (textureWidth <= 0) {
            throw new IllegalArgumentException("Texture width must be positive, but was " + textureWidth);
        }
        if (textureHeight <= 0) {
            throw new IllegalArgumentException("Texture height must be positive, but was " + textureHeight);
        }
        this.surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight);
        this.handler.post(() -> {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            tryDeliverTextureFrame();
        });
    }

    public void forceFrame() {
        this.handler.post(() -> {
            this.hasPendingTexture = true;
            tryDeliverTextureFrame();
        });
    }

    public void setFrameRotation(int rotation) {
        this.handler.post(() -> {
            this.frameRotation = rotation;
        });
    }

    public SurfaceTexture getSurfaceTexture() {
        return this.surfaceTexture;
    }

    public Handler getHandler() {
        return this.handler;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void returnTextureFrame() {
        this.handler.post(() -> {
            this.isTextureInUse = false;
            if (this.isQuitting) {
                release();
            } else {
                tryDeliverTextureFrame();
            }
        });
    }

    public boolean isTextureInUse() {
        return this.isTextureInUse;
    }

    public void dispose() {
        Logging.m1d(TAG, "dispose()");
        ThreadUtils.invokeAtFrontUninterruptibly(this.handler, () -> {
            this.isQuitting = true;
            if (!this.isTextureInUse) {
                release();
            }
        });
    }

    @Deprecated
    public VideoFrame.I420Buffer textureToYuv(VideoFrame.TextureBuffer textureBuffer) {
        return textureBuffer.toI420();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTexImage() {
        synchronized (EglBase.lock) {
            this.surfaceTexture.updateTexImage();
        }
    }

    private void tryDeliverTextureFrame() {
        if (this.handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Wrong thread.");
        }
        if (this.isQuitting || !this.hasPendingTexture || this.isTextureInUse || this.listener == null) {
            return;
        }
        if (this.textureWidth == 0 || this.textureHeight == 0) {
            Logging.m3w(TAG, "Texture size has not been set.");
            return;
        }
        this.isTextureInUse = true;
        this.hasPendingTexture = false;
        updateTexImage();
        float[] transformMatrix = new float[16];
        this.surfaceTexture.getTransformMatrix(transformMatrix);
        long timestampNs = this.surfaceTexture.getTimestamp();
        if (this.timestampAligner != null) {
            timestampNs = this.timestampAligner.translateTimestamp(timestampNs);
        }
        VideoFrame.TextureBuffer buffer = new TextureBufferImpl(this.textureWidth, this.textureHeight, VideoFrame.TextureBuffer.Type.OES, this.oesTextureId, RendererCommon.convertMatrixToAndroidGraphicsMatrix(transformMatrix), this.handler, this.yuvConverter, this.textureRefCountMonitor);
        if (this.frameRefMonitor != null) {
            this.frameRefMonitor.onNewBuffer(buffer);
        }
        VideoFrame frame = new VideoFrame(buffer, this.frameRotation, timestampNs);
        this.listener.onFrame(frame);
        frame.release();
    }

    private void release() {
        if (this.handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Wrong thread.");
        }
        if (this.isTextureInUse || !this.isQuitting) {
            throw new IllegalStateException("Unexpected release.");
        }
        this.yuvConverter.release();
        GLES20.glDeleteTextures(1, new int[]{this.oesTextureId}, 0);
        this.surfaceTexture.release();
        this.eglBase.release();
        this.handler.getLooper().quit();
        if (this.timestampAligner != null) {
            this.timestampAligner.dispose();
        }
    }
}
