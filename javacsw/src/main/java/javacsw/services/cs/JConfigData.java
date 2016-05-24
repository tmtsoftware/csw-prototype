package javacsw.services.cs;

import csw.services.cs.core.ConfigBytes;
import csw.services.cs.core.ConfigData;
import csw.services.cs.core.ConfigFile;
import csw.services.cs.core.ConfigString;
import javacsw.services.cs.core.JConfigDataImpl;
import scala.Unit;

import java.io.File;
import java.io.OutputStream;
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

    /**
     * Writes the data to the given output stream
     * @param os output stream to write to
     * @return a future indicating when done
     */
    CompletableFuture<Unit>  writeToOutputStream(OutputStream os);

    /**
     * Returns a ConfigData instance containing the given string that
     * can be passed as an argument to the config service methods
     * @param s the string data
     * @return the ConfigData instance
     */
    static ConfigData create(String s) {
        return new ConfigString(s);
    }

    /**
     * Returns a ConfigData instance containing the data from the given file
     * that can be passed as an argument to the config service methods
     * @param f the file
     * @return the ConfigData instance
     */
    static ConfigData create(File f) {
        return new ConfigFile(f, 4096);
    }

    /**
     * Returns a ConfigData instance containing data from the given array
     * that can be passed as an argument to the config service methods
     * @param ar the array of bytes
     * @return the ConfigData instance
     */
    static ConfigData create(byte[] ar) {
        return new ConfigBytes(ar);
    }

}
