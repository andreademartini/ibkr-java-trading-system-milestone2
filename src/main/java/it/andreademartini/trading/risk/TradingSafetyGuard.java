package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TradingSafetyGuard {

    private static final Set<Integer> DEFAULT_PAPER_PORTS = Set.of(7497, 4002);
    private static final Set<Integer> DEFAULT_LIVE_PORTS = Set.of(7496, 4001);

    private final TradingSystemProperties properties;

    public TradingSafetyGuard(TradingSystemProperties properties) {
        this.properties = properties;
    }

    public void validateStartupConfiguration() {
        if (properties.getMode() == TradingMode.LIVE || properties.isLiveTradingEnabled()) {
            throw new IllegalStateException(
                    "La modalità LIVE è intenzionalmente bloccata in questo progetto");
        }

        if (properties.getMode() != TradingMode.PAPER) {
            throw new IllegalStateException(
                    "Questa milestone supporta solo PAPER. BACKTEST sarà aggiunto separatamente");
        }

        assertPaperPort();

        if (properties.getStrategy().getFastPeriod()
                >= properties.getStrategy().getSlowPeriod()) {
            throw new IllegalStateException(
                    "strategy.fast-period deve essere minore di strategy.slow-period");
        }

        if (properties.getStrategy().isAutoExecuteSignals()
                && !properties.isOrderSubmissionEnabled()) {
            throw new IllegalStateException(
                    "AUTO_EXECUTE_SIGNALS=true richiede IBKR_PAPER_ORDERS_ENABLED=true");
        }

        if (properties.isOrderSubmissionEnabled()) {
            assertPaperOrderConfiguration();
        }
    }

    public void assertIbkrConnectionAllowed() {
        if (properties.getMode() != TradingMode.PAPER
                || properties.isLiveTradingEnabled()) {
            throw new IllegalStateException(
                    "La connessione IBKR è consentita solo in modalità PAPER");
        }
        assertPaperPort();
    }

    public void assertPaperOrderConfiguration() {
        if (!properties.isOrderSubmissionEnabled()) {
            throw new IllegalStateException(
                    "Invio ordini disabilitato. Impostare IBKR_PAPER_ORDERS_ENABLED=true");
        }

        if (properties.getMode() != TradingMode.PAPER
                || properties.isLiveTradingEnabled()) {
            throw new IllegalStateException(
                    "Ordini consentiti esclusivamente in modalità PAPER");
        }

        if (!TradingSystemProperties.PAPER_CONFIRMATION_PHRASE.equals(
                properties.getPaperAccountConfirmation())) {
            throw new IllegalStateException(
                    "Conferma paper mancante. Impostare PAPER_ACCOUNT_CONFIRMATION="
                            + TradingSystemProperties.PAPER_CONFIRMATION_PHRASE);
        }

        if (properties.getIbkr().getAccountId() == null
                || properties.getIbkr().getAccountId().isBlank()) {
            throw new IllegalStateException(
                    "IBKR_PAPER_ACCOUNT_ID è obbligatorio per inviare ordini paper");
        }

        assertPaperPort();
    }

    private void assertPaperPort() {
        int port = properties.getIbkr().getPort();

        if (DEFAULT_LIVE_PORTS.contains(port)) {
            throw new IllegalStateException(
                    "Porta live IBKR rilevata (" + port + "). "
                            + "Usare 7497 per TWS Paper o 4002 per IB Gateway Paper");
        }

        if (!properties.getIbkr().isAllowCustomPaperPort()
                && !DEFAULT_PAPER_PORTS.contains(port)) {
            throw new IllegalStateException(
                    "Porta " + port + " non ammessa: per sicurezza sono consentite "
                            + "solo 7497 e 4002. Per una porta paper personalizzata impostare "
                            + "IBKR_ALLOW_CUSTOM_PAPER_PORT=true");
        }
    }
}
