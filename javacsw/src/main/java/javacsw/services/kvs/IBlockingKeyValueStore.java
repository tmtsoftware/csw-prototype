package javacsw.services.kvs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Java interface for a blocking key / value store. This class blocks and waits for operations to complete
 * (rather than returning Futures as results).
 *
 * Note: Type T must extend KeyValueStore.KvsFormatter, but due to type issues between Scala and Java,
 * I'm leaving that out for now.
 */
//public interface IBlockingKeyValueStore<T extends KeyValueStore.KvsFormatter> {
@SuppressWarnings("unused")
public interface IBlockingKeyValueStore<T> {
    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key the key
     * @param value the value to store
     */
    void set(String key, T value);

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key the key
     * @param value the value to store
     * @param n the max number of history values to keep (0 means no history)
     */
    void set(String key, T value, int n);

    /**
     * Gets the value of the given key
     *
     * @param key the key
     * @return the result, None if the key was not found
     */
    Optional<T> get(String key);

    /**
     * Returns a list containing up to the last n values for the given key
     *
     * @param key the key to use
     * @param n max number of history values to return
     * @return list of the last n values
     */
    List<T> getHistory(String key, int n);

    /**
     * Deletes the given key(s) from the store
     *
     * @param key the key to delete
     * @return the number of keys that were deleted
     */
    boolean delete(String key);

    /**
     * Sets a value for the given key, where the value itself is a map with keys and values.
     *
     * @param key the key
     * @param value the map of values to store
     * @return the result (true if successful)
     */
    Boolean hmset(String key, Map<String, String> value);

    /**
     * This method is mainly useful for testing hmset. It gets the value of the given field
     * in the map that is the value for the given key. The value is returned here as a String.
     *
     * @param key the key
     * @param field the key for a value in the map
     * @return the result string value for the field, if found
     */
    Optional<String> hmget(String key, String field);
}
