package com.activeviam.experiments.loom.numa.data;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import com.activeviam.experiments.loom.numa.platform.linux.LinuxPlatform;
import com.activeviam.experiments.loom.numa.util.UnsafeUtil;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class DirectChunkDouble implements IChunkDouble, AutoCloseable {

	public static final int ELEMENT_SIZE = Double.BYTES;
	protected final long address;
	protected final int numaNodeId;
	protected final int capacity;

	protected static final Cleaner cleaner = Cleaner.create();
	protected final Cleanable cleanable;

	protected static final sun.misc.Unsafe UNSAFE = UnsafeUtil.getUnsafe();

	protected DirectChunkDouble(int capacity, int numaNodeId, long address, Runnable destructor) {
		this.address = address;
		this.capacity = capacity;
		this.numaNodeId = numaNodeId;
		this.cleanable = cleaner.register(this, destructor);
	}

	public static DirectChunkDouble ofMmap(int capacity) {
		int numaNodeId = IPlatform.CURRENT_PLATFORM.getCurrentNumaNode();
		final long size = (long) capacity * ELEMENT_SIZE;
		final long address = IPlatform.CURRENT_PLATFORM.mmapAnon(size);

		Destructor destructor = new Destructor(
				address,
				(Long addr) -> {
					IPlatform.CURRENT_PLATFORM.munmap(addr, size);
					return null;
				}
		);
		return new DirectChunkDouble(capacity, numaNodeId, address, destructor);
	}

	public static DirectChunkDouble ofNumaAlloc(int capacity, int numaNodeId) {
		if (!(IPlatform.CURRENT_PLATFORM instanceof final LinuxPlatform platform)) {
			throw new UnsupportedOperationException("Cannot use numa_alloc_onnode() on non-Linux platform.");
		}

		final long size = (long) capacity * ELEMENT_SIZE;
		final long address = platform.numaAllocOnNode((int) size, numaNodeId);

		Destructor destructor = new Destructor(
				address,
				(Long addr) -> {
					platform.numaFree(addr, (int) size);
					return null;
				}
		);

		return new DirectChunkDouble(capacity, numaNodeId, address, destructor);
	}

	@Override
	public int capacity() {
		return capacity;
	}

	@Override
	public long getAddress() {
		return address;
	}

	@Override
	public int getNumaNodeId() {
		return numaNodeId;
	}

	@Override
	public double readDouble(int position) {
		assert position >= 0 && position < capacity;
		return UNSAFE.getDouble(address + ((long) position * ELEMENT_SIZE));
	}

	@Override
	public void writeDouble(int position, double value) {
		assert position >= 0 && position < capacity;
		UNSAFE.putDouble(address + ((long) position * ELEMENT_SIZE), value);
	}

	@Override
	public void close() throws Exception {
		cleanable.clean();
	}

	private static class Destructor implements Runnable {
		private final AtomicLong address;
		private final Function<Long, Void> oneshotCallback;

		public Destructor(long address, Function<Long, Void> oneshotCallback) {
			this.address = new AtomicLong(address);
			this.oneshotCallback = oneshotCallback;
		}

		@Override
		public void run() {
			long localAddress = address.getAndSet(0);
			if (localAddress != 0) {
				oneshotCallback.apply(localAddress);
			}
		}
	}
}
