package javacsw.util.cfg;

import csw.util.cfg.Key;

/**
 * Java wrapper for a typed key.
 * This class is used to enforce strict type checking in Java when setting values for a key.
 * XXX TODO: Find a way to enforce that the type T is the same as the scala key's type!
 *
 * @param <T> the type of values stored with this key
 */
public class JKey<T> {
    public final Key key;
    public final Class<T> type;

    public JKey(Key key, Class<T> type) {
        this.key = key;
        this.type = type;
    }

    public static <T> JKey<T> create(Key key, Class<T> type) {
        return new JKey<>(key, type);
    }

    @Override
    public String toString() {
        return "JKey{" +
                "key=" + key +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JKey<?> jKey = (JKey<?>) o;

        return key.equals(jKey.key) && type.equals(jKey.type);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
}
