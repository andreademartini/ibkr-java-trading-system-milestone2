package it.andreademartini.trading.persistence;

import it.andreademartini.trading.marketdata.BarInterval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    List<HistoricalBarEntity> findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
            String provider,
            String symbol,
            BarInterval interval,
            LocalDateTime from,
            LocalDateTime to);

    List<HistoricalBarEntity> findBySymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
            String symbol,
            BarInterval interval,
            LocalDateTime from,
            LocalDateTime to);

    Optional<HistoricalBarEntity> findFirstByProviderAndSymbolAndIntervalOrderByDateTimeAsc(
            String provider,
            String symbol,
            BarInterval interval);

    Optional<HistoricalBarEntity> findFirstByProviderAndSymbolAndIntervalOrderByDateTimeDesc(
            String provider,
            String symbol,
            BarInterval interval);

    long countByProviderAndSymbolAndInterval(
            String provider,
            String symbol,
            BarInterval interval);
}
