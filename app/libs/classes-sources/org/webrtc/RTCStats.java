package org.webrtc;

import java.util.Map;

/* loaded from: classes.jar:org/webrtc/RTCStats.class */
public class RTCStats {
    private final long timestampUs;
    private final String type;

    /* renamed from: id */
    private final String f3id;
    private final Map<String, Object> members;

    public RTCStats(long timestampUs, String type, String id, Map<String, Object> members) {
        this.timestampUs = timestampUs;
        this.type = type;
        this.f3id = id;
        this.members = members;
    }

    public double getTimestampUs() {
        return this.timestampUs;
    }

    public String getType() {
        return this.type;
    }

    public String getId() {
        return this.f3id;
    }

    public Map<String, Object> getMembers() {
        return this.members;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{ timestampUs: ").append(this.timestampUs).append(", type: ").append(this.type).append(", id: ").append(this.f3id);
        for (Map.Entry<String, Object> entry : this.members.entrySet()) {
            builder.append(", ").append(entry.getKey()).append(": ");
            appendValue(builder, entry.getValue());
        }
        builder.append(" }");
        return builder.toString();
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (!(value instanceof Object[])) {
            if (value instanceof String) {
                builder.append('\"').append(value).append('\"');
                return;
            } else {
                builder.append(value);
                return;
            }
        }
        Object[] arrayValue = (Object[]) value;
        builder.append('[');
        for (int i = 0; i < arrayValue.length; i++) {
            if (i != 0) {
                builder.append(", ");
            }
            appendValue(builder, arrayValue[i]);
        }
        builder.append(']');
    }

    @CalledByNative
    static RTCStats create(long timestampUs, String type, String id, Map members) {
        return new RTCStats(timestampUs, type, id, members);
    }
}
