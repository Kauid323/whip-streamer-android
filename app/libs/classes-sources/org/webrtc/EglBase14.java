package org.webrtc;

import android.opengl.EGLContext;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/EglBase14.class */
public interface EglBase14 extends EglBase {

    /* loaded from: classes.jar:org/webrtc/EglBase14$Context.class */
    public interface Context extends EglBase.Context {
        EGLContext getRawContext();
    }
}
