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

import it.andreademartini.trading.strategy.StrategyTradeMode;

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

        @Valid
        private final JoeRossProperties joeRoss123RhTte = new JoeRossProperties();

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

        public JoeRossProperties getJoeRoss123RhTte() {
            return joeRoss123RhTte;
        }

        /** Configuration alias preserving the requested joe-ross-123-rh-tte key. */
        public JoeRossProperties getJoeRoss_123RhTte() {
            return joeRoss123RhTte;
        }

    }

    public static class JoeRossProperties {

        private boolean enabled = true;

        @NotNull
        private StrategyTradeMode direction = StrategyTradeMode.LONG_SHORT;

        @DecimalMin("0.00000001")
        private double minimumTick = 0.01;

        /** Extra distance beyond Point 2 required before accepting the violation. */
        @Min(0)
        private int point2BreakoutBufferTicks = 0;

        @Valid
        private final SwingProperties swing = new SwingProperties();

        @Valid
        private final RossHookProperties rossHook = new RossHookProperties();

        @Valid
        private final TteProperties tte = new TteProperties();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public StrategyTradeMode getDirection() {
            return direction;
        }

        public void setDirection(StrategyTradeMode direction) {
            this.direction = direction;
        }

        public double getMinimumTick() {
            return minimumTick;
        }

        public void setMinimumTick(double minimumTick) {
            this.minimumTick = minimumTick;
        }

        public int getPoint2BreakoutBufferTicks() {
            return point2BreakoutBufferTicks;
        }

        public void setPoint2BreakoutBufferTicks(int point2BreakoutBufferTicks) {
            this.point2BreakoutBufferTicks = point2BreakoutBufferTicks;
        }

        public SwingProperties getSwing() {
            return swing;
        }

        public RossHookProperties getRossHook() {
            return rossHook;
        }

        public TteProperties getTte() {
            return tte;
        }
    }

    public static class SwingProperties {

        @Min(1)
        private int pivotStrength = 2;

        public int getPivotStrength() {
            return pivotStrength;
        }

        public void setPivotStrength(int pivotStrength) {
            this.pivotStrength = pivotStrength;
        }
    }

    public static class RossHookProperties {

        private boolean require123 = true;

        public boolean isRequire123() {
            return require123;
        }

        public void setRequire123(boolean require123) {
            this.require123 = require123;
        }

        public boolean isRequire_123() {
            return require123;
        }

        public void setRequire_123(boolean require123) {
            this.require123 = require123;
        }
    }

    public static class TteProperties {

        private boolean enabled = true;

        @Min(1)
        private int maxBarsAfterRossHook = 3;

        @Min(0)
        private int entryBufferTicks = 1;

        @Min(0)
        private int stopBufferTicks = 1;

        @Valid
        private final MinimumRoomProperties minimumRoom = new MinimumRoomProperties();

        private boolean allowAfterThreeBarsOnDoubleTriple = false;

        @Min(0)
        private int doubleTripleToleranceTicks = 1;

        private boolean rejectGapOverEntry = true;

        private boolean fallbackToRossHookBreakout = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxBarsAfterRossHook() {
            return maxBarsAfterRossHook;
        }

        public void setMaxBarsAfterRossHook(int maxBarsAfterRossHook) {
            this.maxBarsAfterRossHook = maxBarsAfterRossHook;
        }

        public int getEntryBufferTicks() {
            return entryBufferTicks;
        }

        public void setEntryBufferTicks(int entryBufferTicks) {
            this.entryBufferTicks = entryBufferTicks;
        }

        public int getStopBufferTicks() {
            return stopBufferTicks;
        }

        public void setStopBufferTicks(int stopBufferTicks) {
            this.stopBufferTicks = stopBufferTicks;
        }

        public MinimumRoomProperties getMinimumRoom() {
            return minimumRoom;
        }

        public boolean isAllowAfterThreeBarsOnDoubleTriple() {
            return allowAfterThreeBarsOnDoubleTriple;
        }

        public void setAllowAfterThreeBarsOnDoubleTriple(boolean value) {
            this.allowAfterThreeBarsOnDoubleTriple = value;
        }

        public int getDoubleTripleToleranceTicks() {
            return doubleTripleToleranceTicks;
        }

        public void setDoubleTripleToleranceTicks(int value) {
            this.doubleTripleToleranceTicks = value;
        }

        public boolean isRejectGapOverEntry() {
            return rejectGapOverEntry;
        }

        public void setRejectGapOverEntry(boolean rejectGapOverEntry) {
            this.rejectGapOverEntry = rejectGapOverEntry;
        }

        public boolean isFallbackToRossHookBreakout() {
            return fallbackToRossHookBreakout;
        }

        public void setFallbackToRossHookBreakout(boolean value) {
            this.fallbackToRossHookBreakout = value;
        }
    }

    public static class MinimumRoomProperties {

        private boolean enabled = true;

        @NotBlank
        private String type = "R";

        @DecimalMin("0.0")
        private double value = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
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

        @Valid
        private final ProgressiveStopProperties progressiveStop =
                new ProgressiveStopProperties();

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

        public ProgressiveStopProperties getProgressiveStop() {
            return progressiveStop;
        }
    }

    public static class ProgressiveStopProperties {

        private boolean enabled = true;

        private boolean moveOnlyAfterFullTpFill = true;

        @Valid
        private final ProgressiveStopStepProperties afterTp1 =
                new ProgressiveStopStepProperties(ProgressiveStopMoveTarget.ENTRY);

        @Valid
        private final ProgressiveStopStepProperties afterTp2 =
                new ProgressiveStopStepProperties(ProgressiveStopMoveTarget.TP1);

        @Valid
        private final ProgressiveStopStepProperties afterTp3 =
                new ProgressiveStopStepProperties(ProgressiveStopMoveTarget.TP2);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMoveOnlyAfterFullTpFill() {
            return moveOnlyAfterFullTpFill;
        }

        public void setMoveOnlyAfterFullTpFill(
                boolean moveOnlyAfterFullTpFill) {
            this.moveOnlyAfterFullTpFill = moveOnlyAfterFullTpFill;
        }

        public ProgressiveStopStepProperties getAfterTp1() {
            return afterTp1;
        }

        public ProgressiveStopStepProperties getAfterTp2() {
            return afterTp2;
        }

        public ProgressiveStopStepProperties getAfterTp3() {
            return afterTp3;
        }
    }

    public static class ProgressiveStopStepProperties {

        @NotNull
        private ProgressiveStopMoveTarget moveTo;

        public ProgressiveStopStepProperties() {
            this(ProgressiveStopMoveTarget.ENTRY);
        }

        public ProgressiveStopStepProperties(ProgressiveStopMoveTarget moveTo) {
            this.moveTo = moveTo;
        }

        public ProgressiveStopMoveTarget getMoveTo() {
            return moveTo;
        }

        public void setMoveTo(ProgressiveStopMoveTarget moveTo) {
            this.moveTo = moveTo;
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
