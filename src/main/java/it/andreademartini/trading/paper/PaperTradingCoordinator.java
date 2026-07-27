package it.andreademartini.trading.paper;

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

    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PaperTradingCoordinator(
            MarketDataService marketDataService,
            PortfolioService portfolioService) {
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        portfolioService.start();
        marketDataService.start();

        log.info("Servizi paper avviati: account, posizioni, ordini aperti e market data");
    }
}
