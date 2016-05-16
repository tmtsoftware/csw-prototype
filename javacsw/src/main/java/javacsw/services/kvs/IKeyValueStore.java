package javacsw.services.kvs;

import scala.Unit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 *
 * Note: Type T must extend KeyValueStore.KvsFormatter, but due to type issues between Scala and Java,
 * I'm leaving that out for now.
 */
//public interface IKeyValueStore<T extends KeyValueStore.KvsFormatter> {
@SuppressWarnings("unused")
public interface IKeyValueStore<T> {
    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key the key
     * @param value the value to store
     */
    CompletableFuture<Unit> set(String key, T value);

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key the key
     * @param value the value to store
     * @param n the max number of history values to keep (0 means no history)
     */
    CompletableFuture<Unit> set(String key, T value, int n);

    /**
     * Gets the value of the given key
     *
     * @param key the key
     * @return the result, None if the key was not found
     */
    CompletableFuture<Optional<T>> get(String key);

    /**
     * Returns a list containing up to the last n values for the given key
     *
     * @param key the key to use
     * @param n max number of history values to return
     * @return list of the last n values
     */
    CompletableFuture<List<T>> getHistory(String key, int n);

    /**
     * Deletes the given key(s) from the store
     *
     * @param key the key to delete
     * @return the number of keys that were deleted
     */
    CompletableFuture<Boolean> delete(String key);

    /**
     * Sets a value for the given key, where the value itself is a map with keys and values.
     *
     * @param key the key
     * @param value the map of values to store
     * @return the result (true if successful)
     */
    CompletableFuture<Boolean> hmset(String key, Map<String, String> value);

    /**
     * This method is mainly useful for testing hmset. It gets the value of the given field
     * in the map that is the value for the given key. The value is returned here as a String.
     *
     * @param key the key
     * @param field the key for a value in the map
     * @return the result string value for the field, if found
     */
    CompletableFuture<Optional<String>> hmget(String key, String field);
}
