package com.activeviam.experiments.loom.numa.thread.virtual;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import com.activeviam.experiments.loom.numa.util.PlatformUtil;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * This class provides several thread pools (one per NUMA node) and methods to run {@link VirtualThread virtual
 * threads} on them.
 */
public class NumaVirtualThreadRunner {

	public static final String PROPERTY_PREFIX = "com.activeviam.experiments.loom.numa.thread.virtual";

	protected static final ForkJoinPool[] numaNodePools;
	protected static final AtomicReferenceArray<ThreadFactory> threadFactories;

	static {
		numaNodePools = prepareNumaNodePools();
		threadFactories = (numaNodePools == null) ? null : new AtomicReferenceArray<>(numaNodePools.length);
	}

	private static ForkJoinPool[] prepareNumaNodePools() {
		if (!IPlatform.CURRENT_PLATFORM.isNumaAvailable()) {
			return null;
		}

		HashMap<Integer, Integer> processorCount = PlatformUtil.countProcessorsOnNumaNodes();

		int numaNodeCount = IPlatform.CURRENT_PLATFORM.getNUMANodeCount();
		ForkJoinPool[] numaNodePools = new ForkJoinPool[numaNodeCount];
		for (int node = 0; node < numaNodeCount; ++node) {
			numaNodePools[node] = createNumaAwareScheduler(node, processorCount.get(node));
		}

		return numaNodePools;
	}

	/** Copy-paste of {@link VirtualThread#createDefaultScheduler()} */
	@SuppressWarnings("removal")
	private static ForkJoinPool createNumaAwareScheduler(final int numaNode, final int processorCount) {
		ForkJoinWorkerThreadFactory factory = pool -> {
			PrivilegedAction<ForkJoinWorkerThread> pa = () -> new NumaCarrierThread(pool, numaNode);
			return java.security.AccessController.doPrivileged(pa);
		};
		PrivilegedAction<ForkJoinPool> pa = () -> {
			int parallelism, maxPoolSize, minRunnable;
			String parallelismValue = System.getProperty(PROPERTY_PREFIX + ".parallelism");
			String maxPoolSizeValue = System.getProperty(PROPERTY_PREFIX + ".maxPoolSize");
			String minRunnableValue = System.getProperty(PROPERTY_PREFIX + ".minRunnable");
			if (parallelismValue != null) {
				parallelism = Integer.parseInt(parallelismValue);
			} else {
				parallelism = processorCount;
			}
			if (maxPoolSizeValue != null) {
				maxPoolSize = Integer.parseInt(maxPoolSizeValue);
				parallelism = Integer.min(parallelism, maxPoolSize);
			} else {
				maxPoolSize = Integer.max(parallelism, 256);
			}
			if (minRunnableValue != null) {
				minRunnable = Integer.parseInt(minRunnableValue);
			} else {
				minRunnable = Integer.max(parallelism / 2, 1);
			}
			UncaughtExceptionHandler handler = (t, e) -> { };
			boolean asyncMode = true; // FIFO
			return new ForkJoinPool(parallelism, factory, handler, asyncMode,
					0, maxPoolSize, minRunnable, pool -> true, 30, SECONDS);
		};
		return java.security.AccessController.doPrivileged(pa);
	}

	private static class NumaVirtualThreadFactory implements ThreadFactory {
		private static final VarHandle COUNT;
		static {
			try {
				MethodHandles.Lookup l = MethodHandles.lookup();
				COUNT = l.findVarHandle(NumaVirtualThreadFactory.class, "count", long.class);
			} catch (Exception e) {
				throw new InternalError(e);
			}
		}

		// Some reflection black magic to violate package privacy
		private static final Class<?> VIRTUAL_THREAD_CLASS;
		private static final Constructor<?> VIRTUAL_THREAD_CONSTRUCTOR;
		private static final Method VIRTUAL_THREAD_UEH_SETTER;

		static {
			try {
				VIRTUAL_THREAD_CLASS = Class.forName("java.lang.VirtualThread");

				Constructor<?> constructor = VIRTUAL_THREAD_CLASS.getDeclaredConstructor(
						Executor.class, String.class, int.class, Runnable.class
				);
				constructor.setAccessible(true);
				VIRTUAL_THREAD_CONSTRUCTOR = constructor;

				Method uehSetter = Thread.class.getDeclaredMethod(
						"uncaughtExceptionHandler", UncaughtExceptionHandler.class
				);
				uehSetter.setAccessible(true);
				VIRTUAL_THREAD_UEH_SETTER = uehSetter;

			} catch (Exception e) {
				throw new InternalError(e);
			}
		}

		private final String name;
		private final int characteristics;
		private final UncaughtExceptionHandler ueh;
		private final boolean hasCounter;
		private final ForkJoinPool scheduler;
		private volatile long count;

		private NumaVirtualThreadFactory(
				String name,
				long start,
				int characteristics,
				UncaughtExceptionHandler ueh,
				ForkJoinPool scheduler) {
			this.name = name;
			this.characteristics = characteristics;
			this.ueh = ueh;

			if (name != null && start >= 0) {
				this.hasCounter = true;
				this.count = start;
			} else {
				this.hasCounter = false;
			}

			this.scheduler = scheduler;
		}

		public int characteristics() {
			return characteristics;
		}

		public UncaughtExceptionHandler uncaughtExceptionHandler() {
			return ueh;
		}

		protected String nextThreadName() {
			if (hasCounter) {
				return name + (long) COUNT.getAndAdd(this, 1);
			} else {
				return name;
			}
		}

		@Override
		public Thread newThread(Runnable task) {
			Objects.requireNonNull(task);

			var thread = newVirtualThread(scheduler, nextThreadName(), characteristics(), task);
			UncaughtExceptionHandler ueh = uncaughtExceptionHandler();
			if (ueh != null) {
				setUncaughtExceptionHandler(thread, ueh);
			}

			return thread;
		}

		protected Thread newVirtualThread(
				ExecutorService executor, String threadName, int characteristics, Runnable task) {
			try {
				Thread t = (Thread) VIRTUAL_THREAD_CONSTRUCTOR.newInstance(executor, threadName, characteristics, task);
				Objects.requireNonNull(t);
				return t;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		protected void setUncaughtExceptionHandler(Thread t, UncaughtExceptionHandler ueh) {
			try {
				VIRTUAL_THREAD_UEH_SETTER.invoke(t, ueh);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static int getNumaPoolCount() {
		return numaNodePools == null ? 0 : numaNodePools.length;
	}

	private static void verifyNumaNodeId(int numaNode) {
		if (numaNode < 0 || numaNode >= getNumaPoolCount()) {
			throw new IllegalArgumentException(
					"Bad NUMA node id, must be in range [0, " + (getNumaPoolCount() - 1) + "]");
		}
	}

	public static ThreadFactory getDefaultThreadFactory(int numaNode) {
		verifyNumaNodeId(numaNode);

		if (threadFactories.get(numaNode) == null) {
			synchronized (NumaVirtualThreadRunner.class) {
				if (threadFactories.get(numaNode) == null) {
					ThreadFactory factory =  getNewThreadFactory(
							"NumaVirtualThread-node" + numaNode + "-", 0, null, numaNode);
					threadFactories.set(numaNode, factory);
				}
			}
		}

		return threadFactories.get(numaNode);
	}

	public static ThreadFactory getNewThreadFactory(
			String name, long start, UncaughtExceptionHandler ueh, int numaNode) {
		verifyNumaNodeId(numaNode);

		return new NumaVirtualThreadFactory(name, start, 0, ueh, numaNodePools[numaNode]);
	}
}
