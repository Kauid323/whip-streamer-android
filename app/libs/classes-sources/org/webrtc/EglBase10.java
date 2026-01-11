package org.webrtc;

import javax.microedition.khronos.egl.EGLContext;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/EglBase10.class */
public interface EglBase10 extends EglBase {

    /* loaded from: classes.jar:org/webrtc/EglBase10$Context.class */
    public interface Context extends EglBase.Context {
        EGLContext getRawContext();
    }
}
