package com.activeviam.experiments.loom.numa.platform.linux;

import com.sun.jna.Library;

/**
 * The JNA {@link Library library} for the NUMA methods. Expose the {@code numa.h} methods.
 *
 * @author ActiveViam
 */
interface NumaLibrary extends Library {

	/**
	 * The name of the library.
	 */
	String LIBRARY_NAME = "numa";

	/**
	 * This mode specifies that any nondefault thread memory
	 * policy be removed, so that the memory policy "falls back"
	 * to the system default policy.  The system default policy
	 * is "local allocation"—that is, allocate memory on the node
	 * of the CPU that triggered the allocation.  nodemask must
	 * be specified as NULL.  If the "local node" contains no
	 * free memory, the system will attempt to allocate memory
	 * from a "near by" node.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_DEFAULT = 0;

	/**
	 * This mode sets the preferred node for allocation.  The
	 * kernel will try to allocate pages from this node first and
	 * fall back to "near by" nodes if the preferred node is low
	 * on free memory.  If nodemask specifies more than one node
	 * ID, the first node in the mask will be selected as the
	 * preferred node.  If the nodemask and maxnode arguments
	 * specify the empty set, then the policy specifies "local
	 * allocation" (like the system default policy discussed
	 * above).
	 * @see #numa_set_mempolicy
	 */
	int MPOL_PREFERRED = 1;

	/**
	 * This mode defines a strict policy that restricts memory
	 * allocation to the nodes specified in nodemask.  If
	 * nodemask specifies more than one node, page allocations
	 * will come from the node with the lowest numeric node ID
	 * first, until that node contains no free memory.
	 * Allocations will then come from the node with the next
	 * highest node ID specified in nodemask and so forth, until
	 * none of the specified nodes contain free memory.  Pages
	 * will not be allocated from any node not specified in the
	 * nodemask.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_BIND = 2;

	/**
	 * This mode interleaves page allocations across the nodes
	 * specified in nodemask in numeric node ID order.  This
	 * optimizes for bandwidth instead of latency by spreading
	 * out pages and memory accesses to those pages across
	 * multiple nodes.  However, accesses to a single page will
	 * still be limited to the memory bandwidth of a single node.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_INTERLEAVE = 3;

	/**
	 * (since Linux 3.8)
	 * This mode specifies "local allocation"; the memory is
	 * allocated on the node of the CPU that triggered the
	 * allocation (the "local node").  The nodemask and maxnode
	 * arguments must specify the empty set.  If the "local node"
	 * is low on free memory, the kernel will try to allocate
	 * memory from other nodes.  The kernel will allocate memory
	 * from the "local node" whenever memory for this node is
	 * available.  If the "local node" is not allowed by the
	 * process's current cpuset context, the kernel will try to
	 * allocate memory from other nodes.  The kernel will
	 * allocate memory from the "local node" whenever it becomes
	 * allowed by the process's current cpuset context.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_LOCAL = 4;

	/**
	 * (since Linux 2.6.26)
	 * A nonempty nodemask specifies physical node IDs.  Linux
	 * will not remap the nodemask when the process moves to a
	 * different cpuset context, nor when the set of nodes
	 * allowed by the process's current cpuset context changes.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_F_STATIC_NODES = (1 << 15);

	/**
	 * (since Linux 2.6.26)
	 * A nonempty nodemask specifies node IDs that are relative
	 * to the set of node IDs allowed by the process's current
	 * cpuset.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_F_RELATIVE_NODES = (1 << 14);

	/**
	 * (since Linux 5.12)
	 * When mode is MPOL_BIND, enable the kernel NUMA balancing
	 * for the task if it is supported by the kernel.  If the
	 * flag isn't supported by the kernel, or is used with mode
	 * other than MPOL_BIND, -1 is returned and errno is set to
	 * EINVAL.
	 * @see #numa_set_mempolicy
	 */
	int MPOL_F_NUMA_BALANCING = (1 << 13); /* Optimize with NUMA balancing if possible */

	/**
	 * Before any other calls in this library can be used numa_available() must be called. If it returns -1, all
	 * other functions in this library are undefined.
	 *
	 * @return -1 if NUMA is not available, a strictly positive value if it is available
	 */
	int numa_available();

	/**
	 * Returns the number of memory nodes in the system. This count includes any nodes that are currently disabled.
	 * This count is derived from the node numbers in /sys/devices/system/node. (Depends on the kernel being
	 * configured with /sys (CONFIG_SYSFS))
	 *
	 * @return the number of nodes
	 */
	int numa_num_configured_nodes();

	/**
	 * Returns the node that a cpu belongs to. If the user supplies an invalid cpu errno will be set to EINVAL and
	 * -1 will be returned.
	 *
	 * @param cpu the cpu id
	 * @return -1 if an invalid cpu id is passed
	 */
	int numa_node_of_cpu(int cpu);

	/**
	 * Moves the specified pages of the process pid to the memory nodes specified by nodes. The result of the move
	 * is reflected in status. The flags indicate constraints on the pages to be moved.
	 *
	 * @param pid The id of the process to move pages for, or 0 for current process.
	 * @param count The number of pages to move
	 * @param pages The pointer to the pages to move
	 * @param nodes The nodes to move the pages to, or null if we do not want to move them.
	 * @param status The node on which the pages are.
	 * @param flags The constraints on the move.
	 * @return 0 on success, -1 otherwise
	 */
	int numa_move_pages(int pid, int count, long[] pages, int[] nodes, int[] status, int flags);

	/**
	 * Sets the NUMA memory policy of the calling thread, which consists of a policy mode and zero
	 * or more nodes, to the values specified by the {@code mode}, {@code nodemask}, and {@code maxnode} arguments.
	 * @param mode must specify one of {@link #MPOL_DEFAULT}, {@link #MPOL_BIND},
	 *        {@link #MPOL_INTERLEAVE}, {@link #MPOL_PREFERRED}, or {@link #MPOL_LOCAL}.
	 *        All modes except {@link #MPOL_DEFAULT} require the caller
	 *        to specify the node or nodes to which the mode
	 *        applies, via the {@code nodemask} argument. The {@code mode} argument may also include an optional
	 *        mode flag ({@link #MPOL_F_NUMA_BALANCING}, {@link #MPOL_F_RELATIVE_NODES},
	 *        {@link #MPOL_F_STATIC_NODES}).
	 * @param nodemask A bit mask of node IDs that contains up to
	 *        {@code maxnode} bits. Where a {@code nodemask} is required, it must contain at least one node
	 *        that is on-line, allowed by the process's current cpuset context,
	 *        (unless the {@link #MPOL_F_STATIC_NODES} mode flag is specified), and
	 *        contains memory.  If the {@link #MPOL_F_STATIC_NODES} is set in mode and a
	 *        required {@code nodemask} contains no nodes that are allowed by the
	 *        process's current cpuset context, the memory policy reverts to
	 *        local allocation.  This effectively overrides the specified
	 *        policy until the process's cpuset context includes one or more of
	 *        the nodes specified by {@code nodemask}.
	 * @param maxnode Number of bits in the {@code nodemask} bit mask.
	 * */
	int numa_set_mempolicy(int mode, long[] nodemask, int maxnode);

	/**
	 * Allocates memory on a specific node. By default, tries to allocate memory on the specified node first, but
	 * falls back to other nodes when there is not enough memory. When {@link #numa_set_strict numa_set_strict(1)}
	 * was executed first, it does not fall back and fails the allocation when there is not enough memory on the
	 * intended node.
	 *
	 * The memory must bee freed with {@link #numa_free}.
	 * @param size The memory block size in bytes
	 * @param node The NUMA node index
	 * @return address or 0 on fail
	 * */
	long numa_alloc_onnode(long size, int node);

	/**
	 * Frees the memory allocated by {@link #numa_alloc_onnode}.
	 * @param address The memory block begin address
	 * @param size The memory block size
	 * */
	void numa_free(long address, long size);

	/**
	 * Sets a flag that says whether the functions allocating on specific nodes should use a strict policy. Strict
	 * means the allocation will fail if the memory cannot be allocated on the target node. Default operation is to
	 * fall back to other nodes. This doesn't apply to interleave and default. */
	void numa_set_strict(int strictFlag);

	/**
	 * Runs the current thread and its children on a specific node. They will not migrate to CPUs of other nodes
	 * until the node affinity is reset with a new call to numa_run_on_node_mask(). Passing -1 permits the kernel
	 * to schedule on all nodes again. On success, 0 is returned; on error -1 is returned, and errno is set to
	 * indicate the error.
	 *
	 * @param node the node id
	 * @return 0 if success, -1 if failed
	 */
	int numa_run_on_node(int node);
}
