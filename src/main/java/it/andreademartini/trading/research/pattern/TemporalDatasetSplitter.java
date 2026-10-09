package it.andreademartini.trading.research.pattern;

import org.springframework.stereotype.Component;

@Component
public class TemporalDatasetSplitter {

    public int splitIndex(int barCount, int inSamplePercent) {
        if (barCount < 2) throw new IllegalArgumentException("Servono almeno due barre");
        if (inSamplePercent < 1 || inSamplePercent > 99) {
            throw new IllegalArgumentException("inSamplePercent deve essere tra 1 e 99");
        }
        return Math.max(1, Math.min(barCount - 1,
                (int) Math.floor(barCount * inSamplePercent / 100.0)));
    }

    public DatasetSegment segment(int signalBarIndex, int splitIndex) {
        return signalBarIndex < splitIndex
                ? DatasetSegment.IN_SAMPLE : DatasetSegment.OUT_OF_SAMPLE;
    }
}
