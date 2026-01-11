package org.webrtc;

/* loaded from: classes.jar:org/webrtc/SSLCertificateVerifier.class */
public interface SSLCertificateVerifier {
    @CalledByNative
    boolean verify(byte[] bArr);
}
