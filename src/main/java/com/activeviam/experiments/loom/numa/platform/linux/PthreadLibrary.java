package com.activeviam.experiments.loom.numa.platform.linux;

import com.sun.jna.Library;

/**
 * The JNA {@link Library library} to expose the {@code libc} methods.
 *
 * @author ActiveViam
 */
interface PthreadLibrary extends Library {

	/**
	 * The name of the library.
	 */
	String LIBRARY_NAME = "pthread";

	/**
	 * Returns the number of the CPU on which the calling thread is currently executing.
	 *
	 * @return On success, sched_getcpu() returns a nonnegative CPU number. On error, -1 is returned and errno is set
	 * to indicate the error.
	 */
	int sched_getcpu();

}
