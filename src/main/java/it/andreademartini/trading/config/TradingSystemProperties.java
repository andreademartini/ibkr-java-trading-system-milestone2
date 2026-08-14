package it.andreademartini.trading.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "trading")
public class TradingSystemProperties {

    public static final String PAPER_CONFIRMATION_PHRASE =
            "I_CONFIRM_THIS_IS_A_PAPER_ACCOUNT";

    @NotNull
    private TradingMode mode = TradingMode.PAPER;

    /**
     * Rimane sempre false in questa versione. Il codice rifiuta comunque LIVE.
     */
    private boolean liveTradingEnabled = false;

    /**
     * Per inviare ordini al simulatore deve essere abilitato esplicitamente.
     */
    private boolean orderSubmissionEnabled = false;

    /**
     * Terza conferma esplicita richiesta quando gli ordini sono abilitati.
     */
    private String paperAccountConfirmation = "";

    @Valid
    private final IbkrProperties ibkr = new IbkrProperties();

    @Valid
    private final MarketDataProperties marketData = new MarketDataProperties();

    @Valid
    private final StrategyProperties strategy = new StrategyProperties();

    @Valid
    private final RiskProperties risk = new RiskProperties();

    @Valid
    private final TakeProfitProperties takeProfit = new TakeProfitProperties();

    @Valid
    private final OrderProperties orders = new OrderProperties();

    public TradingMode getMode() {
        return mode;
    }

    public void setMode(TradingMode mode) {
        this.mode = mode;
    }

    public boolean isLiveTradingEnabled() {
        return liveTradingEnabled;
    }

    public void setLiveTradingEnabled(boolean liveTradingEnabled) {
        this.liveTradingEnabled = liveTradingEnabled;
    }

    public boolean isOrderSubmissionEnabled() {
        return orderSubmissionEnabled;
    }

    public void setOrderSubmissionEnabled(boolean orderSubmissionEnabled) {
        this.orderSubmissionEnabled = orderSubmissionEnabled;
    }

    public String getPaperAccountConfirmation() {
        return paperAccountConfirmation;
    }

    public void setPaperAccountConfirmation(String paperAccountConfirmation) {
        this.paperAccountConfirmation = paperAccountConfirmation;
    }

    public IbkrProperties getIbkr() {
        return ibkr;
    }

    public MarketDataProperties getMarketData() {
        return marketData;
    }

    public StrategyProperties getStrategy() {
        return strategy;
    }

    public RiskProperties getRisk() {
        return risk;
    }

    public TakeProfitProperties getTakeProfit() {
        return takeProfit;
    }

    public OrderProperties getOrders() {
        return orders;
    }

    public static class IbkrProperties {

        @NotBlank
        private String host = "127.0.0.1";

        @Min(1)
        @Max(65535)
        private int port = 7497;

        @Min(0)
        private int clientId = 21;

        @NotNull
        private Duration historicalDataTimeout = Duration.ofSeconds(30);

        /**
         * Inserire esattamente l'account paper mostrato da TWS/IB Gateway.
         * L'applicazione non invia ordini finché managedAccounts non lo conferma.
         */
        private String accountId = "";

        private boolean autoConnect = true;

        /**
         * False per accettare solo le porte paper predefinite 7497 e 4002.
         */
        private boolean allowCustomPaperPort = false;

        @NotNull
        private Duration connectionTimeout = Duration.ofSeconds(10);

        @Valid
        private final ReconnectProperties reconnect = new ReconnectProperties();

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getClientId() {
            return clientId;
        }

        public void setClientId(int clientId) {
            this.clientId = clientId;
        }

        public Duration getHistoricalDataTimeout() {
            return historicalDataTimeout;
        }

        public void setHistoricalDataTimeout(Duration historicalDataTimeout) {
            this.historicalDataTimeout = historicalDataTimeout;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public boolean isAutoConnect() {
            return autoConnect;
        }

        public void setAutoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
        }

        public boolean isAllowCustomPaperPort() {
            return allowCustomPaperPort;
        }

        public void setAllowCustomPaperPort(boolean allowCustomPaperPort) {
            this.allowCustomPaperPort = allowCustomPaperPort;
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public ReconnectProperties getReconnect() {
            return reconnect;
        }
    }

    public static class ReconnectProperties {

        private boolean enabled = true;

        @NotNull
        private Duration delay = Duration.ofSeconds(5);

        @Min(0)
        private int maxAttempts = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class MarketDataProperties {

        private boolean enabled = true;

        @NotNull
        private MarketDataProvider provider = MarketDataProvider.IBKR;

        @NotNull
        private MarketDataMode mode = MarketDataMode.DELAYED;

        @Valid
        private List<InstrumentProperties> instruments = new ArrayList<>();

        public MarketDataProperties() {
            instruments.add(new InstrumentProperties());
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public MarketDataProvider getProvider() {
            return provider;
        }

        public void setProvider(MarketDataProvider provider) {
            this.provider = MarketDataProvider.IBKR;
        }

        public MarketDataMode getMode() {
            return mode;
        }

        public void setMode(MarketDataMode mode) {
            this.mode = mode;
        }

        public List<InstrumentProperties> getInstruments() {
            return instruments;
        }

        public void setInstruments(List<InstrumentProperties> instruments) {
            this.instruments = instruments == null ? new ArrayList<>() : instruments;
        }
    }

    public static class InstrumentProperties {

        @NotBlank
        private String symbol = "SPY";

        @NotBlank
        private String securityType = "STK";

        @NotBlank
        private String exchange = "SMART";

        @NotBlank
        private String currency = "USD";

        private String primaryExchange = "ARCA";

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getSecurityType() {
            return securityType;
        }

        public void setSecurityType(String securityType) {
            this.securityType = securityType;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getPrimaryExchange() {
            return primaryExchange;
        }

        public void setPrimaryExchange(String primaryExchange) {
            this.primaryExchange = primaryExchange;
        }
    }

    public static class StrategyProperties {

        @NotNull
        private StrategyType type = StrategyType.MOVING_AVERAGE_CROSSOVER;

        /**
         * Se true calcola segnali; non implica l'invio automatico.
         */
        private boolean enabled = true;

        /**
         * Se true i segnali automatici vengono passati al PaperOrderExecutionService.
         * Richiede comunque tutte le altre protezioni.
         */
        private boolean autoExecuteSignals = false;

        @Min(2)
        private int fastPeriod = 5;

        @Min(3)
        private int slowPeriod = 20;

        @Min(1)
        private long quantity = 1;

        @NotNull
        private Duration signalCooldown = Duration.ofMinutes(1);

        public StrategyType getType() {
            return type;
        }

        public void setType(StrategyType type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoExecuteSignals() {
            return autoExecuteSignals;
        }

        public void setAutoExecuteSignals(boolean autoExecuteSignals) {
            this.autoExecuteSignals = autoExecuteSignals;
        }

        public int getFastPeriod() {
            return fastPeriod;
        }

        public void setFastPeriod(int fastPeriod) {
            this.fastPeriod = fastPeriod;
        }

        public int getSlowPeriod() {
            return slowPeriod;
        }

        public void setSlowPeriod(int slowPeriod) {
            this.slowPeriod = slowPeriod;
        }

        public long getQuantity() {
            return quantity;
        }

        public void setQuantity(long quantity) {
            this.quantity = quantity;
        }

        public Duration getSignalCooldown() {
            return signalCooldown;
        }

        public void setSignalCooldown(Duration signalCooldown) {
            this.signalCooldown = signalCooldown;
        }

    }

    public static class RiskProperties {

        @DecimalMin("0.01")
        private double tradingCapital = 10_000.0;

        @DecimalMin("0.01")
        private double riskPerTradePercent = 2.0;

        @DecimalMin("0.01")
        private double maxOrderValue = 5_000.0;

        @DecimalMin("0.01")
        private double maxPositionValue = 10_000.0;

        @DecimalMin("0.01")
        private double maxLongNotional = 10_000.0;

        @DecimalMin("0.01")
        private double maxShortNotional = 10_000.0;

        @Min(1)
        private long maxQuantityPerOrder = 1_000;

        @Min(1)
        private int maxDailyOrders = 20;

        @Min(1)
        private int maxOpenOrders = 10;

        @DecimalMin("0.01")
        private double maxDailyLoss = 250.0;

        private boolean allowLong = true;

        private boolean allowShortSelling = true;

        private boolean useLeverage = false;

        @DecimalMin("0.0")
        private double entryTicket = 0.01;

        @DecimalMin("0.0")
        private double commissionPerOrder = 0.0;

        @DecimalMin("0.0")
        private double slippagePercent = 0.0;

        @DecimalMin("0.0")
        private double transactionCostsPerShare = 0.0;

        public double getTradingCapital() {
            return tradingCapital;
        }

        public void setTradingCapital(double tradingCapital) {
            this.tradingCapital = tradingCapital;
        }

        public double getRiskPerTradePercent() {
            return riskPerTradePercent;
        }

        public void setRiskPerTradePercent(double riskPerTradePercent) {
            this.riskPerTradePercent = riskPerTradePercent;
        }

        public double getMaxOrderValue() {
            return maxOrderValue;
        }

        public void setMaxOrderValue(double maxOrderValue) {
            this.maxOrderValue = maxOrderValue;
        }

        public double getMaxPositionValue() {
            return maxPositionValue;
        }

        public void setMaxPositionValue(double maxPositionValue) {
            this.maxPositionValue = maxPositionValue;
        }

        public double getMaxLongNotional() {
            return maxLongNotional;
        }

        public void setMaxLongNotional(double maxLongNotional) {
            this.maxLongNotional = maxLongNotional;
        }

        public double getMaxShortNotional() {
            return maxShortNotional;
        }

        public void setMaxShortNotional(double maxShortNotional) {
            this.maxShortNotional = maxShortNotional;
        }

        public long getMaxQuantityPerOrder() {
            return maxQuantityPerOrder;
        }

        public void setMaxQuantityPerOrder(long maxQuantityPerOrder) {
            this.maxQuantityPerOrder = maxQuantityPerOrder;
        }

        public int getMaxDailyOrders() {
            return maxDailyOrders;
        }

        public void setMaxDailyOrders(int maxDailyOrders) {
            this.maxDailyOrders = maxDailyOrders;
        }

        public int getMaxOpenOrders() {
            return maxOpenOrders;
        }

        public void setMaxOpenOrders(int maxOpenOrders) {
            this.maxOpenOrders = maxOpenOrders;
        }

        public double getMaxDailyLoss() {
            return maxDailyLoss;
        }

        public void setMaxDailyLoss(double maxDailyLoss) {
            this.maxDailyLoss = maxDailyLoss;
        }

        public boolean isAllowLong() {
            return allowLong;
        }

        public void setAllowLong(boolean allowLong) {
            this.allowLong = allowLong;
        }

        public boolean isAllowShortSelling() {
            return allowShortSelling;
        }

        public void setAllowShortSelling(boolean allowShortSelling) {
            this.allowShortSelling = allowShortSelling;
        }

        public boolean isUseLeverage() {
            return useLeverage;
        }

        public void setUseLeverage(boolean useLeverage) {
            this.useLeverage = useLeverage;
        }

        public double getEntryTicket() {
            return entryTicket;
        }

        public void setEntryTicket(double entryTicket) {
            this.entryTicket = entryTicket;
        }

        public double getCommissionPerOrder() {
            return commissionPerOrder;
        }

        public void setCommissionPerOrder(double commissionPerOrder) {
            this.commissionPerOrder = commissionPerOrder;
        }

        public double getSlippagePercent() {
            return slippagePercent;
        }

        public void setSlippagePercent(double slippagePercent) {
            this.slippagePercent = slippagePercent;
        }

        public double getTransactionCostsPerShare() {
            return transactionCostsPerShare;
        }

        public void setTransactionCostsPerShare(double transactionCostsPerShare) {
            this.transactionCostsPerShare = transactionCostsPerShare;
        }
    }

    public static class TakeProfitProperties {

        private boolean enabled = true;

        @Valid
        private final TargetProperties target1 = new TargetProperties(2.0, 50.0);

        @Valid
        private final TargetProperties target2 = new TargetProperties(4.0, 30.0);

        @Valid
        private final TargetProperties target3 = new TargetProperties(8.0, 20.0);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TargetProperties getTarget1() {
            return target1;
        }

        public TargetProperties getTarget2() {
            return target2;
        }

        public TargetProperties getTarget3() {
            return target3;
        }

        public List<TargetProperties> targets() {
            return List.of(target1, target2, target3);
        }
    }

    public static class TargetProperties {

        @DecimalMin("0.01")
        private double profitPercent;

        @DecimalMin("0.0")
        private double positionPercent;

        public TargetProperties() {
        }

        public TargetProperties(double profitPercent, double positionPercent) {
            this.profitPercent = profitPercent;
            this.positionPercent = positionPercent;
        }

        public double getProfitPercent() {
            return profitPercent;
        }

        public void setProfitPercent(double profitPercent) {
            this.profitPercent = profitPercent;
        }

        public double getPositionPercent() {
            return positionPercent;
        }

        public void setPositionPercent(double positionPercent) {
            this.positionPercent = positionPercent;
        }
    }

    public static class OrderProperties {

        @NotBlank
        private String entryTimeInForce = "DAY";

        @NotBlank
        private String protectiveTimeInForce = "GTC";

        private boolean outsideRegularTradingHours = false;

        @NotNull
        private Duration interOrderDelay = Duration.ofMillis(25);

        public String getEntryTimeInForce() {
            return entryTimeInForce;
        }

        public void setEntryTimeInForce(String entryTimeInForce) {
            this.entryTimeInForce = entryTimeInForce;
        }

        public String getProtectiveTimeInForce() {
            return protectiveTimeInForce;
        }

        public void setProtectiveTimeInForce(String protectiveTimeInForce) {
            this.protectiveTimeInForce = protectiveTimeInForce;
        }

        public boolean isOutsideRegularTradingHours() {
            return outsideRegularTradingHours;
        }

        public void setOutsideRegularTradingHours(boolean outsideRegularTradingHours) {
            this.outsideRegularTradingHours = outsideRegularTradingHours;
        }

        public Duration getInterOrderDelay() {
            return interOrderDelay;
        }

        public void setInterOrderDelay(Duration interOrderDelay) {
            this.interOrderDelay = interOrderDelay;
        }
    }
}
