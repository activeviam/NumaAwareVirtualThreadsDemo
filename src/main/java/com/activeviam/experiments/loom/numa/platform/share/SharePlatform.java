package com.activeviam.experiments.loom.numa.platform.share;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import com.activeviam.experiments.loom.numa.util.UnsafeUtil;
import java.util.logging.Logger;

/**
 * The default platform to use when on an unknown system.
 *
 * @author ActiveViam
 */
public class SharePlatform implements IPlatform {

	/** Singleton instance of this platform. */
	protected static volatile SharePlatform INSTANCE;

	/** Logger. */
	private static final Logger LOGGER = Logger.getLogger(SharePlatform.class.getName());

	private static final sun.misc.Unsafe UNSAFE = UnsafeUtil.getUnsafe();

	private SharePlatform() {
		LOGGER.warning("The platform is not supported. Everything will behave as on an UMA system.");
	}

	/** Returns the singleton Share Platform. */
	public static SharePlatform getInstance() {
		if (INSTANCE == null) {
			synchronized (SharePlatform.class) {
				if (INSTANCE == null) {
					INSTANCE = new SharePlatform();
				}
			}
		}
		return INSTANCE;
	}

	@Override
	public int getNUMANodeCount() {
		return 1;
	}

	@Override
	public int getCurrentNumaNode() {
		return 0;
	}

	@Override
	public boolean isNumaAvailable() {
		return false;
	}

	@Override
	public int getPointerNode(long pointer) {
		return 0;
	}

	@Override
	public long mmapAnon(long size) {
		return UNSAFE.allocateMemory(size);
	}

	@Override
	public void munmap(long ptr, long size) {
		UNSAFE.freeMemory(ptr);
	}

	@Override
	public void setNumaNode(int node) {
		// Do nothing.
	}

	@Override
	public int getProcessorCount() {
		return Runtime.getRuntime().availableProcessors();
	}

	@Override
	public int getNumaNode(int processorId) {
		return 0;
	}
}
