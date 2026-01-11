package org.webrtc;

/* loaded from: classes.jar:org/webrtc/CryptoOptions.class */
public final class CryptoOptions {
    private final Srtp srtp;
    private final SFrame sframe;

    /* loaded from: classes.jar:org/webrtc/CryptoOptions$Srtp.class */
    public final class Srtp {
        private final boolean enableGcmCryptoSuites;
        private final boolean enableAes128Sha1_32CryptoCipher;
        private final boolean enableEncryptedRtpHeaderExtensions;

        private Srtp(boolean enableGcmCryptoSuites, boolean enableAes128Sha1_32CryptoCipher, boolean enableEncryptedRtpHeaderExtensions) {
            this.enableGcmCryptoSuites = enableGcmCryptoSuites;
            this.enableAes128Sha1_32CryptoCipher = enableAes128Sha1_32CryptoCipher;
            this.enableEncryptedRtpHeaderExtensions = enableEncryptedRtpHeaderExtensions;
        }

        @CalledByNative("Srtp")
        public boolean getEnableGcmCryptoSuites() {
            return this.enableGcmCryptoSuites;
        }

        @CalledByNative("Srtp")
        public boolean getEnableAes128Sha1_32CryptoCipher() {
            return this.enableAes128Sha1_32CryptoCipher;
        }

        @CalledByNative("Srtp")
        public boolean getEnableEncryptedRtpHeaderExtensions() {
            return this.enableEncryptedRtpHeaderExtensions;
        }
    }

    /* loaded from: classes.jar:org/webrtc/CryptoOptions$SFrame.class */
    public final class SFrame {
        private final boolean requireFrameEncryption;

        private SFrame(boolean requireFrameEncryption) {
            this.requireFrameEncryption = requireFrameEncryption;
        }

        @CalledByNative("SFrame")
        public boolean getRequireFrameEncryption() {
            return this.requireFrameEncryption;
        }
    }

    private CryptoOptions(boolean enableGcmCryptoSuites, boolean enableAes128Sha1_32CryptoCipher, boolean enableEncryptedRtpHeaderExtensions, boolean requireFrameEncryption) {
        this.srtp = new Srtp(enableGcmCryptoSuites, enableAes128Sha1_32CryptoCipher, enableEncryptedRtpHeaderExtensions);
        this.sframe = new SFrame(requireFrameEncryption);
    }

    public static Builder builder() {
        return new Builder();
    }

    @CalledByNative
    public Srtp getSrtp() {
        return this.srtp;
    }

    @CalledByNative
    public SFrame getSFrame() {
        return this.sframe;
    }

    /* loaded from: classes.jar:org/webrtc/CryptoOptions$Builder.class */
    public static class Builder {
        private boolean enableGcmCryptoSuites;
        private boolean enableAes128Sha1_32CryptoCipher;
        private boolean enableEncryptedRtpHeaderExtensions;
        private boolean requireFrameEncryption;

        private Builder() {
        }

        public Builder setEnableGcmCryptoSuites(boolean enableGcmCryptoSuites) {
            this.enableGcmCryptoSuites = enableGcmCryptoSuites;
            return this;
        }

        public Builder setEnableAes128Sha1_32CryptoCipher(boolean enableAes128Sha1_32CryptoCipher) {
            this.enableAes128Sha1_32CryptoCipher = enableAes128Sha1_32CryptoCipher;
            return this;
        }

        public Builder setEnableEncryptedRtpHeaderExtensions(boolean enableEncryptedRtpHeaderExtensions) {
            this.enableEncryptedRtpHeaderExtensions = enableEncryptedRtpHeaderExtensions;
            return this;
        }

        public Builder setRequireFrameEncryption(boolean requireFrameEncryption) {
            this.requireFrameEncryption = requireFrameEncryption;
            return this;
        }

        public CryptoOptions createCryptoOptions() {
            return new CryptoOptions(this.enableGcmCryptoSuites, this.enableAes128Sha1_32CryptoCipher, this.enableEncryptedRtpHeaderExtensions, this.requireFrameEncryption);
        }
    }
}
