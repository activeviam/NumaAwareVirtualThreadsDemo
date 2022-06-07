package com.activeviam.experiments.loom.numa.thread.virtual;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
import jdk.internal.misc.CarrierThread;

/**
 * This class extends {@link CarrierThread} with ability to move itself to the specified NUMA node.
 */
class NumaCarrierThread extends CarrierThread {

	private static final Logger LOGGER = Logger.getLogger(NumaCarrierThread.class.getName());
	protected final int numaNode;
	protected static final int MAX_ITERATIONS = 5;

	public NumaCarrierThread(ForkJoinPool pool, int numaNode) {
		super(pool);
		this.numaNode = numaNode;
	}

	public int getNumaNode() {
		return numaNode;
	}

	@Override
	protected void onStart() {
		super.onStart();
		moveToNumaNode(numaNode);
	}

	protected void moveToNumaNode(int node) {
		IPlatform.CURRENT_PLATFORM.setNumaNode(node);

		if (node < 0) {
			return;
		}

		for (int i = 0; i < MAX_ITERATIONS; ++i) {
			LOGGER.fine("Thread " + this + ": Waiting for rescheduling on the appropriate NUMA node, iteration #" + i);
			try {
				Thread.sleep(1 << i);
			} catch (InterruptedException ignored) {
			}
			if (IPlatform.CURRENT_PLATFORM.getCurrentNumaNode() == node) {
				return;
			}
		}

		LOGGER.warning("Thread " + this + ": Failed to move to the chosen NUMA node");
	}
}
