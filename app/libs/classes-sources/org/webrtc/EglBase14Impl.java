package org.webrtc;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.support.annotation.Nullable;
import android.view.Surface;
import org.webrtc.EglBase14;

@TargetApi(EglBase14Impl.EGLExt_SDK_VERSION)
/* loaded from: classes.jar:org/webrtc/EglBase14Impl.class */
class EglBase14Impl implements EglBase14 {
    private static final String TAG = "EglBase14Impl";
    private static final int EGLExt_SDK_VERSION = 18;
    private static final int CURRENT_SDK_VERSION = Build.VERSION.SDK_INT;
    private EGLContext eglContext;

    @Nullable
    private EGLConfig eglConfig;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLDisplay eglDisplay = getEglDisplay();

    public static boolean isEGL14Supported() {
        Logging.m1d(TAG, "SDK version: " + CURRENT_SDK_VERSION + ". isEGL14Supported: " + (CURRENT_SDK_VERSION >= EGLExt_SDK_VERSION));
        return CURRENT_SDK_VERSION >= EGLExt_SDK_VERSION;
    }

    /* loaded from: classes.jar:org/webrtc/EglBase14Impl$Context.class */
    public static class Context implements EglBase14.Context {
        private final EGLContext egl14Context;

        @Override // org.webrtc.EglBase14.Context
        public EGLContext getRawContext() {
            return this.egl14Context;
        }

        @Override // org.webrtc.EglBase.Context
        @TargetApi(21)
        public long getNativeEglContext() {
            return EglBase14Impl.CURRENT_SDK_VERSION >= 21 ? this.egl14Context.getNativeHandle() : this.egl14Context.getHandle();
        }

        public Context(EGLContext eglContext) {
            this.egl14Context = eglContext;
        }
    }

    public EglBase14Impl(EGLContext sharedContext, int[] configAttributes) {
        this.eglConfig = getEglConfig(this.eglDisplay, configAttributes);
        int openGlesVersion = EglBase.getOpenGlesVersionFromConfig(configAttributes);
        Logging.m1d(TAG, "Using OpenGL ES version " + openGlesVersion);
        this.eglContext = createEglContext(sharedContext, this.eglDisplay, this.eglConfig, openGlesVersion);
    }

    @Override // org.webrtc.EglBase
    public void createSurface(Surface surface) {
        createSurfaceInternal(surface);
    }

    @Override // org.webrtc.EglBase
    public void createSurface(SurfaceTexture surfaceTexture) {
        createSurfaceInternal(surfaceTexture);
    }

    private void createSurfaceInternal(Object surface) {
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new IllegalStateException("Input must be either a Surface or SurfaceTexture");
        }
        checkIsNotReleased();
        if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Already has an EGLSurface");
        }
        int[] surfaceAttribs = {12344};
        this.eglSurface = EGL14.eglCreateWindowSurface(this.eglDisplay, this.eglConfig, surface, surfaceAttribs, 0);
        if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Failed to create window surface: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    @Override // org.webrtc.EglBase
    public void createDummyPbufferSurface() {
        createPbufferSurface(1, 1);
    }

    @Override // org.webrtc.EglBase
    public void createPbufferSurface(int width, int height) {
        checkIsNotReleased();
        if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Already has an EGLSurface");
        }
        int[] surfaceAttribs = {12375, width, 12374, height, 12344};
        this.eglSurface = EGL14.eglCreatePbufferSurface(this.eglDisplay, this.eglConfig, surfaceAttribs, 0);
        if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Failed to create pixel buffer surface with size " + width + "x" + height + ": 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    @Override // org.webrtc.EglBase
    public Context getEglBaseContext() {
        return new Context(this.eglContext);
    }

    @Override // org.webrtc.EglBase
    public boolean hasSurface() {
        return this.eglSurface != EGL14.EGL_NO_SURFACE;
    }

    @Override // org.webrtc.EglBase
    public int surfaceWidth() {
        int[] widthArray = new int[1];
        EGL14.eglQuerySurface(this.eglDisplay, this.eglSurface, 12375, widthArray, 0);
        return widthArray[0];
    }

    @Override // org.webrtc.EglBase
    public int surfaceHeight() {
        int[] heightArray = new int[1];
        EGL14.eglQuerySurface(this.eglDisplay, this.eglSurface, 12374, heightArray, 0);
        return heightArray[0];
    }

    @Override // org.webrtc.EglBase
    public void releaseSurface() {
        if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(this.eglDisplay, this.eglSurface);
            this.eglSurface = EGL14.EGL_NO_SURFACE;
        }
    }

    private void checkIsNotReleased() {
        if (this.eglDisplay == EGL14.EGL_NO_DISPLAY || this.eglContext == EGL14.EGL_NO_CONTEXT || this.eglConfig == null) {
            throw new RuntimeException("This object has been released");
        }
    }

    @Override // org.webrtc.EglBase
    public void release() {
        checkIsNotReleased();
        releaseSurface();
        detachCurrent();
        synchronized (EglBase.lock) {
            EGL14.eglDestroyContext(this.eglDisplay, this.eglContext);
        }
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(this.eglDisplay);
        this.eglContext = EGL14.EGL_NO_CONTEXT;
        this.eglDisplay = EGL14.EGL_NO_DISPLAY;
        this.eglConfig = null;
    }

    @Override // org.webrtc.EglBase
    public void makeCurrent() {
        checkIsNotReleased();
        if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't make current");
        }
        synchronized (EglBase.lock) {
            if (!EGL14.eglMakeCurrent(this.eglDisplay, this.eglSurface, this.eglSurface, this.eglContext)) {
                throw new RuntimeException("eglMakeCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
            }
        }
    }

    @Override // org.webrtc.EglBase
    public void detachCurrent() {
        synchronized (EglBase.lock) {
            if (!EGL14.eglMakeCurrent(this.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
                throw new RuntimeException("eglDetachCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
            }
        }
    }

    @Override // org.webrtc.EglBase
    public void swapBuffers() {
        checkIsNotReleased();
        if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't swap buffers");
        }
        synchronized (EglBase.lock) {
            EGL14.eglSwapBuffers(this.eglDisplay, this.eglSurface);
        }
    }

    @Override // org.webrtc.EglBase
    public void swapBuffers(long timeStampNs) {
        checkIsNotReleased();
        if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't swap buffers");
        }
        synchronized (EglBase.lock) {
            EGLExt.eglPresentationTimeANDROID(this.eglDisplay, this.eglSurface, timeStampNs);
            EGL14.eglSwapBuffers(this.eglDisplay, this.eglSurface);
        }
    }

    private static EGLDisplay getEglDisplay() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(0);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("Unable to get EGL14 display: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("Unable to initialize EGL14: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        return eglDisplay;
    }

    private static EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] configAttributes) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        if (numConfigs[0] <= 0) {
            throw new RuntimeException("Unable to find any matching EGL config");
        }
        EGLConfig eglConfig = configs[0];
        if (eglConfig == null) {
            throw new RuntimeException("eglChooseConfig returned null");
        }
        return eglConfig;
    }

    private static EGLContext createEglContext(@Nullable EGLContext sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int openGlesVersion) {
        EGLContext eglContext;
        if (sharedContext != null && sharedContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("Invalid sharedContext");
        }
        int[] contextAttributes = {12440, openGlesVersion, 12344};
        EGLContext rootContext = sharedContext == null ? EGL14.EGL_NO_CONTEXT : sharedContext;
        synchronized (EglBase.lock) {
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes, 0);
        }
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("Failed to create EGL context: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        return eglContext;
    }
}
