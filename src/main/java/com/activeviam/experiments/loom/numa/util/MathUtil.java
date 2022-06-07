package com.activeviam.experiments.loom.numa.util;

import java.util.Arrays;

public class MathUtil {

	public static long[] removeBestsAndWorsts(long[] sample, int bests, int worsts) {
		Arrays.sort(sample);
		return Arrays.copyOfRange(sample, worsts, sample.length - bests);
	}

	public static long calculateMean(long[] sample) {
		if (sample.length == 0) {
			return 0;
		}

		long sum = 0;
		for (long value : sample) {
			sum += value;
		}

		return sum / sample.length;
	}
}
