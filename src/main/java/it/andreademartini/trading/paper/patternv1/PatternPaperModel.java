package it.andreademartini.trading.paper.patternv1;

import it.andreademartini.trading.backtest.patternstable.PatternStableService;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Operational records are separate from backtest runs and contain actual broker fills only. */
public final class PatternPaperModel {
    private PatternPaperModel() {}
    public enum Mode { DISABLED, SHADOW, PAPER_AUTO, STOPPING, ERROR, KILL_SWITCH }
    public enum Role { ENTRY, STOP_LOSS, TAKE_PROFIT, TIMEOUT }
    public record Limits(BigDecimal dailyLoss, BigDecimal portfolioDrawdownPercent, Integer maxOpenPositions,
                         Integer maxNewOrdersPerDay, BigDecimal maxAggregateExposurePercent) {
        public void validate() {
            if(dailyLoss==null||dailyLoss.signum()<=0||portfolioDrawdownPercent==null||portfolioDrawdownPercent.signum()<=0
                    ||portfolioDrawdownPercent.compareTo(BigDecimal.valueOf(100))>0||maxOpenPositions==null||maxOpenPositions<1
                    ||maxNewOrdersPerDay==null||maxNewOrdersPerDay<1||maxAggregateExposurePercent==null
                    ||maxAggregateExposurePercent.signum()<=0||maxAggregateExposurePercent.compareTo(BigDecimal.valueOf(100))>0)
                throw new IllegalArgumentException("Configurare esplicitamente tutti i limiti operativi; esposizione aggregata massima 100%, senza leva");
        }
        public static Limits unset(){return new Limits(null,null,null,null,null);}
    }
    public static final class Session {
        public Mode mode=Mode.DISABLED;
        public boolean killSwitch, resumeRequested;
        public String reason="PAPER non attivato", account="", configurationHash="", strategyVersion="";
        public PatternStableService.Version frozen;
        public Limits limits=Limits.unset();
        public Instant startedAt, lastReconciliation, lastScheduler, lastSignalAt;
        public LocalDate activationDate;
        public long generation;
        public BigDecimal initialCapital=BigDecimal.valueOf(10000), peakEquity=BigDecimal.valueOf(10000), maximumDrawdown=BigDecimal.ZERO;
        public List<SignalRecord> signals=new ArrayList<>();
        public List<OrderRecord> orders=new ArrayList<>();
        public List<ExecutionRecord> executions=new ArrayList<>();
        public List<CommissionRecord> commissions=new ArrayList<>();
        public List<CommissionRecord> pendingCommissions=new ArrayList<>();
        public List<Trade> trades=new ArrayList<>();
        public List<EquityPoint> equityCurve=new ArrayList<>();
        public Set<String> processedCloses=new HashSet<>();
        public Set<String> commands=new HashSet<>();
        public Map<String,PatternPaperCalendar> calendars=new HashMap<>();
        public List<Trade> shadowTrades=new ArrayList<>();
        public List<EquityPoint> shadowEquity=new ArrayList<>();
        public Set<String> shadowProcessedCloses=new HashSet<>();
        public BigDecimal shadowPeak=BigDecimal.valueOf(10000), shadowMaximumDrawdown=BigDecimal.ZERO;
    }
    public static final class SignalRecord {
        public String id, mode, symbol, status, reason;
        public Signal signal;
        public Instant generatedAt;
        public LocalDate entrySession;
        public BigDecimal expectedEntry, expectedStop, expectedTarget;
        public long expectedQuantity;
        public SignalRecord() {}
    }
    public static final class OrderRecord {
        public int orderId, parentId;
        public long permId, quantity;
        public String signalId, clientOrderId, account, symbol, action, type, status="INTENT", ocaGroup;
        public Role role;
        public BigDecimal limitPrice, stopPrice, filled=BigDecimal.ZERO, remaining=BigDecimal.ZERO;
        public Instant createdAt, updatedAt;
        public boolean transmit;
        public OrderRecord() {}
        public boolean terminal(){return Set.of("Filled","Cancelled","ApiCancelled","Inactive","REJECTED").contains(status);}
    }
    public record ExecutionRecord(String executionId, String signalId, int orderId, long permId, String account,
                                  String symbol, String side, BigDecimal quantity, BigDecimal price, Instant time, Role role) {}
    public record CommissionRecord(String executionId, BigDecimal amount, String currency, Instant reportedAt) {}
    public static final class Trade {
        public String signalId, symbol, direction, pattern, mode, exitReason;
        public LocalDate signalDate, entryDate, lastCountedSession;
        public Instant entryAt, exitAt;
        public BigDecimal entryValue=BigDecimal.ZERO, exitValue=BigDecimal.ZERO, entered=BigDecimal.ZERO,
                exited=BigDecimal.ZERO, stop, target, expectedEntry, expectedExit, expectedSlippage, expectedRisk,
                commission=BigDecimal.ZERO, shadowSlippage=BigDecimal.ZERO, mark, realized=BigDecimal.ZERO;
        public long expectedQuantity;
        public int holdingBars;
        public boolean timeoutSubmitted;
        public Trade() {}
        public BigDecimal remaining(){return entered.subtract(exited);}
        public BigDecimal entryPrice(){return entered.signum()==0?BigDecimal.ZERO:entryValue.divide(entered,java.math.MathContext.DECIMAL64);}
        public BigDecimal exitPrice(){return exited.signum()==0?BigDecimal.ZERO:exitValue.divide(exited,java.math.MathContext.DECIMAL64);}
        public BigDecimal unrealized(){return mark==null?BigDecimal.ZERO:mark.subtract(entryPrice()).multiply(remaining()).multiply(direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate());}
        public BigDecimal netPnl(){return realized.add(unrealized()).subtract(commission).subtract(shadowSlippage);}
        public boolean closed(){return entered.signum()>0&&remaining().signum()==0;}
    }
    public record EquityPoint(Instant time, String mode, BigDecimal equity, BigDecimal realized, BigDecimal unrealized,
                              BigDecimal commissions, BigDecimal available, BigDecimal committed, BigDecimal longExposure,
                              BigDecimal shortExposure, BigDecimal drawdownPercent) {}
    public record Event(Instant time, String type, String strategyId, String version, String symbol,
                        Integer orderId, String signalId, String message, boolean critical) {}
    public record Check(String name, boolean passed, String reason) {}
    public record Connection(boolean connected, String host, int port, String account, List<String> managedAccounts,
                             boolean paperIdentityVerified, boolean writesVerified, boolean realtimeData,
                             String identityReason, Instant connectedAt, Instant heartbeat, long epoch) {}
    public record BrokerPosition(String account, String symbol, BigDecimal quantity, BigDecimal averageCost) {}
    public record BrokerOrder(int id, long permId, int parentId, String account, String symbol, String clientOrderId,
                              String action, String type, BigDecimal quantity, BigDecimal filled, String status,
                              BigDecimal stop, BigDecimal limit, String ocaGroup) {}
    public record BrokerSnapshot(long epoch, Instant time, List<BrokerPosition> positions, List<BrokerOrder> orders,
                                 List<ExecutionRecord> executions, List<CommissionRecord> commissions,
                                 BigDecimal availableFunds, boolean complete, String reason) {}
    public record Quote(BigDecimal open, BigDecimal last, BigDecimal bid, BigDecimal ask, Instant time,
                        LocalDate sessionDate, boolean realtime, BigDecimal shortableShares, BigDecimal shortMargin) {}
    public record Dashboard(Session session, Connection connection, List<Check> checks, EquityPoint capital,
                            Map<String,Object> performance, List<Event> events, Map<String,Instant> nextSessions,
                            boolean adminConfigured, List<String> limitations) {}
}
