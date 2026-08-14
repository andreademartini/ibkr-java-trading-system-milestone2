package it.andreademartini.trading;

import it.andreademartini.trading.config.TradingMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionException;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.paper.PaperTradingCoordinator;
import it.andreademartini.trading.risk.TradingSafetyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TradingApplication {

    private static final Logger log = LoggerFactory.getLogger(TradingApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }

    @Bean
    ApplicationRunner startupRunner(
            TradingSystemProperties properties,
            TradingSafetyGuard safetyGuard,
            IbkrConnectionService connectionService,
            PaperTradingCoordinator coordinator) {

        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                safetyGuard.validateStartupConfiguration();

                log.info("Trading system avviato in modalità {}", properties.getMode());
                log.info("Invio ordini paper abilitato: {}", properties.isOrderSubmissionEnabled());
                log.info("Esecuzione automatica segnali: {}",
                        properties.getStrategy().isAutoExecuteSignals());

                if (properties.getMode() == TradingMode.BACKTEST) {
                    log.info("Modalita BACKTEST: connessione IBKR e servizi paper non avviati");
                    return;
                }

                if (!properties.getIbkr().isAutoConnect()) {
                    log.warn("Connessione automatica IBKR disabilitata");
                    coordinator.start();
                    return;
                }

                try {
                    connectionService.connect();
                    coordinator.start();
                } catch (IbkrConnectionException exception) {
                    log.error("Connessione iniziale a IBKR non riuscita: {}", exception.getMessage());
                    coordinator.start();
                }
            }
        };
    }
}
