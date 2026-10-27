package io.github.wdy.easytouch.nei;

import java.util.function.LongUnaryOperator;

/** Smallest native batch count whose rounded output is strictly above the opening inventory. */
final class InitialCraftingQuantity {
    private InitialCraftingQuantity() { }

    static long above(long owned, LongUnaryOperator outputForBatches) {
        long low = 1;
        long high = Integer.MAX_VALUE;
        while (low < high) {
            long middle = low + (high - low) / 2;
            if (outputForBatches.applyAsLong(middle) > owned) high = middle;
            else low = middle + 1;
        }
        return low;
    }
}
