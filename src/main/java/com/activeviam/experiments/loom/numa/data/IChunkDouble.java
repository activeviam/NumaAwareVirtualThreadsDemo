package com.activeviam.experiments.loom.numa.data;

/**
 * A fixed-size chunk of {@code double} values.
 */
public interface IChunkDouble {

	/**
	 * Returns the capacity of this chunk (i.e. the number of values it can store).
	 *
	 * @return This chunk's capacity
	 */
	int capacity();

	/**
	 * Returns the base address of the underlying direct ByteBuffer or 0
	 *
	 * @return Underlying ByteBuffer's base address
	 * */
	long getAddress();

	int getNumaNodeId();

	/**
	 * Returns the value stored at the given position in this chunk.
	 *
	 * @param position A position in this chunk
	 * @return The value stored at that position in this chunk
	 */
	double readDouble(int position);

	/**
	 * Writes the given value at the specified position in this chunk.
	 *
	 * @param position A position in this chunk
	 * @param value A value
	 */
	void writeDouble(int position, double value);

}
