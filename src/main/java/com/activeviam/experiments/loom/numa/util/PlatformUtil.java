package com.activeviam.experiments.loom.numa.util;

import com.activeviam.experiments.loom.numa.platform.IPlatform;
import java.util.HashMap;

public class PlatformUtil {
	public static HashMap<Integer, Integer> countProcessorsOnNumaNodes() {
		HashMap<Integer, Integer> result = new HashMap<>();
		int numaNodeCount = IPlatform.CURRENT_PLATFORM.getNUMANodeCount();

		for (int node = 0; node < numaNodeCount; ++node) {
			result.put(node, 0);
		}

		int processorCount = IPlatform.CURRENT_PLATFORM.getProcessorCount();
		for (int proc = 0; proc < processorCount; ++proc) {
			int node = IPlatform.CURRENT_PLATFORM.getNumaNode(proc);
			result.put(node, result.get(node) + 1);
		}

		return result;
	}

	public static HashMap<Integer, Integer> processorToNumaNodeMap() {
		HashMap<Integer, Integer> result = new HashMap<>();
		int processorCount = IPlatform.CURRENT_PLATFORM.getProcessorCount();

		for (int proc = 0; proc < processorCount; ++proc) {
			result.put(proc, IPlatform.CURRENT_PLATFORM.getNumaNode(proc));
		}

		return result;
	}
}
