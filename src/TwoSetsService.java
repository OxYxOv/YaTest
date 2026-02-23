import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

@Service
public class TwoSetsService {
    private static final int HALF = 20;
    private static final int RANDOM_ROUNDS = 4;
    private static final int RANDOM_ATTEMPTS_PER_ROUND = 1_000_000;

    public Result findEqualDisjointSubsets(long[] numbers) {
        if (numbers.length == 0 || numbers.length > 62) {
            throw new IllegalArgumentException("Expected 1..62 numbers (64-bit mask limit)");
        }

        Result result = findInRange(numbers, 0, Math.min(HALF, numbers.length));
        if (result != null) {
            return result;
        }

        if (numbers.length > HALF) {
            result = findInRange(numbers, HALF, numbers.length - HALF);
            if (result != null) {
                return result;
            }

            result = findCrossHalves(numbers);
            if (result != null) {
                return result;
            }
        }

        result = findByRandomCollisions(numbers);
        if (result != null) {
            return result;
        }

        throw new IllegalStateException("No solution found");
    }

    private Result findInRange(long[] numbers, int start, int length) {
        if (length <= 0) {
            return null;
        }
        int subsets = 1 << length;
        Map<Long, Long> seen = new HashMap<>(subsets);

        for (int localMask = 1; localMask < subsets; localMask++) {
            long sum = 0;
            for (int bit = 0; bit < length; bit++) {
                if ((localMask & (1 << bit)) != 0) {
                    sum += numbers[start + bit];
                }
            }
            long mask = ((long) localMask) << start;
            Long previous = seen.putIfAbsent(sum, mask);
            if (previous != null) {
                Result result = toResult(previous, mask, numbers.length);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private Result findCrossHalves(long[] numbers) {
        int leftSize = Math.min(HALF, numbers.length);
        int rightSize = numbers.length - leftSize;
        int leftSubsets = 1 << leftSize;
        int rightSubsets = 1 << rightSize;

        Map<Long, Long> leftSums = new HashMap<>(leftSubsets);
        for (int leftMask = 1; leftMask < leftSubsets; leftMask++) {
            long sum = 0;
            for (int bit = 0; bit < leftSize; bit++) {
                if ((leftMask & (1 << bit)) != 0) {
                    sum += numbers[bit];
                }
            }
            leftSums.putIfAbsent(sum, (long) leftMask);
        }

        for (int rightMask = 1; rightMask < rightSubsets; rightMask++) {
            long sum = 0;
            for (int bit = 0; bit < rightSize; bit++) {
                if ((rightMask & (1 << bit)) != 0) {
                    sum += numbers[leftSize + bit];
                }
            }
            Long leftMask = leftSums.get(sum);
            if (leftMask != null) {
                long rightGlobalMask = ((long) rightMask) << leftSize;
                Result result = toResult(leftMask, rightGlobalMask, numbers.length);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private Result findByRandomCollisions(long[] numbers) {
        long maxMask = 1L << numbers.length;
        SplittableRandom random = new SplittableRandom();

        for (int round = 0; round < RANDOM_ROUNDS; round++) {
            Map<Long, Long> seen = new HashMap<>(RANDOM_ATTEMPTS_PER_ROUND * 2);
            for (int attempt = 0; attempt < RANDOM_ATTEMPTS_PER_ROUND; attempt++) {
                long mask = random.nextLong(1, maxMask);
                long sum = sumByMask(numbers, mask);
                Long previous = seen.putIfAbsent(sum, mask);
                if (previous != null) {
                    Result result = toResult(previous, mask, numbers.length);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private long sumByMask(long[] numbers, long mask) {
        long sum = 0;
        int index = 0;
        long current = mask;
        while (current != 0) {
            if ((current & 1L) != 0) {
                sum += numbers[index];
            }
            current >>>= 1;
            index++;
        }
        return sum;
    }

    private Result toResult(long firstMask, long secondMask, int n) {
        long left = firstMask & ~secondMask;
        long right = secondMask & ~firstMask;
        if (left == 0 || right == 0) {
            return null;
        }
        return new Result(maskToIndices(left, n), maskToIndices(right, n));
    }

    private List<Integer> maskToIndices(long mask, int n) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (((mask >>> i) & 1L) != 0) {
                indices.add(i + 1);
            }
        }
        return indices;
    }

    public record Result(List<Integer> first, List<Integer> second) {
    }
}
