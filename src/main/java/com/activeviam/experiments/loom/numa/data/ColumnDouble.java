package com.activeviam.experiments.loom.numa.data;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import java.util.Arrays;
import java.util.function.Function;

/**
 * A column of {@code double} values.
 * <p>
 * This column stores its data in {@link IChunkDouble chunks} and can be {@link #ensureCapacity(int)
 * expanded} as more data is stored in it.
 *
 * @author ActiveViam
 */
public class ColumnDouble {

	/** The size of a chunk. */
	public static final int CHUNK_SIZE = 1 << 20; // ~1M rows

	/** Data chunks. */
	protected IChunkDouble[] chunks;

	/** Chunk order (the base-2 log of the chunk capacity). */
	protected int chunkOrder;

	/** Mask to extract positions within chunks. */
	protected int chunkMask;

	/** This columns's capacity. */
	protected int capacity;

	protected final Function<Integer, IChunkDouble> chunkFactory;

	/**
	 * Constructor.
	 *
	 * @param chunkCapacity The target capacity of a chunk
	 */
	public ColumnDouble(final long chunkCapacity, final Function<Integer, IChunkDouble> chunkFactory) {
		// Compute the chunk order (i.e. the smallest integer k
		// such as 2^k >= chunkCapacity)
		final int chunkOrder = (int) (32 - Long.numberOfLeadingZeros(chunkCapacity - 1));
		this.chunkOrder = chunkOrder;
		this.chunkMask = (1 << chunkOrder) - 1;

		this.chunkFactory = chunkFactory;

		// Initialize the first chunk
		setChunkCount(0);
		recomputeCapacity();
	}

	/**
	 * Reads the value stored at the given position.
	 *
	 * @param position A row position in this column
	 * @return The value stored at this position
	 */
	public double readDouble(final int position) {
		return this.chunks[chunkId(position)].readDouble(chunkPosition(position));
	}

	/**
	 * Writes the given value at the specified position.
	 *
	 * @param position A position in this chunk
	 * @param value A value
	 */
	public void writeDouble(final int position, final double value) {
		this.chunks[chunkId(position)].writeDouble(chunkPosition(position), value);
	}

	/**
	 * Computes and returns the sum of all the values stored in this column.
	 *
	 * @return The sum of all the values stored in this column
	 */
	public double sum() {
		double result = 0;

		// Go through each chunk and aggregate their data
		final IChunkDouble[] chunks = this.chunks;
		final int chunkCapacity = getChunkCapacity();
		for (int c = 0, numChunks = chunks.length; c < numChunks; ++c) {
			final IChunkDouble chunk = chunks[c];
			for (int r = 0; r < chunkCapacity; ++r) {
				result += chunk.readDouble(r);
			}
		}

		// Return the aggregated result
		return result;
	}

	/**
	 * Read all the doubles in the column.
	 *
	 * @return a boolean to avoid compiler/runtime optimizations. This boolean is very likely to be false.
	 */
	public boolean readAll() {
		// Go through each chunk and aggregate their data
		final IChunkDouble[] chunks = this.chunks;
		final int chunkCapacity = getChunkCapacity();
		boolean a = false;
		for (int c = 0, numChunks = chunks.length; c < numChunks; ++c) {
			final IChunkDouble chunk = chunks[c];
			for (int r = 0; r < chunkCapacity; ++r) {
				// Avoid compiler otpimizations
				a = chunk.readDouble(r) == 0;
			}
		}

		return a;

	}

	/**
	 * Read all the doubles in the column and does at each iterations a bunch of simple operations like increments,
	 * assignation...
	 *
	 * @return a boolean to avoid compiler optims. It is very likely to be false.
	 */
	public boolean slowReadAll() {
		// Go through each chunk and aggregate their data
		final IChunkDouble[] chunks = this.chunks;
		final int chunkCapacity = getChunkCapacity();
		boolean a = false;
		for (int c = 0, numChunks = chunks.length; c < numChunks; ++c) {
			final IChunkDouble chunk = chunks[c];
			for (int r = 0; r < chunkCapacity; ++r) {
				if (!a) {
					a = chunk.readDouble(r) == 0;
				}

			}
		}

		return a;

	}

	protected boolean isNullAllowed() {
		return false;
	}

	protected boolean dummyCheck(int r) {
		return r < 0;
	}

	/**
	 * Returns the ID of the chunk that holds the value for the row at the given position.
	 *
	 * @param position A global row position in this column
	 * @return The ID of the chunk holding this row's value
	 */
	protected int chunkId(final int position) {
		return position >> this.chunkOrder;
	}

	/**
	 * Returns the position in the chunk corresponding to the given global position.
	 *
	 * @param position A global row position in this column
	 * @return The position in this chunk of this row
	 */
	protected int chunkPosition(final int position) {
		return position & this.chunkMask;
	}

	/**
	 * Makes sure the column has enough room to store the given number of rows.
	 *
	 * @param capacity The target capacity (in number of rows)
	 * @return The actual capacity of the column (after expansion)
	 */
	public int ensureCapacity(final int capacity) {
		if (this.capacity < capacity) {
			final int targetChunkCount = 1 + chunkId(capacity - 1);
			if (getChunkCount() < targetChunkCount) {
				setChunkCount(targetChunkCount);
				recomputeCapacity();
			}
		}
		return capacity;
	}

	/**
	 * Returns the current capacity of the column, which is the number of rows that can be stored in this column.
	 *
	 * @return The capacity of the column (in number of rows)
	 */
	public int getCapacity() {
		return this.capacity;
	}

	/**
	 * Sets the chunk count, either to increase or to reduce this column's capacity.
	 * <p>
	 * Even when a new chunk array is allocated because of the resize, the old chunks are reused in the new array.
	 * New chunks are created when there wasn't enough chunks in the old array.
	 *
	 * @param chunkCount The target number of chunks
	 */
	protected void setChunkCount(final int chunkCount) {
		final IChunkDouble[] previousChunks = this.chunks;
		final int previousCount = previousChunks == null ? 0 : previousChunks.length;
		if (previousCount != chunkCount) {
			final IChunkDouble[] newChunks = new IChunkDouble[chunkCount];
			for (int i = 0; i < chunkCount; ++i) {
				if (i < previousCount) {
					newChunks[i] = previousChunks[i];
				} else {
					newChunks[i] = createChunk(getChunkCapacity());
				}
			}
			this.chunks = newChunks;
		}
	}

	/**
	 * Returns the capacity of a chunk.
	 *
	 * @return The capacity of a chunk
	 */
	protected int getChunkCapacity() {
		return 1 << this.chunkOrder;
	}

	/**
	 * Returns the number of chunks in this column.
	 *
	 * @return The number of chunks in this column
	 */
	protected int getChunkCount() {
		final IChunkDouble[] chunks = this.chunks;
		return chunks == null ? 0 : chunks.length;
	}

	/**
	 * Creates a new chunk.
	 *
	 * @param capacity The requested chunk's capacity
	 * @return A newly created chunk
	 */
	protected IChunkDouble createChunk(final int capacity) {
		return chunkFactory.apply(capacity);
	}

	/**
	 * Recomputes this column's capacity.
	 * <p>
	 * This is often required after a changes in the number of chunks.
	 *
	 * @see #setChunkCount(int)
	 */
	protected void recomputeCapacity() {
		this.capacity = getChunkCount() * getChunkCapacity();
	}

	/**
	 * Verifies that all the chunks are allocated on the proper NUMA node
	 * */
	public boolean verifyNodeAffinity(int node) {
		if (node < 0) {
			return true;
		}

		if (chunks == null) {
			return true;
		}

		int[] chunkPerNodeDistribution = new int[IPlatform.CURRENT_PLATFORM.getNUMANodeCount()];

		for (IChunkDouble chunk : chunks) {
			long address = chunk.getAddress();
			int pointerNode = IPlatform.CURRENT_PLATFORM.getPointerNode(address);
			++chunkPerNodeDistribution[pointerNode];
		}

		if (chunkPerNodeDistribution[node] == chunks.length) {
			return true;
		}

		throw new IllegalStateException(
				"Wrong chunk placement (current node: " + node +
						", chunk distribution: " + Arrays.toString(chunkPerNodeDistribution) + ")"
		);
	}
}
