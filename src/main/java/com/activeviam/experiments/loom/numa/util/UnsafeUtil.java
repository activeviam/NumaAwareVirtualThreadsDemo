package com.activeviam.experiments.loom.numa.util;

import java.lang.reflect.Field;
import java.security.PrivilegedExceptionAction;
import sun.misc.Unsafe;

/**
 * A wrapper class with some extra utilities on top of {@link sun.misc.Unsafe}.
 * <p>
 * It provides some low level methods for fine-tuned accesses to class variables. As its name indicates, uses of
 * these methods can be unsafe and are not advisable.
 *
 * @author ActiveViam
 */
public class UnsafeUtil {

	/**
	 * The Unsafe singleton.
	 */
	private static final sun.misc.Unsafe UNSAFE = retrieveUnsafe();

	/**
	 * Gets the cached {@link sun.misc.Unsafe} instance.
	 *
	 * @return {@link sun.misc.Unsafe} instance
	 */
	public static sun.misc.Unsafe getUnsafe() {
		return UNSAFE;
	}

	/**
	 * Returns a {@link sun.misc.Unsafe}. Suitable for use in a 3rd party package.
	 *
	 * @return a {@link sun.misc.Unsafe}
	 */
	private static sun.misc.Unsafe retrieveUnsafe() {
		try {
			return sun.misc.Unsafe.getUnsafe();
		} catch (SecurityException se) {
			try {
				return java.security.AccessController.doPrivileged((PrivilegedExceptionAction<Unsafe>) () -> {
					final Field f = Unsafe.class.getDeclaredField("theUnsafe");
					f.setAccessible(true);
					return (Unsafe) f.get(null);
				});
			} catch (java.security.PrivilegedActionException e) {
				throw new RuntimeException("Could not initialize intrinsics", e.getCause());
			}
		}
	}

	/**
	 * Private constructor to avoid instantiation.
	 */
	private UnsafeUtil() {
	}
}
