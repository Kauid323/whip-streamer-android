package org.webrtc;

/* loaded from: classes.jar:org/webrtc/Predicate.class */
public interface Predicate<T> {
    boolean test(T t);

    /* renamed from: or */
    default Predicate<T> m8or(final Predicate<? super T> other) {
        return new Predicate<T>() { // from class: org.webrtc.Predicate.1
            @Override // org.webrtc.Predicate
            public boolean test(T arg) {
                return Predicate.this.test(arg) || other.test(arg);
            }
        };
    }

    default Predicate<T> and(final Predicate<? super T> other) {
        return new Predicate<T>() { // from class: org.webrtc.Predicate.2
            @Override // org.webrtc.Predicate
            public boolean test(T arg) {
                return Predicate.this.test(arg) && other.test(arg);
            }
        };
    }

    default Predicate<T> negate() {
        return new Predicate<T>() { // from class: org.webrtc.Predicate.3
            @Override // org.webrtc.Predicate
            public boolean test(T arg) {
                return !Predicate.this.test(arg);
            }
        };
    }
}
