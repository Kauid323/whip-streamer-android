package org.webrtc;

import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes.jar:org/webrtc/MediaConstraints.class */
public class MediaConstraints {
    public final List<KeyValuePair> mandatory = new ArrayList();
    public final List<KeyValuePair> optional = new ArrayList();

    /* loaded from: classes.jar:org/webrtc/MediaConstraints$KeyValuePair.class */
    public static class KeyValuePair {
        private final String key;
        private final String value;

        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @CalledByNative("KeyValuePair")
        public String getKey() {
            return this.key;
        }

        @CalledByNative("KeyValuePair")
        public String getValue() {
            return this.value;
        }

        public String toString() {
            return this.key + ": " + this.value;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            KeyValuePair that = (KeyValuePair) other;
            return this.key.equals(that.key) && this.value.equals(that.value);
        }

        public int hashCode() {
            return this.key.hashCode() + this.value.hashCode();
        }
    }

    private static String stringifyKeyValuePairList(List<KeyValuePair> list) {
        StringBuilder builder = new StringBuilder("[");
        for (KeyValuePair pair : list) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(pair.toString());
        }
        return builder.append("]").toString();
    }

    public String toString() {
        return "mandatory: " + stringifyKeyValuePairList(this.mandatory) + ", optional: " + stringifyKeyValuePairList(this.optional);
    }

    @CalledByNative
    List<KeyValuePair> getMandatory() {
        return this.mandatory;
    }

    @CalledByNative
    List<KeyValuePair> getOptional() {
        return this.optional;
    }
}
