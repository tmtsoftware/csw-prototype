package javacsw.services.cs;

import csw.services.cs.core.ConfigData;
import csw.services.cs.core.ConfigFileHistory;
import csw.services.cs.core.ConfigFileInfo;
import csw.services.cs.core.ConfigId;

import java.io.File;
import java.util.List;

/**
 * Defines a java interface for storing and retrieving configuration information
 */
@SuppressWarnings("unused")
public interface JConfigManager {

    /**
     * Creates a config file with the given path and data and optional comment.
     * An IOException is thrown if the file already exists.
     *
     * @param path       the config file path
     * @param configData the contents of the file
     * @param oversize   true if the file is large and requires special handling (external storage)
     * @param comment    a comment to associate with this file
     * @return a unique id that can be used to refer to the file
     */
    ConfigId create(File path, ConfigData configData, Boolean oversize, String comment);

    /**
     * Updates the config file with the given path and data and optional comment.
     * An FileNotFoundException is thrown if the file does not exists.
     *
     * @param path       the config file path
     * @param configData the contents of the file
     * @param comment    a comment to associate with this file
     * @return a unique id that can be used to refer to the file
     */
    ConfigId update(File path, ConfigData configData, String comment);

    /**
     * Creates a config file with the given path and data and optional comment,
     * or updates it, if it already exists.
     *
     * @param path       the config file path
     * @param configData the contents of the file
     * @param oversize   true if the file is large and requires special handling (external storage)
     * @param comment    a comment to associate with this file
     * @return a unique id that can be used to refer to the file
     */
    ConfigId createOrUpdate(File path, ConfigData configData, Boolean oversize, String comment);

    /**
     * Gets and returns the latest version of the config file stored under the given path.
     *
     * @param path the configuration path
     * @return an object containing the configuration data, if found
     */
    JConfigData get(File path);

    /**
     * Gets and returns the config file stored under the given path.
     *
     * @param path the configuration path
     * @param id   id used to specify a specific version to fetch
     * @return an object containing the configuration data, if found
     */
    JConfigData get(File path, ConfigId id);

    /**
     * Returns true if the given path exists and is being managed
     *
     * @param path the configuration path
     * @return true if the file exists
     */
    boolean exists(File path);

    /**
     * Deletes the given config file (older versions will still be available)
     *
     * @param path the configuration path
     */
    void delete(File path);

    /**
     * Deletes the given config file (older versions will still be available)
     *
     * @param path    the configuration path
     * @param comment comment for the delete operation
     */
    void delete(File path, String comment);

    /**
     * Returns a list containing all known configuration files
     *
     * @return a list containing one ConfigFileInfo object for each known config file
     */
    List<ConfigFileInfo> list();

    /**
     * Returns a list of all known versions of a given path
     *
     * @param path the relative path in the repo
     * @return a list containing one ConfigFileHistory object for each version of path
     */
    List<ConfigFileHistory> history(File path);
}
