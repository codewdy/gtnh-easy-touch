package io.github.wdy.easytouch.nei;

/** Large adjustment is the smallest whole number of batches producing at least 64 items. */
final class QuantityButtonStep {
    private QuantityButtonStep() { }

    static int largeBatches(long batchOutput) {
        // Preserve batch controls for chance outputs that round to zero for a single batch.
        return batchOutput <= 0 ? 64 : (int) (63 / batchOutput + 1);
    }
}
