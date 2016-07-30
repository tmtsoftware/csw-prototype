package javacsw.services.cs;

import csw.services.cs.core.ConfigData;
import csw.services.cs.core.ConfigFileHistory;
import csw.services.cs.core.ConfigFileInfo;
import csw.services.cs.core.ConfigId;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Defines a synchronous (blocking) java interface for storing and retrieving configuration information
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface IBlockingConfigManager {

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
   * Gets and returns the config file stored under the given path, if found.
   *
   * @param path the configuration path
   * @return an optional object containing the configuration data, if found
   */
  Optional<IBlockingConfigData> get(File path);

  /**
   * Gets and returns the config file stored under the given path, if found.
   *
   * @param path the configuration path
   * @param id   id used to specify a specific version to fetch (default: latest)
   * @return an optional object containing the configuration data, if found
   */
  Optional<IBlockingConfigData> get(File path, ConfigId id);

  /**
   /**
   * Gets the file as it existed on the given date.
   * If date is before the file was created, the initial version is returned.
   * If date is after the last change, the most recent version is returned.
   * If the path does not exist in the repo, an empty value is returned.
   *
   * @param path the file path relative to the repository root
   * @param date the target date
   * @return an object that can be used to access the file's data, if found
   */
  Optional<IBlockingConfigData> get(File path, Date date);

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

  /**
   * Sets the "default version" of the file with the given path.
   * If this method is not called, the default version will always be the latest version.
   * After calling this method, the version with the given Id will be the default.
   *
   * @param path the file path relative to the repository root
   * @param id   an optional id used to specify a specific version
   *             (by default the id of the latest version is used)
   */
  void setDefault(File path, Optional<ConfigId> id);

  /**
   * Resets the "default version" of the file with the given path to be always the latest version.
   *
   * @param path the file path relative to the repository root
   */
  void resetDefault(File path);

  /**
   * Gets and returns the default version of the file stored under the given path.
   * If no default was set, this returns the latest version.
   *
   * @param path the file path relative to the repository root
   * @return an object that can be used to access the file's data, if found
   */
  Optional<ConfigData> getDefault(File path);

}
