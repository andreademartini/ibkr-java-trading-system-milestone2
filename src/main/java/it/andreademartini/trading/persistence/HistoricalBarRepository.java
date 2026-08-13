package it.andreademartini.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HistoricalBarRepository extends JpaRepository<HistoricalBarEntity, Long> {

    List<HistoricalBarEntity> findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
            String provider,
            String symbol,
            String timeframe);

    List<HistoricalBarEntity> findByProviderAndSymbolAndTimeframeAndBarLabelIn(
            String provider,
            String symbol,
            String timeframe,
            Collection<String> barLabels);
}
