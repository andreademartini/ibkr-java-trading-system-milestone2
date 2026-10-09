package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemporalDatasetSplitterTest {
    @Test void performsChronologicalSeventyThirtySplit() {
        var splitter = new TemporalDatasetSplitter();
        int split = splitter.splitIndex(10, 70);
        assertEquals(7, split);
        for (int index = 0; index < split; index++) {
            assertEquals(DatasetSegment.IN_SAMPLE, splitter.segment(index, split));
        }
        for (int index = split; index < 10; index++) {
            assertEquals(DatasetSegment.OUT_OF_SAMPLE, splitter.segment(index, split));
        }
    }
}
