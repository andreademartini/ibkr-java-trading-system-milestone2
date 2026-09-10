package it.andreademartini.trading.web;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionProfileServiceTest {

    @Test
    void responseSeedsMissingDefaultUniverseWithoutDuplicatingExistingStocks() {
        TradingStockRepository repository = mock(TradingStockRepository.class);
        TradingSystemProperties properties = new TradingSystemProperties();
        List<TradingStockEntity> stored = new ArrayList<>(List.of(
                stock("AAPL", "AAPL"),
                stock("SPY", "SPY")));
        AtomicReference<List<TradingStockEntity>> saved = new AtomicReference<>(List.of());

        when(repository.findAllByOrderBySymbolAsc()).thenAnswer(_invocation ->
                stored.stream()
                        .sorted(Comparator.comparing(TradingStockEntity::getSymbol))
                        .toList());
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<TradingStockEntity> entities = invocation.getArgument(0);
            List<TradingStockEntity> values = StreamSupport.stream(
                            entities.spliterator(),
                            false)
                    .toList();
            saved.set(values);
            stored.addAll(values);
            return values;
        });

        ActionProfilesResponse response =
                new ActionProfileService(repository, properties).response();

        assertThat(saved.get())
                .extracting(TradingStockEntity::getSymbol)
                .doesNotContain("AAPL", "SPY")
                .contains("MSFT", "NVDA", "QQQ", "XLY");
        assertThat(saved.get()).hasSize(28);
        assertThat(response.stocks())
                .extracting(ActionStockConfig::symbol)
                .containsExactlyInAnyOrder(
                        "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL",
                        "TSLA", "AMD", "AVGO", "NFLX", "JPM", "BAC",
                        "GS", "XOM", "CVX", "CAT", "BA", "DIS", "NKE",
                        "KO", "SPY", "QQQ", "IWM", "DIA", "XLK", "XLF",
                        "XLE", "SMH", "XLI", "XLY");
        assertThat(response.stocks())
                .filteredOn(stock -> stock.symbol().equals("MSFT"))
                .singleElement()
                .satisfies(stock -> {
                    assertThat(stock.name()).isEqualTo("MSFT - Azione - Tecnologia");
                    assertThat(stock.securityType()).isEqualTo("STK");
                    assertThat(stock.primaryExchange()).isEqualTo("NASDAQ");
                    assertThat(stock.strategies()).hasSize(1);
                    assertThat(stock.strategies().getFirst().name())
                            .contains("liquidita elevata");
                });

        new ActionProfileService(repository, properties).response();

        verify(repository, times(1)).saveAll(any());
    }

    private TradingStockEntity stock(String symbol, String name) {
        return new TradingStockEntity(
                symbol + "-id",
                symbol,
                name,
                "STK",
                "SMART",
                "USD",
                symbol.equals("SPY") ? "ARCA" : "NASDAQ",
                Instant.parse("2026-09-02T00:00:00Z"));
    }
}
