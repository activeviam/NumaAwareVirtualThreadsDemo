package com.activeviam.experiments.loom.numa.platform.linux;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provide access to native Linux NUMA functions.
 *
 * @author ActiveViam
 */
public class LinuxPlatform implements IPlatform {

	/** The minimum version of the linux kernel known to work properly. */
	protected static final String MINIMUM_KNOWN_WORKING_VERSION = "2.6.32";

	/**
	 * The JNA {@link Library library} to expose the {@code libc} methods.
	 */
	protected final PthreadLibrary pthreadLib;

	/**
	 * The JNA {@link Library library} for the NUMA methods.
	 */
	protected final NumaLibrary numaLib;

	/**
	 * The native C library.
	 */
	protected final CLibrary stdcLib;

	/**
	 * Whether NUMA is available on this machine. This is stored to reduce the number of native function calls.
	 */
	protected final boolean numaAvailable;

	/** This class logger. */
	protected static Logger LOGGER = Logger.getLogger(LinuxPlatform.class.getName());

	/**
	 * Instance of this class, used for singleton pattern.
	 */
	protected static volatile LinuxPlatform INSTANCE;

	/**
	 * Constructor.
	 */
	protected LinuxPlatform() {
		if (!Platform.isLinux()) {
			assert false;

			this.numaLib = null;
			this.pthreadLib = null;
			this.stdcLib = null;
			this.numaAvailable = false;

			LOGGER.warning("Tried to initialize a Linux native library on a non-Linux system.");
			return;
		}

		this.numaLib = tryLoadNumaLibrary();
		this.pthreadLib = tryLoadPthreadLibrary();
		this.stdcLib = tryLoadStdCLibrary();
		this.numaAvailable = findIfNumaIsAvailable();
	}

	/**
	 * Checks natively if numa is available on this machine. MUST be called before calling any other method on this
	 * class. {@link NumaLibrary#numa_available}.
	 */
	protected boolean findIfNumaIsAvailable() {
		if (numaLib == null) {
			return false;
		}

		boolean isNumaAvailable;
		try {
			isNumaAvailable = numaLib.numa_available() >= 0;
		} catch (UnsatisfiedLinkError e) {
			isNumaAvailable = false;
			LOGGER.log(
					Level.WARNING,
					"Either the numactl package has not been installed properly,"
							+ " or the Linux Kernel currently in use is too old."
							+ " NUMA optimizations have been disabled."
							+ " To benefit from NUMA optimizations, upgrade the kernel to version "
							+ MINIMUM_KNOWN_WORKING_VERSION + " or higher.",
					e);
		}

		// Disable NUMA for old kernels that do not support numa_num_configured_nodes.
		if (isNumaAvailable) {
			try {
				isNumaAvailable = numaLib.numa_num_configured_nodes() > 0;
			} catch (UnsatisfiedLinkError e) {
				isNumaAvailable = false;
				LOGGER.log(
						Level.WARNING,
						"The Linux Kernel currently in use is too old, NUMA optimizations have been disabled."
								+ " To benefit from NUMA optimizations, upgrade the kernel to version "
								+ MINIMUM_KNOWN_WORKING_VERSION + " or higher.",
						e);
			}
		}
		return isNumaAvailable;
	}

	private NumaLibrary tryLoadNumaLibrary() {
		NumaLibrary numaLib;
		try {
			numaLib = Native.load(NumaLibrary.LIBRARY_NAME, NumaLibrary.class);
			LOGGER.config("The native NUMA library was successfully loaded.");
		} catch (UnsatisfiedLinkError e) {
			numaLib = null;
			LOGGER.log(
					Level.CONFIG,
					"We are unable to load the native NUMA library. Things will behave as if "
							+ "NUMA was not available. Check that the `numactl` package is installed.",
					e);
		}
		return numaLib;
	}

	private PthreadLibrary tryLoadPthreadLibrary() {
		PthreadLibrary pthreadLib;
		try {
			pthreadLib = Native.load(PthreadLibrary.LIBRARY_NAME, PthreadLibrary.class);
			LOGGER.config("The Pthread library was successfully loaded.");
		} catch (UnsatisfiedLinkError e) {
			pthreadLib = null;
			LOGGER.log(Level.CONFIG, "We are unable to load thr Pthread library.", e);
		}

		return pthreadLib;
	}

	private CLibrary tryLoadStdCLibrary() {
		CLibrary stdcLib;
		try {
			stdcLib = Native.load(CLibrary.LIBRARY_NAME, CLibrary.class);
			LOGGER.config("The C library was successfully loaded.");
		} catch (UnsatisfiedLinkError e) {
			stdcLib = null;
			LOGGER.log(Level.CONFIG, "We were unable to load the C library.", e);
		}
		return stdcLib;
	}

	@Override
	public int getNUMANodeCount() {
		final int result = numaAvailable ? numaLib.numa_num_configured_nodes() : 1;
		if (result < 1) {
			Errno.throwLastError("numa_num_configured_nodes");
		}
		return result;
	}

	@Override
	public int getCurrentNumaNode() {
		final int cpu = pthreadLib.sched_getcpu();
		final int res = numaAvailable ? numaLib.numa_node_of_cpu(cpu) : 0;
		if (res < 0) {
			Errno.throwLastError("numa_node_of_cpu", cpu);
		}
		return res;
	}

	/** Returns the singleton Linux Platform. */
	public static LinuxPlatform getInstance() {
		if (!Platform.isLinux()) {
			return null;
		}

		if (INSTANCE == null) {
			synchronized (LinuxPlatform.class) {
				if (INSTANCE == null) {
					INSTANCE = new LinuxPlatform();
				}
			}
		}

		return INSTANCE;
	}

	@Override
	public boolean isNumaAvailable() {
		return numaAvailable;
	}

	@Override
	public int getPointerNode(long pointer) {
		if (!numaAvailable) {
			return 0;
		}
		int pageCnt = 1;

		int[] status = new int[pageCnt];
		status[0] = -1;

		try {
			final int result = numaLib.numa_move_pages(0, pageCnt, new long[] {pointer}, null, status, 0);
			if (result == -1) {
				Errno.throwLastError(
						"numa_move_pages",
						0,
						pageCnt,
						Arrays.toString(new long[] {pointer}),
						null,
						Arrays.toString(new int[] {-1}),
						0);
			}
			if (status[0] < 0) {
				Errno.throwError(
						-status[0],
						"numa_move_pages",
						0,
						pageCnt,
						Arrays.toString(new long[] {pointer}),
						null,
						Arrays.toString(new int[] {-1}),
						0);
			}
		} catch (UnsatisfiedLinkError e) {
			throw new RuntimeException(
					"numa_move_pages was not found. The linux kernel "
							+ "might not be up to date. It requires a kernel version of "
							+ "2.6.18 and the numaif.h header, in libnuma-devel package.",
					e);
		}

		return status[0];
	}

	@Override
	public long mmapAnon(long size) {
		if (stdcLib == null) {
			throw new RuntimeException(
					"C Library could not be loaded on your system. Calls to mmap are not available.");
		}
		if (size < 0) {
			throw new IllegalArgumentException("Cannot allocate a negative size, was " + size);
		}
		// All Linux distro should support MAP_ANONYMOUS, so no need to create a mapping in /dev/zero.
		final long ptr = stdcLib.mmap(
				0,
				size,
				CLibrary.PROT_READ | CLibrary.PROT_WRITE,
				CLibrary.MAP_PRIVATE | CLibrary.MAP_ANONYMOUS,
				-1,
				0);
		if (ptr == CLibrary.MAP_FAILED) {
			final int errno = Native.getLastError();
			switch (errno) {
				case Errno.EINVAL -> throw new IllegalArgumentException("Invalid length: was " + size);
				case Errno.ENOMEM -> throw new OutOfMemoryError(
						"No memory is available, or the process's maximum number of mappings has exceeded."
								+ " Could not allocate " + size);
				case Errno.EBADF ->
						throw new RuntimeException("Your system does not support map anonymous. (" + size + ")");
				default -> Errno.throwLastError(
						"mmap",
						0,
						size,
						CLibrary.PROT_READ | CLibrary.PROT_WRITE,
						CLibrary.MAP_PRIVATE | CLibrary.MAP_ANONYMOUS,
						-1,
						0);
			}
		}
		return ptr;
	}

	@Override
	public void munmap(long ptr, long size) {
		if (stdcLib == null) {
			throw new RuntimeException(
					"C Library could not be loaded on your system. Calls to munmap are not available.");
		}
		final int result = stdcLib.munmap(ptr, size);
		if (result != 0) {
			final int errno = Native.getLastError();
			if (errno == Errno.ENOMEM) {
				throw new OutOfMemoryError(
						"Tried to unmap partially a mapping, and "
								+ "now the process's maximum number of mappings has exceeded. (" + ptr + ","
								+ size + ")");
			} else {
				Errno.throwLastError("munmap", ptr, size);
			}
		}
	}

	@Override
	public void setNumaNode(int node) {
		if (!numaAvailable) {
			return;
		}
		if (numaLib.numa_run_on_node(node) != 0) {
			Errno.throwLastError("numa_run_on_node", node);
		}
	}

	@Override
	public int getProcessorCount() {
		return Runtime.getRuntime().availableProcessors();
	}

	@Override
	public int getNumaNode(int cpuId) {
		final int res = numaAvailable ? numaLib.numa_node_of_cpu(cpuId) : 0;
		if (res < 0) {
			Errno.throwLastError("numa_node_of_cpu", cpuId);
		}
		return res;
	}

	public long numaAllocOnNode(int size, int node) {
		if (!numaAvailable) {
			throw new RuntimeException("cannot perform numalib call");
		}
		if (size < 0) {
			throw new IllegalArgumentException("Cannot allocate a negative size, was " + size);
		}

		final long ptr = numaLib.numa_alloc_onnode(size, node);
		if (ptr == 0) {
			throw new OutOfMemoryError("numa_alloc_onnode() returned NULL, cannot allocate " + size + " bytes.");
		}

		return ptr;
	}

	public void numaFree(long addr, int size) {
		if (!numaAvailable) {
			throw new RuntimeException("cannot perform numalib call");
		}

		numaLib.numa_free(addr, size);
	}

	public void numaSetStrict(boolean strict) {
		if (!numaAvailable) {
			throw new RuntimeException("cannot perform numalib call");
		}

		numaLib.numa_set_strict(strict ? 1 : 0);
	}

	@Override
	public String toString() {
		return "LinuxPlatform [NUMA library found: " + (numaLib != null) + ", pthread library found: "
				+ (pthreadLib != null) + ", C library found: " + (stdcLib != null) + ", NUMA available="
				+ numaAvailable + "]";
	}
}
