package javacsw.services.cs;

import scala.Unit;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Java asynchronous interface to ConfigData
 */
public interface JConfigData {
    /**
     * Returns a future string representation of the data (assuming it is not binary data)
     * @return the future string
     */
    CompletableFuture<String> toFutureString();

    /**
     * Writes the data to the given file
     * @param file file to write to
     * @return a future indicating when done
     */
    CompletableFuture<Unit>  writeToFile(File file);
}
