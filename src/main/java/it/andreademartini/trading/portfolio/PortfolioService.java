package it.andreademartini.trading.portfolio;

import com.ib.client.Contract;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import it.andreademartini.trading.ibkr.IbkrNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortfolioService implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int ACCOUNT_SUMMARY_REQUEST_ID = 2_001;
    private static final String ACCOUNT_TAGS =
            "NetLiquidation,AvailableFunds,BuyingPower,RealizedPnL,UnrealizedPnL";

    private final TradingSystemProperties properties;
    private final IbkrApiClient apiClient;
    private final Map<String, PositionSnapshot> positions = new ConcurrentHashMap<>();
    private final Map<String, String> accountSummary = new ConcurrentHashMap<>();

    public PortfolioService(
            TradingSystemProperties properties,
            IbkrApiClient apiClient,
            IbkrEWrapper wrapper) {
        this.properties = properties;
        this.apiClient = apiClient;
        wrapper.addListener(this);
    }

    public void start() {
        apiClient.requestPositions();
        apiClient.requestOpenOrders();
        apiClient.requestAccountSummary(ACCOUNT_SUMMARY_REQUEST_ID, ACCOUNT_TAGS);
        log.info("Richiesti posizioni, ordini aperti e riepilogo account");
    }

    @Override
    public void onPosition(
            String account,
            Contract contract,
            Object position,
            double averageCost) {
        String expectedAccount = normalize(properties.getIbkr().getAccountId());
        if (!expectedAccount.isBlank() && !expectedAccount.equals(normalize(account))) {
            return;
        }

        String symbol = normalize(contract.symbol());
        BigDecimal quantity = IbkrNumber.toBigDecimal(position);

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            positions.remove(symbol);
        } else {
            positions.put(symbol, new PositionSnapshot(
                    account, symbol, quantity, averageCost, Instant.now()));
        }

        log.info("Posizione aggiornata {} qty={} avgCost={}",
                symbol, quantity, averageCost);
    }

    @Override
    public void onAccountSummary(
            int requestId,
            String account,
            String tag,
            String value,
            String currency) {
        if (requestId != ACCOUNT_SUMMARY_REQUEST_ID) {
            return;
        }

        String expectedAccount = normalize(properties.getIbkr().getAccountId());
        if (!expectedAccount.isBlank() && !expectedAccount.equals(normalize(account))) {
            return;
        }

        accountSummary.put(tag, value);
        if (currency != null && !currency.isBlank()) {
            accountSummary.put(tag + ".currency", currency);
        }
    }

    public Optional<PositionSnapshot> position(String symbol) {
        return Optional.ofNullable(positions.get(normalize(symbol)));
    }

    public BigDecimal positionQuantity(String symbol) {
        return position(symbol)
                .map(PositionSnapshot::quantity)
                .orElse(BigDecimal.ZERO);
    }

    public Collection<PositionSnapshot> positions() {
        return positions.values().stream()
                .sorted(Comparator.comparing(PositionSnapshot::symbol))
                .toList();
    }

    public Map<String, String> accountSummary() {
        return Map.copyOf(accountSummary);
    }

    public double realizedPnl() {
        return parseDouble(accountSummary.get("RealizedPnL"));
    }

    public double unrealizedPnl() {
        return parseDouble(accountSummary.get("UnrealizedPnL"));
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
