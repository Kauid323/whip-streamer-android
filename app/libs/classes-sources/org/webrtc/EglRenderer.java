package org.webrtc;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase;
import org.webrtc.GlUtil;
import org.webrtc.RendererCommon;

/* loaded from: classes.jar:org/webrtc/EglRenderer.class */
public class EglRenderer implements VideoSink {
    private static final String TAG = "EglRenderer";
    private static final long LOG_INTERVAL_SEC = 4;
    protected final String name;
    private final Object handlerLock;

    @Nullable
    private Handler renderThreadHandler;
    private final ArrayList<FrameListenerAndParams> frameListeners;
    private volatile ErrorCallback errorCallback;
    private final Object fpsReductionLock;
    private long nextFrameTimeNs;
    private long minRenderPeriodNs;

    @Nullable
    private EglBase eglBase;
    private final VideoFrameDrawer frameDrawer;

    @Nullable
    private RendererCommon.GlDrawer drawer;
    private boolean usePresentationTimeStamp;
    private final Matrix drawMatrix;
    private final Object frameLock;

    @Nullable
    private VideoFrame pendingFrame;
    private final Object layoutLock;
    private float layoutAspectRatio;
    private boolean mirrorHorizontally;
    private boolean mirrorVertically;
    private final Object statisticsLock;
    private int framesReceived;
    private int framesDropped;
    private int framesRendered;
    private long statisticsStartTimeNs;
    private long renderTimeNs;
    private long renderSwapBufferTimeNs;
    private final GlTextureFrameBuffer bitmapTextureFramebuffer;
    private final Runnable logStatisticsRunnable;
    private final EglSurfaceCreation eglSurfaceCreationRunnable;

    /* loaded from: classes.jar:org/webrtc/EglRenderer$ErrorCallback.class */
    public interface ErrorCallback {
        void onGlOutOfMemory();
    }

    /* loaded from: classes.jar:org/webrtc/EglRenderer$FrameListener.class */
    public interface FrameListener {
        void onFrame(Bitmap bitmap);
    }

    /* loaded from: classes.jar:org/webrtc/EglRenderer$FrameListenerAndParams.class */
    private static class FrameListenerAndParams {
        public final FrameListener listener;
        public final float scale;
        public final RendererCommon.GlDrawer drawer;
        public final boolean applyFpsReduction;

        public FrameListenerAndParams(FrameListener listener, float scale, RendererCommon.GlDrawer drawer, boolean applyFpsReduction) {
            this.listener = listener;
            this.scale = scale;
            this.drawer = drawer;
            this.applyFpsReduction = applyFpsReduction;
        }
    }

    /* loaded from: classes.jar:org/webrtc/EglRenderer$EglSurfaceCreation.class */
    private class EglSurfaceCreation implements Runnable {
        private Object surface;

        private EglSurfaceCreation() {
        }

        public synchronized void setSurface(Object surface) {
            this.surface = surface;
        }

        @Override // java.lang.Runnable
        public synchronized void run() {
            if (this.surface != null && EglRenderer.this.eglBase != null && !EglRenderer.this.eglBase.hasSurface()) {
                if (this.surface instanceof Surface) {
                    EglRenderer.this.eglBase.createSurface((Surface) this.surface);
                } else if (this.surface instanceof SurfaceTexture) {
                    EglRenderer.this.eglBase.createSurface((SurfaceTexture) this.surface);
                } else {
                    throw new IllegalStateException("Invalid surface: " + this.surface);
                }
                EglRenderer.this.eglBase.makeCurrent();
                GLES20.glPixelStorei(3317, 1);
            }
        }
    }

    /* loaded from: classes.jar:org/webrtc/EglRenderer$HandlerWithExceptionCallback.class */
    private static class HandlerWithExceptionCallback extends Handler {
        private final Runnable exceptionCallback;

        public HandlerWithExceptionCallback(Looper looper, Runnable exceptionCallback) {
            super(looper);
            this.exceptionCallback = exceptionCallback;
        }

        @Override // android.os.Handler
        public void dispatchMessage(Message msg) throws Exception {
            try {
                super.dispatchMessage(msg);
            } catch (Exception e) {
                Logging.m4e(EglRenderer.TAG, "Exception on EglRenderer thread", e);
                this.exceptionCallback.run();
                throw e;
            }
        }
    }

    public EglRenderer(String name) {
        this(name, new VideoFrameDrawer());
    }

    public EglRenderer(String name, VideoFrameDrawer videoFrameDrawer) {
        this.handlerLock = new Object();
        this.frameListeners = new ArrayList<>();
        this.fpsReductionLock = new Object();
        this.drawMatrix = new Matrix();
        this.frameLock = new Object();
        this.layoutLock = new Object();
        this.statisticsLock = new Object();
        this.bitmapTextureFramebuffer = new GlTextureFrameBuffer(6408);
        this.logStatisticsRunnable = new Runnable() { // from class: org.webrtc.EglRenderer.1
            @Override // java.lang.Runnable
            public void run() {
                EglRenderer.this.logStatistics();
                synchronized (EglRenderer.this.handlerLock) {
                    if (EglRenderer.this.renderThreadHandler != null) {
                        EglRenderer.this.renderThreadHandler.removeCallbacks(EglRenderer.this.logStatisticsRunnable);
                        EglRenderer.this.renderThreadHandler.postDelayed(EglRenderer.this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(EglRenderer.LOG_INTERVAL_SEC));
                    }
                }
            }
        };
        this.eglSurfaceCreationRunnable = new EglSurfaceCreation();
        this.name = name;
        this.frameDrawer = videoFrameDrawer;
    }

    public void init(@Nullable EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer, boolean usePresentationTimeStamp) {
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler != null) {
                throw new IllegalStateException(this.name + "Already initialized");
            }
            logD("Initializing EglRenderer");
            this.drawer = drawer;
            this.usePresentationTimeStamp = usePresentationTimeStamp;
            HandlerThread renderThread = new HandlerThread(this.name + TAG);
            renderThread.start();
            this.renderThreadHandler = new HandlerWithExceptionCallback(renderThread.getLooper(), new Runnable() { // from class: org.webrtc.EglRenderer.2
                @Override // java.lang.Runnable
                public void run() {
                    synchronized (EglRenderer.this.handlerLock) {
                        EglRenderer.this.renderThreadHandler = null;
                    }
                }
            });
            ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, () -> {
                if (sharedContext == null) {
                    logD("EglBase10.create context");
                    this.eglBase = EglBase.createEgl10(configAttributes);
                } else {
                    logD("EglBase.create shared context");
                    this.eglBase = EglBase.create(sharedContext, configAttributes);
                }
            });
            this.renderThreadHandler.post(this.eglSurfaceCreationRunnable);
            long currentTimeNs = System.nanoTime();
            resetStatistics(currentTimeNs);
            this.renderThreadHandler.postDelayed(this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(LOG_INTERVAL_SEC));
        }
    }

    public void init(@Nullable EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        init(sharedContext, configAttributes, drawer, false);
    }

    public void createEglSurface(Surface surface) {
        createEglSurfaceInternal(surface);
    }

    public void createEglSurface(SurfaceTexture surfaceTexture) {
        createEglSurfaceInternal(surfaceTexture);
    }

    private void createEglSurfaceInternal(Object surface) {
        this.eglSurfaceCreationRunnable.setSurface(surface);
        postToRenderThread(this.eglSurfaceCreationRunnable);
    }

    public void release() {
        logD("Releasing.");
        CountDownLatch eglCleanupBarrier = new CountDownLatch(1);
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler == null) {
                logD("Already released");
                return;
            }
            this.renderThreadHandler.removeCallbacks(this.logStatisticsRunnable);
            this.renderThreadHandler.postAtFrontOfQueue(() -> {
                synchronized (EglBase.lock) {
                    GLES20.glUseProgram(0);
                }
                if (this.drawer != null) {
                    this.drawer.release();
                    this.drawer = null;
                }
                this.frameDrawer.release();
                this.bitmapTextureFramebuffer.release();
                if (this.eglBase != null) {
                    logD("eglBase detach and release.");
                    this.eglBase.detachCurrent();
                    this.eglBase.release();
                    this.eglBase = null;
                }
                this.frameListeners.clear();
                eglCleanupBarrier.countDown();
            });
            Looper renderLooper = this.renderThreadHandler.getLooper();
            this.renderThreadHandler.post(() -> {
                logD("Quitting render thread.");
                renderLooper.quit();
            });
            this.renderThreadHandler = null;
            ThreadUtils.awaitUninterruptibly(eglCleanupBarrier);
            synchronized (this.frameLock) {
                if (this.pendingFrame != null) {
                    this.pendingFrame.release();
                    this.pendingFrame = null;
                }
            }
            logD("Releasing done.");
        }
    }

    private void resetStatistics(long currentTimeNs) {
        synchronized (this.statisticsLock) {
            this.statisticsStartTimeNs = currentTimeNs;
            this.framesReceived = 0;
            this.framesDropped = 0;
            this.framesRendered = 0;
            this.renderTimeNs = 0L;
            this.renderSwapBufferTimeNs = 0L;
        }
    }

    public void printStackTrace() {
        synchronized (this.handlerLock) {
            Thread renderThread = this.renderThreadHandler == null ? null : this.renderThreadHandler.getLooper().getThread();
            if (renderThread != null) {
                StackTraceElement[] renderStackTrace = renderThread.getStackTrace();
                if (renderStackTrace.length > 0) {
                    logW("EglRenderer stack trace:");
                    for (StackTraceElement traceElem : renderStackTrace) {
                        logW(traceElem.toString());
                    }
                }
            }
        }
    }

    public void setMirror(boolean mirror) {
        logD("setMirrorHorizontally: " + mirror);
        synchronized (this.layoutLock) {
            this.mirrorHorizontally = mirror;
        }
    }

    public void setMirrorVertically(boolean mirrorVertically) {
        logD("setMirrorVertically: " + mirrorVertically);
        synchronized (this.layoutLock) {
            this.mirrorVertically = mirrorVertically;
        }
    }

    public void setLayoutAspectRatio(float layoutAspectRatio) {
        logD("setLayoutAspectRatio: " + layoutAspectRatio);
        synchronized (this.layoutLock) {
            this.layoutAspectRatio = layoutAspectRatio;
        }
    }

    public void setFpsReduction(float fps) {
        logD("setFpsReduction: " + fps);
        synchronized (this.fpsReductionLock) {
            long previousRenderPeriodNs = this.minRenderPeriodNs;
            if (fps <= 0.0f) {
                this.minRenderPeriodNs = Long.MAX_VALUE;
            } else {
                this.minRenderPeriodNs = (long) (TimeUnit.SECONDS.toNanos(1L) / fps);
            }
            if (this.minRenderPeriodNs != previousRenderPeriodNs) {
                this.nextFrameTimeNs = System.nanoTime();
            }
        }
    }

    public void disableFpsReduction() {
        setFpsReduction(Float.POSITIVE_INFINITY);
    }

    public void pauseVideo() {
        setFpsReduction(0.0f);
    }

    public void addFrameListener(FrameListener listener, float scale) {
        addFrameListener(listener, scale, null, false);
    }

    public void addFrameListener(FrameListener listener, float scale, RendererCommon.GlDrawer drawerParam) {
        addFrameListener(listener, scale, drawerParam, false);
    }

    public void addFrameListener(FrameListener listener, float scale, @Nullable RendererCommon.GlDrawer drawerParam, boolean applyFpsReduction) {
        postToRenderThread(() -> {
            RendererCommon.GlDrawer listenerDrawer = drawerParam == null ? this.drawer : drawerParam;
            this.frameListeners.add(new FrameListenerAndParams(listener, scale, listenerDrawer, applyFpsReduction));
        });
    }

    public void removeFrameListener(FrameListener listener) {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler == null) {
                return;
            }
            if (Thread.currentThread() == this.renderThreadHandler.getLooper().getThread()) {
                throw new RuntimeException("removeFrameListener must not be called on the render thread.");
            }
            postToRenderThread(() -> {
                latch.countDown();
                Iterator<FrameListenerAndParams> iter = this.frameListeners.iterator();
                while (iter.hasNext()) {
                    if (iter.next().listener == listener) {
                        iter.remove();
                    }
                }
            });
            ThreadUtils.awaitUninterruptibly(latch);
        }
    }

    public void setErrorCallback(ErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    @Override // org.webrtc.VideoSink
    public void onFrame(VideoFrame frame) {
        boolean dropOldFrame;
        synchronized (this.statisticsLock) {
            this.framesReceived++;
        }
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler == null) {
                logD("Dropping frame - Not initialized or already released.");
                return;
            }
            synchronized (this.frameLock) {
                dropOldFrame = this.pendingFrame != null;
                if (dropOldFrame) {
                    this.pendingFrame.release();
                }
                this.pendingFrame = frame;
                this.pendingFrame.retain();
                this.renderThreadHandler.post(this::renderFrameOnRenderThread);
            }
            if (dropOldFrame) {
                synchronized (this.statisticsLock) {
                    this.framesDropped++;
                }
            }
        }
    }

    public void releaseEglSurface(Runnable completionCallback) {
        this.eglSurfaceCreationRunnable.setSurface(null);
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler != null) {
                this.renderThreadHandler.removeCallbacks(this.eglSurfaceCreationRunnable);
                this.renderThreadHandler.postAtFrontOfQueue(() -> {
                    if (this.eglBase != null) {
                        this.eglBase.detachCurrent();
                        this.eglBase.releaseSurface();
                    }
                    completionCallback.run();
                });
            } else {
                completionCallback.run();
            }
        }
    }

    private void postToRenderThread(Runnable runnable) {
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler != null) {
                this.renderThreadHandler.post(runnable);
            }
        }
    }

    private void clearSurfaceOnRenderThread(float r, float g, float b, float a) {
        if (this.eglBase != null && this.eglBase.hasSurface()) {
            logD("clearSurface");
            GLES20.glClearColor(r, g, b, a);
            GLES20.glClear(16384);
            this.eglBase.swapBuffers();
        }
    }

    public void clearImage() {
        clearImage(0.0f, 0.0f, 0.0f, 0.0f);
    }

    public void clearImage(float r, float g, float b, float a) {
        synchronized (this.handlerLock) {
            if (this.renderThreadHandler == null) {
                return;
            }
            this.renderThreadHandler.postAtFrontOfQueue(() -> {
                clearSurfaceOnRenderThread(r, g, b, a);
            });
        }
    }

    private void renderFrameOnRenderThread() {
        boolean shouldRenderFrame;
        float drawnAspectRatio;
        float scaleX;
        float scaleY;
        synchronized (this.frameLock) {
            if (this.pendingFrame == null) {
                return;
            }
            VideoFrame frame = this.pendingFrame;
            this.pendingFrame = null;
            if (this.eglBase == null || !this.eglBase.hasSurface()) {
                logD("Dropping frame - No surface");
                frame.release();
                return;
            }
            synchronized (this.fpsReductionLock) {
                if (this.minRenderPeriodNs == Long.MAX_VALUE) {
                    shouldRenderFrame = false;
                } else if (this.minRenderPeriodNs <= 0) {
                    shouldRenderFrame = true;
                } else {
                    long currentTimeNs = System.nanoTime();
                    if (currentTimeNs < this.nextFrameTimeNs) {
                        logD("Skipping frame rendering - fps reduction is active.");
                        shouldRenderFrame = false;
                    } else {
                        this.nextFrameTimeNs += this.minRenderPeriodNs;
                        this.nextFrameTimeNs = Math.max(this.nextFrameTimeNs, currentTimeNs);
                        shouldRenderFrame = true;
                    }
                }
            }
            long startTimeNs = System.nanoTime();
            float frameAspectRatio = frame.getRotatedWidth() / frame.getRotatedHeight();
            synchronized (this.layoutLock) {
                drawnAspectRatio = this.layoutAspectRatio != 0.0f ? this.layoutAspectRatio : frameAspectRatio;
            }
            if (frameAspectRatio > drawnAspectRatio) {
                scaleX = drawnAspectRatio / frameAspectRatio;
                scaleY = 1.0f;
            } else {
                scaleX = 1.0f;
                scaleY = frameAspectRatio / drawnAspectRatio;
            }
            this.drawMatrix.reset();
            this.drawMatrix.preTranslate(0.5f, 0.5f);
            this.drawMatrix.preScale(this.mirrorHorizontally ? -1.0f : 1.0f, this.mirrorVertically ? -1.0f : 1.0f);
            this.drawMatrix.preScale(scaleX, scaleY);
            this.drawMatrix.preTranslate(-0.5f, -0.5f);
            try {
                if (shouldRenderFrame) {
                    try {
                        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                        GLES20.glClear(16384);
                        this.frameDrawer.drawFrame(frame, this.drawer, this.drawMatrix, 0, 0, this.eglBase.surfaceWidth(), this.eglBase.surfaceHeight());
                        long swapBuffersStartTimeNs = System.nanoTime();
                        if (this.usePresentationTimeStamp) {
                            this.eglBase.swapBuffers(frame.getTimestampNs());
                        } else {
                            this.eglBase.swapBuffers();
                        }
                        long currentTimeNs2 = System.nanoTime();
                        synchronized (this.statisticsLock) {
                            this.framesRendered++;
                            this.renderTimeNs += currentTimeNs2 - startTimeNs;
                            this.renderSwapBufferTimeNs += currentTimeNs2 - swapBuffersStartTimeNs;
                        }
                    } catch (GlUtil.GlOutOfMemoryException e) {
                        logE("Error while drawing frame", e);
                        ErrorCallback errorCallback = this.errorCallback;
                        if (errorCallback != null) {
                            errorCallback.onGlOutOfMemory();
                        }
                        this.drawer.release();
                        this.frameDrawer.release();
                        this.bitmapTextureFramebuffer.release();
                        frame.release();
                        return;
                    }
                }
                notifyCallbacks(frame, shouldRenderFrame);
                frame.release();
            } catch (Throwable th) {
                frame.release();
                throw th;
            }
        }
    }

    private void notifyCallbacks(VideoFrame frame, boolean wasRendered) {
        if (this.frameListeners.isEmpty()) {
            return;
        }
        this.drawMatrix.reset();
        this.drawMatrix.preTranslate(0.5f, 0.5f);
        this.drawMatrix.preScale(this.mirrorHorizontally ? -1.0f : 1.0f, this.mirrorVertically ? -1.0f : 1.0f);
        this.drawMatrix.preScale(1.0f, -1.0f);
        this.drawMatrix.preTranslate(-0.5f, -0.5f);
        Iterator<FrameListenerAndParams> it = this.frameListeners.iterator();
        while (it.hasNext()) {
            FrameListenerAndParams listenerAndParams = it.next();
            if (wasRendered || !listenerAndParams.applyFpsReduction) {
                it.remove();
                int scaledWidth = (int) (listenerAndParams.scale * frame.getRotatedWidth());
                int scaledHeight = (int) (listenerAndParams.scale * frame.getRotatedHeight());
                if (scaledWidth == 0 || scaledHeight == 0) {
                    listenerAndParams.listener.onFrame(null);
                } else {
                    this.bitmapTextureFramebuffer.setSize(scaledWidth, scaledHeight);
                    GLES20.glBindFramebuffer(36160, this.bitmapTextureFramebuffer.getFrameBufferId());
                    GLES20.glFramebufferTexture2D(36160, 36064, 3553, this.bitmapTextureFramebuffer.getTextureId(), 0);
                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    GLES20.glClear(16384);
                    this.frameDrawer.drawFrame(frame, listenerAndParams.drawer, this.drawMatrix, 0, 0, scaledWidth, scaledHeight);
                    ByteBuffer bitmapBuffer = ByteBuffer.allocateDirect(scaledWidth * scaledHeight * 4);
                    GLES20.glViewport(0, 0, scaledWidth, scaledHeight);
                    GLES20.glReadPixels(0, 0, scaledWidth, scaledHeight, 6408, 5121, bitmapBuffer);
                    GLES20.glBindFramebuffer(36160, 0);
                    GlUtil.checkNoGLES2Error("EglRenderer.notifyCallbacks");
                    Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(bitmapBuffer);
                    listenerAndParams.listener.onFrame(bitmap);
                }
            }
        }
    }

    private String averageTimeAsString(long sumTimeNs, int count) {
        return count <= 0 ? "NA" : TimeUnit.NANOSECONDS.toMicros(sumTimeNs / count) + " us";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logStatistics() {
        DecimalFormat fpsFormat = new DecimalFormat("#.0");
        long currentTimeNs = System.nanoTime();
        synchronized (this.statisticsLock) {
            long elapsedTimeNs = currentTimeNs - this.statisticsStartTimeNs;
            if (elapsedTimeNs <= 0 || (this.minRenderPeriodNs == Long.MAX_VALUE && this.framesReceived == 0)) {
                return;
            }
            float renderFps = (this.framesRendered * TimeUnit.SECONDS.toNanos(1L)) / elapsedTimeNs;
            logD("Duration: " + TimeUnit.NANOSECONDS.toMillis(elapsedTimeNs) + " ms. Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered + ". Render fps: " + fpsFormat.format(renderFps) + ". Average render time: " + averageTimeAsString(this.renderTimeNs, this.framesRendered) + ". Average swapBuffer time: " + averageTimeAsString(this.renderSwapBufferTimeNs, this.framesRendered) + ".");
            resetStatistics(currentTimeNs);
        }
    }

    private void logE(String string, Throwable e) {
        Logging.m4e(TAG, this.name + string, e);
    }

    private void logD(String string) {
        Logging.m1d(TAG, this.name + string);
    }

    private void logW(String string) {
        Logging.m3w(TAG, this.name + string);
    }
}
