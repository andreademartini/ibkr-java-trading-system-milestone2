package it.andreademartini.trading.orders;

import java.time.Instant;
import java.util.List;

public record OrderSubmissionResult(
        String symbol,
        PaperOrderType requestedType,
        List<Integer> orderIds,
        String status,
        Instant submittedAt) {
}
