package it.andreademartini.trading.ibkr;

import it.andreademartini.trading.config.TradingSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PaperAccountVerifier implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaperAccountVerifier.class);

    private final TradingSystemProperties properties;
    private final AtomicBoolean verified = new AtomicBoolean(false);

    public PaperAccountVerifier(
            TradingSystemProperties properties,
            IbkrEWrapper wrapper) {
        this.properties = properties;
        wrapper.addListener(this);
    }

    @Override
    public void onManagedAccounts(List<String> accountIds) {
        String expected = normalize(properties.getIbkr().getAccountId());
        boolean matches = !expected.isBlank()
                && accountIds.stream().map(this::normalize).anyMatch(expected::equals);

        verified.set(matches);

        if (matches) {
            log.info("Account IBKR configurato verificato tramite managedAccounts: {}", expected);
        } else if (expected.isBlank()) {
            log.warn("IBKR_PAPER_ACCOUNT_ID non configurato: invio ordini bloccato");
        } else {
            log.error("Account configurato {} non presente tra gli account IBKR {}",
                    expected, accountIds);
        }
    }

    @Override
    public void onConnectionClosed() {
        verified.set(false);
    }

    public boolean isVerified() {
        return verified.get();
    }

    public void assertVerified() {
        if (!verified.get()) {
            throw new IllegalStateException(
                    "Account paper non verificato. Controllare IBKR_PAPER_ACCOUNT_ID "
                            + "e il login Paper in TWS/IB Gateway");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
