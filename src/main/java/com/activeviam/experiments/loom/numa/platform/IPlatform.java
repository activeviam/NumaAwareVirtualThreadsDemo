package com.activeviam.experiments.loom.numa.platform;

/**
 * Interface providing access to numa capabilities, like binding a thread to a node. Should be implemented as a
 * singleton.
 *
 * @author ActiveViam
 */
public interface IPlatform {

	/**
	 * A reference to the current (singleton) platform.
	 */
	public static final IPlatform CURRENT_PLATFORM = PlatformHelper.CURRENT_PLATFORM;

	/**
	 * Retrieves the number of NUMA nodes on this machine.
	 *
	 * @return the number of available nodes
	 */
	// CHECKSTYLE.OFF: AbbreviationAsWordInName (mandatory for JNA)
	int getNUMANodeCount();
	// CHECKSTYLE.ON: AbbreviationAsWordInName

	/**
	 * Returns the id of the NUMA node on which the current thread is running. The NUMA node id must be an integer
	 * between 0 and (number of numa nodes - 1), or -1 in case of non critical failure to retrieve the current
	 * node.
	 *
	 * @return the node id.
	 */
	int getCurrentNumaNode();

	/**
	 * Checks if NUMA support is available on this machine.
	 *
	 * @return true if NUMA is available
	 */
	boolean isNumaAvailable();

	/**
	 * Returns the node of a given native memory pointer, or -1 in case of in case of non critical failure.
	 *
	 * @param pointer the pointer to native memory
	 * @return the node
	 */
	int getPointerNode(long pointer);

	/**
	 * MMAP an anonymous memory section (ie not backed by a file).
	 *
	 * @param size The size of the anonymous mapping in bytes.
	 * @return The allocated pointer.
	 */
	long mmapAnon(long size);

	/**
	 * Un-map a memory region.
	 *
	 * @param ptr The pointer to un-map, MUST be paged aligned.
	 * @param size The number of bytes to unmap.
	 */
	void munmap(long ptr, long size);

	/**
	 * Set the node on which to run the current thread. This call does not happen instantaneously, a few
	 * nanoseconds are needed, and it is non blocking. The NUMA node id must be an integer between 0 and (number of
	 * numa nodes - 1). Passing -1 permits the kernel to schedule on all nodes again.
	 *
	 * @param node id of node on which to run the current thread
	 */
	void setNumaNode(int node);

	/** Returns the number of logical processors in the platform. */
	int getProcessorCount();

	/**
	 * Returns the id of the node on which a given CPU is. The NUMA node id must be an integer between 0 and
	 * (number of numa nodes - 1), or -1 in case of non critical failure.
	 *
	 * @param processorId Id of the processor
	 * @return id of the node
	 */
	int getNumaNode(int processorId);
}
