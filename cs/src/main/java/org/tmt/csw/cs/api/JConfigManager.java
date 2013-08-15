package org.tmt.csw.cs.api;

import java.util.List;

/**
 * Defines a java interface for storing and retrieving configuration information
 */
public interface JConfigManager {

    /**
     * Creates a config file with the given path and data and optional comment.
     * An IOException is thrown if the file already exists.
     *
     * @param path       the config file path
     * @param configData the contents of the file
     * @param comment    a comment to associate with this file
     * @return a unique id that can be used to refer to the file
     */
    public ConfigId create(String path, ConfigData configData, String comment);

    /**
     * Updates the config file with the given path and data and optional comment.
     * An FileNotFoundException is thrown if the file does not exists.
     *
     * @param path       the config file path
     * @param configData the contents of the file
     * @param comment    a comment to associate with this file
     * @return a unique id that can be used to refer to the file
     */
    public ConfigId update(String path, ConfigData configData, String comment);

    /**
     * Gets and returns the latest version of the config file stored under the given path.
     *
     * @param path the configuration path
     * @return an object containing the configuration data, if found
     */
    public ConfigData get(String path);

    /**
     * Gets and returns the config file stored under the given path.
     *
     * @param path the configuration path
     * @param id   id used to specify a specific version to fetch
     * @return an object containing the configuration data, if found
     */
    public ConfigData get(String path, ConfigId id);

    /**
     * Returns true if the given path exists and is being managed
     *
     * @param path the configuration path
     * @return true if the file exists
     */
    public boolean exists(String path);

    /**
     * Deletes the given config file (older versions will still be available)
     *
     * @param path the configuration path
     */
    public void delete(String path);

    /**
     * Deletes the given config file (older versions will still be available)
     *
     * @param path    the configuration path
     * @param comment comment for the delete operation
     */
    public void delete(String path, String comment);

    /**
     * Returns a list containing all known configuration files
     *
     * @return a list containing one ConfigFileInfo object for each known config file
     */
    public List<ConfigFileInfo> list();

    /**
     * Returns a list of all known versions of a given path
     *
     * @return a list containing one ConfigFileHistory object for each version of path
     */
    public List<ConfigFileHistory> history(String path);
}
