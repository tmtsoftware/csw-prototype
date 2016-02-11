package csw.services.apps.configServiceAnnex

import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.LocationService
import org.slf4j.LoggerFactory

/**
 * An HTTP server application that allows storing and retrieving files.
 * This is intended to be used by the config service to store large/binary
 * files.
 *
 * The URL format is http://host:port/filename, where filename is the SHA-1
 * hash of the file's contents.
 *
 * To upload a file, use POST, to download, use GET.
 * Use HEAD to check if a file already exists, DELETE to delete.
 * Files are immutable. (TODO: Should delete be available?)
 */
object ConfigServiceAnnexServerApp extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexServerApp"))
  LocationService.initInterface()
  ConfigServiceAnnexServer(registerWithLoc = true)
}
