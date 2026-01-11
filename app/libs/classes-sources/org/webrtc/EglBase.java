package org.webrtc;

import android.graphics.SurfaceTexture;
import android.support.annotation.Nullable;
import android.view.Surface;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLContext;
import org.webrtc.EglBase10;
import org.webrtc.EglBase14;

/* loaded from: classes.jar:org/webrtc/EglBase.class */
public interface EglBase {
    public static final int EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_ES3_BIT = 64;
    public static final int EGL_RECORDABLE_ANDROID = 12610;
    public static final Object lock = new Object();
    public static final int[] CONFIG_PLAIN = configBuilder().createConfigAttributes();
    public static final int[] CONFIG_RGBA = configBuilder().setHasAlphaChannel(true).createConfigAttributes();
    public static final int[] CONFIG_PIXEL_BUFFER = configBuilder().setSupportsPixelBuffer(true).createConfigAttributes();
    public static final int[] CONFIG_PIXEL_RGBA_BUFFER = configBuilder().setHasAlphaChannel(true).setSupportsPixelBuffer(true).createConfigAttributes();
    public static final int[] CONFIG_RECORDABLE = configBuilder().setIsRecordable(true).createConfigAttributes();

    /* loaded from: classes.jar:org/webrtc/EglBase$Context.class */
    public interface Context {
        public static final long NO_CONTEXT = 0;

        long getNativeEglContext();
    }

    void createSurface(Surface surface);

    void createSurface(SurfaceTexture surfaceTexture);

    void createDummyPbufferSurface();

    void createPbufferSurface(int i, int i2);

    Context getEglBaseContext();

    boolean hasSurface();

    int surfaceWidth();

    int surfaceHeight();

    void releaseSurface();

    void release();

    void makeCurrent();

    void detachCurrent();

    void swapBuffers();

    void swapBuffers(long j);

    static ConfigBuilder configBuilder() {
        return new ConfigBuilder();
    }

    /* loaded from: classes.jar:org/webrtc/EglBase$ConfigBuilder.class */
    public static class ConfigBuilder {
        private int openGlesVersion = 2;
        private boolean hasAlphaChannel;
        private boolean supportsPixelBuffer;
        private boolean isRecordable;

        public ConfigBuilder setOpenGlesVersion(int version) {
            if (version < 1 || version > 3) {
                throw new IllegalArgumentException("OpenGL ES version " + version + " not supported");
            }
            this.openGlesVersion = version;
            return this;
        }

        public ConfigBuilder setHasAlphaChannel(boolean hasAlphaChannel) {
            this.hasAlphaChannel = hasAlphaChannel;
            return this;
        }

        public ConfigBuilder setSupportsPixelBuffer(boolean supportsPixelBuffer) {
            this.supportsPixelBuffer = supportsPixelBuffer;
            return this;
        }

        public ConfigBuilder setIsRecordable(boolean isRecordable) {
            this.isRecordable = isRecordable;
            return this;
        }

        public int[] createConfigAttributes() {
            ArrayList<Integer> list = new ArrayList<>();
            list.add(12324);
            list.add(8);
            list.add(12323);
            list.add(8);
            list.add(12322);
            list.add(8);
            if (this.hasAlphaChannel) {
                list.add(12321);
                list.add(8);
            }
            if (this.openGlesVersion == 2 || this.openGlesVersion == 3) {
                list.add(12352);
                list.add(Integer.valueOf(this.openGlesVersion == 3 ? 64 : 4));
            }
            if (this.supportsPixelBuffer) {
                list.add(12339);
                list.add(1);
            }
            if (this.isRecordable) {
                list.add(Integer.valueOf(EglBase.EGL_RECORDABLE_ANDROID));
                list.add(1);
            }
            list.add(12344);
            int[] res = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                res[i] = list.get(i).intValue();
            }
            return res;
        }
    }

    static int getOpenGlesVersionFromConfig(int[] configAttributes) {
        for (int i = 0; i < configAttributes.length - 1; i++) {
            if (configAttributes[i] == 12352) {
                switch (configAttributes[i + 1]) {
                    case 4:
                        return 2;
                    case EGL_OPENGL_ES3_BIT /* 64 */:
                        return 3;
                    default:
                        return 1;
                }
            }
        }
        return 1;
    }

    static EglBase create(@Nullable Context sharedContext, int[] configAttributes) {
        if (sharedContext == null) {
            return EglBase14Impl.isEGL14Supported() ? createEgl14(configAttributes) : createEgl10(configAttributes);
        }
        if (sharedContext instanceof EglBase14.Context) {
            return createEgl14((EglBase14.Context) sharedContext, configAttributes);
        }
        if (sharedContext instanceof EglBase10.Context) {
            return createEgl10((EglBase10.Context) sharedContext, configAttributes);
        }
        throw new IllegalArgumentException("Unrecognized Context");
    }

    static EglBase create() {
        return create(null, CONFIG_PLAIN);
    }

    static EglBase create(Context sharedContext) {
        return create(sharedContext, CONFIG_PLAIN);
    }

    static EglBase10 createEgl10(int[] configAttributes) {
        return new EglBase10Impl(null, configAttributes);
    }

    static EglBase10 createEgl10(EglBase10.Context sharedContext, int[] configAttributes) {
        return new EglBase10Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
    }

    static EglBase10 createEgl10(EGLContext sharedContext, int[] configAttributes) {
        return new EglBase10Impl(sharedContext, configAttributes);
    }

    static EglBase14 createEgl14(int[] configAttributes) {
        return new EglBase14Impl(null, configAttributes);
    }

    static EglBase14 createEgl14(EglBase14.Context sharedContext, int[] configAttributes) {
        return new EglBase14Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
    }

    static EglBase14 createEgl14(android.opengl.EGLContext sharedContext, int[] configAttributes) {
        return new EglBase14Impl(sharedContext, configAttributes);
    }
}
