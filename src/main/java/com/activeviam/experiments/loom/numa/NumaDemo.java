package com.activeviam.experiments.loom.numa;

import com.activeviam.experiments.loom.numa.data.ColumnDouble;
import com.activeviam.experiments.loom.numa.data.DirectChunkDouble;
import com.activeviam.experiments.loom.numa.data.IChunkDouble;
import com.activeviam.experiments.loom.numa.platform.IPlatform;
import com.activeviam.experiments.loom.numa.thread.virtual.NumaVirtualThreadRunner;
import com.activeviam.experiments.loom.numa.util.MathUtil;
import com.activeviam.experiments.loom.numa.util.PlatformUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class NumaDemo {
	static {
		try (InputStream is = NumaDemo.class.getClassLoader().getResourceAsStream("logging.properties")) {
			LogManager.getLogManager().readConfiguration(is);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static final Logger LOGGER = Logger.getLogger(NumaDemo.class.getName());

	public static final double DATA_SIZE = 1;

	public static final int NBR_TESTS = 30;

	protected static final HashMap<Integer, Integer> PROCESSOR_COUNT = PlatformUtil.countProcessorsOnNumaNodes();

	protected enum ETestType {
		REMOTE_NODE,
		RANDOM_NODE,
		HOME_NODE,
	}

	protected static final HashMap<ETestType, Integer> ALLOCATION_NODE;
	protected static final HashMap<ETestType, Integer> READ_NODE;

	static {
		int nodeCount = IPlatform.CURRENT_PLATFORM.getNUMANodeCount();

		HashMap<ETestType, Integer> allocationNode = new HashMap<>();
		allocationNode.put(ETestType.REMOTE_NODE, nodeCount / 2);
		allocationNode.put(ETestType.RANDOM_NODE, -1);
		allocationNode.put(ETestType.HOME_NODE, 0);
		ALLOCATION_NODE = allocationNode;

		HashMap<ETestType, Integer> readNode = new HashMap<>();
		readNode.put(ETestType.REMOTE_NODE, 0);
		readNode.put(ETestType.RANDOM_NODE, 0);
		readNode.put(ETestType.HOME_NODE, 0);
		READ_NODE = readNode;
	}

	public static void main(String[] args) {
		System.out.println("Platform support: " + IPlatform.CURRENT_PLATFORM.toString());
		boolean numaAvailable = IPlatform.CURRENT_PLATFORM.isNumaAvailable();
		System.out.println("NUMA Available: " + numaAvailable);
		if (!numaAvailable) {
			return;
		}

		System.out.println("NUMA node count: " + IPlatform.CURRENT_PLATFORM.getNUMANodeCount());

		LOGGER.info("=== SIMPLE BENCHMARK USING mmap() ===");
		new NumaDemo().printSimpleBenchmark(DirectChunkDouble::ofMmap);

		LOGGER.info("=== SIMPLE BENCHMARK USING numa_node_alloc() ===");
		new NumaDemo().printSimpleBenchmark((Integer capacity) ->
				DirectChunkDouble.ofNumaAlloc(capacity, IPlatform.CURRENT_PLATFORM.getCurrentNumaNode()));
	}

	public void printSimpleBenchmark(Function<Integer, IChunkDouble> chunkFactory) {
		long numRows = fromGigaToRows(DATA_SIZE);

		long randomNodeExecTime = runReadTest(ETestType.RANDOM_NODE, numRows, chunkFactory);
		long remoteNodeExecTime = runReadTest(ETestType.REMOTE_NODE, numRows, chunkFactory);
		long homeNodeExecTime = runReadTest(ETestType.HOME_NODE, numRows, chunkFactory);

		System.out.println(
				"Home node: " + homeNodeExecTime + "ms" + " | Random node: " + randomNodeExecTime + "ms - factor="
						+ (double) randomNodeExecTime / (double) homeNodeExecTime + " | Remote node: "
						+ remoteNodeExecTime + "ms - factor="
						+ (double) remoteNodeExecTime / (double) homeNodeExecTime);
	}

	protected long runReadTest(ETestType testType, long numRows, Function<Integer, IChunkDouble> chunkFactory) {
		collectAll();
		LOGGER.info("Starting " + testType + " node read tests");
		ColumnDouble[] memory = allocateMemoryOnNode(ALLOCATION_NODE.get(testType), numRows, chunkFactory);
		long execTime = (long) (readNTimesFromOneNode(READ_NODE.get(testType), memory, NBR_TESTS) * 1e-6);
		memory = null;
		LOGGER.info(
				NBR_TESTS + " " + testType + " node read tests were executed in an average of " + execTime + "ms");
		return execTime;
	}

	protected long readNTimesFromOneNode(int node, ColumnDouble[] memory, int nbrTests) {
		long[] execTime = new long[nbrTests];

		ThreadFactory threadFactory = (node < 0)
				? Thread.ofVirtual().factory()
				: NumaVirtualThreadRunner.getDefaultThreadFactory(node);

		for (int i = 0; i < nbrTests; ++i) {
			execTime[i] = readFromNode(node, memory, threadFactory);
		}

		if (nbrTests > 2) {
			execTime = MathUtil.removeBestsAndWorsts(execTime, 1, 1);
		}

		return MathUtil.calculateMean(execTime);
	}

	public long readFromNode(int node, ColumnDouble[] columns, ThreadFactory threadFactory) {
		long startTimeNs = System.nanoTime();

		final CountDownLatch latch = new CountDownLatch(columns.length);

		for (int col = 0; col < columns.length; ++col) {
			threadFactory.newThread(new ReadCommand(columns[col], latch)).start();
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, "Unexpected InteruptedException: ", e);
			throw new RuntimeException(e);
		}

		long endTimeNs = System.nanoTime();

		return endTimeNs - startTimeNs;
	}

	/**
	 * Makes sure that nothing remains from past objects in eden. This does 15 collections, which is the max amount
	 * of collection a young gen object can survive without being migrated to old gen.
	 */
	protected void collectAll() {
		for (int i = 0; i < 15; ++i) {
			System.gc();
		}
	}

	protected static long fromGigaToRows(double giga) {
		return (long) ((1L << 27) * giga);
	}

	protected ColumnDouble[] allocateMemoryOnNode(int node, long numRows,
			Function<Integer, IChunkDouble> chunkFactory) {
		int numCols = (node < 0)
				? IPlatform.CURRENT_PLATFORM.getProcessorCount()
				: PROCESSOR_COUNT.get(node);

		ThreadFactory threadFactory = (node < 0)
				? Thread.ofVirtual().factory()
				: NumaVirtualThreadRunner.getDefaultThreadFactory(node);

		ColumnDouble[] columns = new ColumnDouble[numCols];
		for (int i = 0; i < numCols; ++i) {
			columns[i] = new ColumnDouble(ColumnDouble.CHUNK_SIZE, chunkFactory);
		}

		generateData(threadFactory, node, columns, numRows);

		return columns;
	}

	protected void generateData(ThreadFactory threadFactory, int node, ColumnDouble[] columns, long numRows) {
		LOGGER.info("Generating " + numRows + " rows");

		long startTimeNs = System.nanoTime();

		long numRowsPerColumn = (numRows - 1) / columns.length + 1; // Division with ceiling
		final CountDownLatch latch = new CountDownLatch(columns.length);
		long rowsToGenerate = numRows;

		for (int col = 0; col < columns.length; ++col) {
			long nRows = Math.min(numRowsPerColumn, rowsToGenerate);
			rowsToGenerate -= nRows;
			threadFactory.newThread(new DataGeneratorCommand(nRows, node, columns[col], latch)).start();
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, "Unexpected InteruptedException: ", e);
			throw new RuntimeException(e);
		}

		long endTimeNs = System.nanoTime();

		LOGGER.info(numRows + " rows were generated in " + (endTimeNs - startTimeNs) * 1e-6 + " ms");
	}

	protected static class DataGeneratorCommand implements Runnable {

		protected final long rows;
		protected final int node;
		protected final ColumnDouble column;
		protected final CountDownLatch latch;

		public DataGeneratorCommand(long rows, int node, ColumnDouble column, CountDownLatch latch) {
			this.rows = rows;
			this.node = node;
			this.column = column;
			this.latch = latch;
		}

		@Override
		public void run() {
			try {
				if (this.node >= 0 && this.node != IPlatform.CURRENT_PLATFORM.getCurrentNumaNode()) {
					throw new IllegalStateException("Unexpected NUMA node");
				}

				column.ensureCapacity((int) this.rows);
				for (int r = 0; r < this.rows; ++r) {
					column.writeDouble(r, 1);
				}

				assert column.verifyNodeAffinity(node);
			} finally {
				latch.countDown();
			}
		}
	}

	protected static class ReadCommand implements Runnable {

		protected final ColumnDouble column;
		protected final CountDownLatch latch;

		public ReadCommand(ColumnDouble column, CountDownLatch latch) {
			this.column = column;
			this.latch = latch;
		}

		@Override
		public void run() {
			try {
				this.column.readAll();
			} finally {
				latch.countDown();
			}
		}
	}
}
