package org.webrtc;

import java.util.Locale;

/* loaded from: classes.jar:org/webrtc/SessionDescription.class */
public class SessionDescription {
    public final Type type;
    public final String description;

    /* loaded from: classes.jar:org/webrtc/SessionDescription$Type.class */
    public enum Type {
        OFFER,
        PRANSWER,
        ANSWER;

        public String canonicalForm() {
            return name().toLowerCase(Locale.US);
        }

        @CalledByNative("Type")
        public static Type fromCanonicalForm(String canonical) {
            return (Type) valueOf(Type.class, canonical.toUpperCase(Locale.US));
        }
    }

    @CalledByNative
    public SessionDescription(Type type, String description) {
        this.type = type;
        this.description = description;
    }

    @CalledByNative
    String getDescription() {
        return this.description;
    }

    @CalledByNative
    String getTypeInCanonicalForm() {
        return this.type.canonicalForm();
    }
}
