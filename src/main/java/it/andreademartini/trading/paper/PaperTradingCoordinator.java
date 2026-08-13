package it.andreademartini.trading.paper;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.portfolio.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaperTradingCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(PaperTradingCoordinator.class);

    private final TradingSystemProperties properties;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;
    private final IbkrConnectionService connectionService;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PaperTradingCoordinator(
            TradingSystemProperties properties,
            MarketDataService marketDataService,
            PortfolioService portfolioService,
            IbkrConnectionService connectionService) {
        this.properties = properties;
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
        this.connectionService = connectionService;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        if (connectionService.isReady()) {
            portfolioService.start();
        } else {
            log.warn("IBKR non connesso: posizioni, ordini aperti e account summary non richiesti");
        }

        if (properties.getMarketData().getProvider() != MarketDataProvider.IBKR
                || connectionService.isReady()) {
            marketDataService.start();
        } else {
            log.warn("IBKR non connesso: market data IBKR non richiesti");
        }

        log.info("Servizi paper avviati: account, posizioni, ordini aperti e market data");
    }
}
