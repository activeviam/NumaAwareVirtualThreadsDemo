package com.activeviam.experiments.loom.numa.platform;

import com.activeviam.experiments.loom.numa.platform.linux.LinuxPlatform;
import com.activeviam.experiments.loom.numa.platform.share.SharePlatform;
import com.sun.jna.Platform;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A helper class to retrieve the current platform.
 *
 * @author ActiveViam
 */
class PlatformHelper {

	/**
	 * A reference to the current (singleton) platform.
	 */
	public static final IPlatform CURRENT_PLATFORM;

	/**
	 * The logger for this class.
	 */
	protected static final Logger LOGGER = Logger.getLogger(PlatformHelper.class.getName());

	static {

		try {

			CURRENT_PLATFORM = Platform.isLinux()
							? LinuxPlatform.getInstance()
							: SharePlatform.getInstance();

			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Created platform " + CURRENT_PLATFORM);
			}

		} catch (Throwable t) {
			// Make sure t does not get swallowed by a NoClassDefFoundError.
			t.printStackTrace();
			throw t;
		}

	}
}
