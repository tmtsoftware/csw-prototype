package javacsw.services.cs;

import java.io.File;

/**
 * Java interface to ConfigData
 */
public interface JConfigData {
    /**
     * Returns a string representation of the data (assuming it is not binary data)
     */
    String toString();

    /**
     * Writes the data to the given file
     * @param file file to write to
     */
    void writeToFile(File file);

    // TODO
//    byte[] toByteArray();
}
