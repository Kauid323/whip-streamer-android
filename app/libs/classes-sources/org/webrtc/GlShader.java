package org.webrtc;

import android.opengl.GLES20;
import java.nio.Buffer;
import java.nio.FloatBuffer;

/* loaded from: classes.jar:org/webrtc/GlShader.class */
public class GlShader {
    private static final String TAG = "GlShader";
    private int program;

    private static int compileShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            throw new RuntimeException("glCreateShader() failed. GLES20 error: " + GLES20.glGetError());
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = {0};
        GLES20.glGetShaderiv(shader, 35713, compileStatus, 0);
        if (compileStatus[0] != 1) {
            Logging.m2e(TAG, "Compile error " + GLES20.glGetShaderInfoLog(shader) + " in shader:\n" + source);
            throw new RuntimeException(GLES20.glGetShaderInfoLog(shader));
        }
        GlUtil.checkNoGLES2Error("compileShader");
        return shader;
    }

    public GlShader(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(35633, vertexSource);
        int fragmentShader = compileShader(35632, fragmentSource);
        this.program = GLES20.glCreateProgram();
        if (this.program == 0) {
            throw new RuntimeException("glCreateProgram() failed. GLES20 error: " + GLES20.glGetError());
        }
        GLES20.glAttachShader(this.program, vertexShader);
        GLES20.glAttachShader(this.program, fragmentShader);
        GLES20.glLinkProgram(this.program);
        int[] linkStatus = {0};
        GLES20.glGetProgramiv(this.program, 35714, linkStatus, 0);
        if (linkStatus[0] != 1) {
            Logging.m2e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(this.program));
            throw new RuntimeException(GLES20.glGetProgramInfoLog(this.program));
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        GlUtil.checkNoGLES2Error("Creating GlShader");
    }

    public int getAttribLocation(String label) {
        if (this.program == -1) {
            throw new RuntimeException("The program has been released");
        }
        int location = GLES20.glGetAttribLocation(this.program, label);
        if (location < 0) {
            throw new RuntimeException("Could not locate '" + label + "' in program");
        }
        return location;
    }

    public void setVertexAttribArray(String label, int dimension, FloatBuffer buffer) {
        setVertexAttribArray(label, dimension, 0, buffer);
    }

    public void setVertexAttribArray(String label, int dimension, int stride, FloatBuffer buffer) {
        if (this.program == -1) {
            throw new RuntimeException("The program has been released");
        }
        int location = getAttribLocation(label);
        GLES20.glEnableVertexAttribArray(location);
        GLES20.glVertexAttribPointer(location, dimension, 5126, false, stride, (Buffer) buffer);
        GlUtil.checkNoGLES2Error("setVertexAttribArray");
    }

    public int getUniformLocation(String label) {
        if (this.program == -1) {
            throw new RuntimeException("The program has been released");
        }
        int location = GLES20.glGetUniformLocation(this.program, label);
        if (location < 0) {
            throw new RuntimeException("Could not locate uniform '" + label + "' in program");
        }
        return location;
    }

    public void useProgram() {
        if (this.program == -1) {
            throw new RuntimeException("The program has been released");
        }
        synchronized (EglBase.lock) {
            GLES20.glUseProgram(this.program);
        }
        GlUtil.checkNoGLES2Error("glUseProgram");
    }

    public void release() {
        Logging.m1d(TAG, "Deleting shader.");
        if (this.program != -1) {
            GLES20.glDeleteProgram(this.program);
            this.program = -1;
        }
    }
}
