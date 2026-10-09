package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.backtest.patternv2.*;

import it.andreademartini.trading.backtest.patternv1.*;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.*;

import it.andreademartini.trading.strategy.patternv1.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Frozen version 2.0 financial kernel, promoted from the verified V2 reference.
 * This snapshot is deliberately separate from the editable research implementation.
 * Accounting order, pattern recognition, sizing, entry and exit rules require a new version to change. */
public final class PatternPortfolioSimulator {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal DEFAULT_MAX_ORDER_VALUE_PERCENT = new BigDecimal("60");
    private final PatternV1PositionSizing sizing;
    private final List<PatternDetector> detectors;
    public PatternPortfolioSimulator(PositionSizingService sizing, List<PatternDetector> detectors) {
        this.sizing = new PatternV1PositionSizing(sizing);
        this.detectors = List.copyOf(detectors);
    }
    public record Simulation(PatternV1Result portfolio, List<String> equityTimestamps, List<PatternRangeAnalysis.SignalAudit> signals,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,List<CapitalSkipAudit> capitalSkipAudits, Map<String,it.andreademartini.trading.backtest.patternv2.PatternV2CapitalLimits.Audit> capitalLimitsAudits,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals) {}
    public Simulation run(PatternV2Configuration configuration, Map<String,List<HistoricalBar>> input) {
        return run(configuration,input,UUID.randomUUID().toString());
    }
    Simulation run(PatternV2Configuration configuration,Map<String,List<HistoricalBar>> input,String simulationId) {
        configuration.validate(detectors);
        var auditConfiguration=it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON.convertValue(configuration,it.andreademartini.trading.backtest.patternv2.PatternV2Configuration.class);
        var splitCosts = configuration.splitCosts()==null?new PatternV2SplitCosts(List.of()):configuration.splitCosts();
        var global=configuration.global();
        LocalDate from=global.startDate(), to=global.endDate();
        if(to==null) throw new IllegalArgumentException("Normalized end date required");
        var properties=new PatternV1Properties();
        properties.setMinQuantity(global.minQuantity());properties.setMinOrderValue(bd(global.minOrderValue()));
        properties.setMaxHoldingBars(global.maxHoldingBars());properties.setStopBufferTicks(global.stopBufferTicks());
        properties.setSmallQuantityWarningThreshold(global.smallQuantityWarningThreshold());
        BigDecimal initial=bd(global.initialCapital());
        PatternV1Commission commission=global.commissionEnabled()?global.commission():PatternV1Commission.fixed(BigDecimal.ZERO);
        BigDecimal slippage=global.slippageEnabled()?bd(global.slippagePercentPerSide()):BigDecimal.ZERO;
        var risk=riskFor(configuration,null,initial);
        Map<String,SymbolPatternConfiguration> mapping=new TreeMap<>();
        for(var s:configuration.symbols()) if(s.enabled()) mapping.put(s.symbol(),new SymbolPatternConfiguration(
            new DirectionPatternConfiguration(s.longConfig().enabled(),s.longConfig().pattern(),s.longConfig().targetR()),
            new DirectionPatternConfiguration(s.shortConfig().enabled(),s.shortConfig().pattern(),s.shortConfig().targetR())));
        Map<String, SymbolPatternConfiguration> configurations = new TreeMap<>(mapping);
        ConfiguredPatternStrategy strategy = new ConfiguredPatternStrategy(configuration, detectors);
        List<String> warnings = new ArrayList<>();
        Map<String, List<HistoricalBar>> series = new TreeMap<>();
        input.forEach((symbol, bars) -> {
            if (!configurations.containsKey(symbol)) { warnings.add(symbol + ": DISABLED_NOT_CORE"); return; }
            var filtered = bars.stream().filter(b -> !date(b).isBefore(from) && !date(b).isAfter(to))
                    .sorted(Comparator.comparing(HistoricalBar::timestamp)).toList();
            if (filtered.stream().anyMatch(b -> b.interval() != global.timeframe())) throw new IllegalArgumentException("Timeframe mismatch");
            if (filtered.stream().map(HistoricalBar::dateTime).distinct().count() != filtered.size())
                throw new IllegalArgumentException(symbol + ": duplicate daily dates");
            for (var b : filtered) if (b.getLow().signum() <= 0 || b.getHigh().compareTo(b.getLow()) < 0
                    || b.getOpen().compareTo(b.getLow()) < 0 || b.getOpen().compareTo(b.getHigh()) > 0
                    || b.getClose().compareTo(b.getLow()) < 0 || b.getClose().compareTo(b.getHigh()) > 0)
                throw new IllegalArgumentException(symbol + ": invalid OHLC " + date(b));
            if (filtered.isEmpty()) warnings.add(symbol + ": NO_DATA");
            else {
                if (date(filtered.getFirst()).isAfter(from.plusDays(7))) warnings.add(symbol + ": COVERAGE_START=" + date(filtered.getFirst()));
                if (date(filtered.getLast()).isBefore(to.minusDays(7))) warnings.add(symbol + ": COVERAGE_END=" + date(filtered.getLast()));
            }
            series.put(symbol, filtered);
        });
        String id = simulationId;
        String datasetId=PatternV2Dataset.capture(input).id();
        List<CapitalSkipAudit> capitalAudits=new ArrayList<>();
        Map<String,BigDecimal> signalEquities=new HashMap<>();
        TreeMap<java.time.LocalDateTime, Map<String, Integer>> timeline = new TreeMap<>();
        series.forEach((symbol, bars) -> {
            for (int i = 0; i < bars.size(); i++) timeline.computeIfAbsent(bars.get(i).dateTime(), ignored -> new TreeMap<>()).put(symbol, i);
        });
        List<Event> events = new ArrayList<>();
        List<PatternRangeAnalysis.SignalAudit> signalAudits = new ArrayList<>();
        List<CloseStrengthAnalysis.SignalAudit> closeAudits = new ArrayList<>();
        List<BollingerAtrAnalysis.SignalAudit> bollingerAudits=new ArrayList<>();
        Map<String,BollingerAtr> indicators=new HashMap<>();
        List<Trade> trades = new ArrayList<>();
        Map<String,it.andreademartini.trading.backtest.patternv2.PatternV2CapitalLimits.Audit> capitalLimitsAudits=new LinkedHashMap<>();
        List<EquityPoint> equityCurve = new ArrayList<>();
        Map<String, Position> positions = new TreeMap<>();
        Map<String, List<PatternV1Strategy.Signal>> pending = new TreeMap<>();
        Map<String, BigDecimal> lastPrices = new TreeMap<>();
        BigDecimal cash = initial, equity = initial, peak = initial;
        double maximumDrawdown = 0, maximumDrawdownPercent = 0, totalExposurePercent = 0;
        int nextNumber = 1, daysInMarket = 0;
        for (var day : timeline.entrySet()) {
            boolean inMarket = !positions.isEmpty();
            // Only known opens and earlier closes are visible to entry/sizing.
            Map<String, BigDecimal> openingPrices = new TreeMap<>(lastPrices);
            day.getValue().forEach((s, i) -> openingPrices.put(s, series.get(s).get(i).getOpen()));
            List<CapitalSkipAudit.Candidate> candidates=new ArrayList<>();
            for(var si:day.getValue().entrySet())for(var candidate:pending.getOrDefault(si.getKey(),List.of()))candidates.add(new CapitalSkipAudit.Candidate(candidates.size()+1,signalId(datasetId,candidate,series),candidate.symbol(),candidate.detection().direction().name(),candidate.detection().patternType().name(),candidate.detection().signalBarIndex(),"PENDING"));
            int entryEventsStart=events.size(),auditStart=capitalAudits.size(),processingOrder=0;
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                var bar = series.get(symbol).get(index);
                var signals = pending.remove(symbol); if (signals == null) continue;
                for (var signal : signals) {
                    processingOrder++;
                    if (positions.containsKey(symbol)) {
                        event(events, "SIGNAL_SKIPPED_POSITION_ALREADY_OPEN", null, signal, day.getKey().toLocalDate(), index, Map.of()); continue;
                    }
                    boolean isLong = signal.detection().direction() == PatternDirection.LONG;
                    if ((isLong && !risk.isAllowLong()) || (!isLong && !risk.isAllowShortSelling())) {
                        event(events, "SIGNAL_SKIPPED_DIRECTION_DISABLED", null, signal, day.getKey().toLocalDate(), index, Map.of()); continue;
                    }
                    PatternV1Strategy.EntryPlan plan;
                    try { plan = strategy.onNextOpen(signal, index, day.getKey().toLocalDate(), bar.getOpen()); }
                    catch (IllegalArgumentException invalid) {
                        event(events, "SIGNAL_SKIPPED_INVALID_RISK", null, signal, day.getKey().toLocalDate(), index, Map.of("reason", invalid.getMessage())); continue;
                    }
                    var equityAtEntry = markedEquity(cash, positions, openingPrices);
                    var available = availableCash(cash, positions);
                    var symbolConfig=configuration.symbols().stream().filter(s->s.symbol().equals(symbol)).findFirst().orElseThrow();
                    try { risk=riskFor(configuration,symbolConfig,equityAtEntry); }
                    catch(IllegalArgumentException invalid) { event(events,"SIGNAL_SKIPPED_INVALID_EQUITY",null,signal,day.getKey().toLocalDate(),index,Map.of("reason",invalid.getMessage())); continue; }
                    var entryFactor=splitCosts.factor(symbol,bar);
                    var entryCommission=PatternV2SplitCosts.commission(commission,entryFactor);
                    risk.setTransactionCostsPerShare(PatternV2SplitCosts.perShare(bd(global.transactionCostsPerShare()),entryFactor).doubleValue());
                    var decision = sizing.calculate(plan, equityAtEntry, available, risk, properties, entryCommission, slippage);
                    if (decision.quantity() == 0) {
                        if(decision.capitalConstrained()){
                            List<CapitalSkipAudit.OpenPosition> open=new ArrayList<>();
                            for(var position:positions.values()){var pp=position.plan;open.add(new CapitalSkipAudit.OpenPosition(position.id,pp.signal().symbol(),pp.signal().detection().direction().name(),position.quantity,pp.entryPrice(),pp.stopPrice(),openingPrices.get(pp.signal().symbol()),pp.riskPerShare().multiply(bd(position.quantity))));}
                            var context=new CapitalSkipAudit.Context(available,marketValue(positions,openingPrices,true).add(marketValue(positions,openingPrices,false)),equityAtEntry,positions.size(),open.stream().map(CapitalSkipAudit.OpenPosition::initialRisk).reduce(BigDecimal.ZERO,BigDecimal::add),List.copyOf(open),List.of(),processingOrder,decision.requiredCapital(),decision.requiredCapital().subtract(available).max(BigDecimal.ZERO));
                            var sid=signalId(datasetId,signal,series);capitalAudits.add(new CapitalSkipAudit(sid,signal,series.get(symbol).get(signal.detection().signalBarIndex()).timestamp().toString(),bar.timestamp().toString(),index,Objects.requireNonNull(signalEquities.get(sid)),"INSUFFICIENT_CAPITAL",context));
                        }
                        event(events, decision.capitalConstrained() ? "SIGNAL_SKIPPED_CAPITAL" : "SIGNAL_SKIPPED_SIZE", null, signal, day.getKey().toLocalDate(), index,
                                Map.of("requiredCapital", decision.requiredCapital(), "availableCapital", available, "desiredQuantity", decision.desiredQuantity(),
                                        "finalPossibleQuantity", decision.finalPossibleQuantity(), "quantityLimitingReason", decision.reason().name(),
                                        "minimumRejected", decision.minimumRejected())); continue;
                    }
                    var p = new Position(id + "-" + nextNumber, nextNumber++, plan, decision, symbolConfig.holding(global));
                    capitalLimitsAudits.put(p.id,PatternV2CapitalLimits.audit(new it.andreademartini.trading.backtest.patternv2.PatternV2CapitalLimits.Effective(bd(risk.getMaxOrderValue()),bd(risk.getMaxPositionValue()),bd(risk.getMaxLongNotional()),bd(risk.getMaxShortNotional())),decision,plan.entryPrice(),risk.getMaxQuantityPerOrder(),signal.detection().direction()));
                    positions.put(symbol, p); inMarket = true;
                    cash = cash.add(isLong ? decision.orderValue().negate() : decision.orderValue()).subtract(decision.entryCosts());
                    if (!risk.isUseLeverage() && (cash.signum() < 0 || availableCash(cash, positions).signum() < 0))
                        throw new IllegalStateException("NO_LEVERAGE cash/collateral invariant");
                    if (p.quantity <= properties.getSmallQuantityWarningThreshold())
                        warnings.add("SMALL_QUANTITY: " + p.id + " " + symbol + " qty=" + p.quantity + " reason=" + decision.reason());
                    if (decision.capitalConstrained() && decision.quantity() < decision.desiredQuantity() * .25)
                        warnings.add("SIZE_STRONGLY_CAPITAL_CONSTRAINED: " + p.id + " desired=" + decision.desiredQuantity() + " final=" + decision.quantity());
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("entryPrice", plan.entryPrice()); detail.put("quantity", p.quantity); detail.put("entryDate", day.getKey().toLocalDate());
                    detail.put("desiredQuantity", decision.desiredQuantity()); detail.put("finalQuantity", p.quantity);
                    detail.put("quantityLimitingReason", decision.reason().name()); detail.put("quantityLimitingReasons", decision.reasons().stream().map(Enum::name).toList());
                    detail.put("equityAtEntry", equityAtEntry); detail.put("orderValue", decision.orderValue());
                    detail.put("availableCashBeforeEntry", available); detail.put("availableCashAfterEntry", decision.availableAfter());
                    detail.put("desiredRiskAmount", decision.desiredRiskAmount()); detail.put("actualRiskAmount", decision.actualRiskAmount());
                    detail.put("desiredRiskPercent", decision.desiredRiskPercent()); detail.put("actualRiskPercent", decision.actualRiskPercent());
                    detail.put("sizeConstrained", decision.sizeConstrained());
                    detail.put("commission", entryCommission.charge(p.quantity, plan.entryPrice()));
                    detail.put("splitFactor", entryFactor);
                    detail.put("quantityExecutionEquivalent", PatternV2SplitCosts.equivalentQuantity(p.quantity,entryFactor));
                    detail.put("slippageCost", plan.entryPrice().multiply(slippage).divide(bd(100),MATH).multiply(bd(p.quantity)));
                    detail.put("entryCosts", decision.entryCosts());
                    event(events, "ENTRY", p.id, signal, day.getKey().toLocalDate(), index, detail);
                    event(events, "INITIAL_STOP", p.id, signal, day.getKey().toLocalDate(), index,
                            Map.of("stopPrice", plan.stopPrice(), "stopMethod", "PATTERN_EXTREME", "stopBufferTicks", global.stopBufferTicks(), "riskPerShare", plan.riskPerShare()));
                    event(events, "TAKE_PROFIT_CREATED", p.id, signal, day.getKey().toLocalDate(), index, Map.of("targetR", signal.targetR(),
                            "takeProfitPrice", plan.takeProfitPrice(), "formula", isLong ? "ENTRY + R * TARGET_R" : "ENTRY - R * TARGET_R"));
                    event(events, "TRADE_OPENED", p.id, signal, day.getKey().toLocalDate(), index, Map.of());
                }
            }
            List<CapitalSkipAudit.Candidate> resolved=new ArrayList<>();
            for(var candidate:candidates){String outcome="NOT_PROCESSED";for(int ei=entryEventsStart;ei<events.size();ei++){var e=events.get(ei);if(e.symbol().equals(candidate.symbol())&&e.direction().name().equals(candidate.direction())&&e.pattern().name().equals(candidate.pattern())&&Objects.equals(e.details().get("signalIndex"),Long.valueOf(candidate.signalIndex()))&&(e.eventType().equals("ENTRY")||e.eventType().startsWith("SIGNAL_SKIPPED"))){outcome=e.eventType();break;}}resolved.add(new CapitalSkipAudit.Candidate(candidate.processingOrder(),candidate.signalId(),candidate.symbol(),candidate.direction(),candidate.pattern(),candidate.signalIndex(),outcome));}
            for(int ai=auditStart;ai<capitalAudits.size();ai++)capitalAudits.set(ai,capitalAudits.get(ai).withCandidates(resolved));
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                var bar = series.get(symbol).get(index); lastPrices.put(symbol, bar.getClose());
                Position p = positions.get(symbol); if (p == null) continue;
                p.holdingBars++;
                var exitDecision=PatternV2BarRules.exit(p.plan,bar,p.holdingBars,p.maxHoldingBars,global.sameBarPolicy());
                boolean stop=exitDecision.stop(),target=exitDecision.target(),exitOnStop=exitDecision.exitOnStop();p.measure(bar,exitOnStop,target&&!exitOnStop);
                if(exitDecision.completed()){
                    p.ambiguous=stop&&target;String reason=exitDecision.reason();var exit=exitDecision.price();
                    var gross = pnl(p, exit);
                    var exitFactor=splitCosts.factor(symbol,bar);
                    var exitCommission = PatternV2SplitCosts.commission(commission,exitFactor).charge(p.quantity, exit);
                    var exitCosts = exitCommission.add(exit.multiply(slippage).divide(bd(100), MATH)
                            .add(bd(PatternV2SplitCosts.perShare(bd(global.transactionCostsPerShare()),exitFactor).doubleValue())).multiply(bd(p.quantity)));
                    var costs = p.entryCosts.add(exitCosts); var net = gross.subtract(costs);
                    var resultR = divide(p.isLong() ? exit.subtract(p.plan.entryPrice()) : p.plan.entryPrice().subtract(exit), p.plan.riskPerShare());
                    var researchR = exitOnStop ? BigDecimal.ONE.negate() : target ? p.plan.signal().targetR() : null;
                    if (p.ambiguous) event(events, "SAME_BAR_AMBIGUITY", p.id, p.plan.signal(), day.getKey().toLocalDate(), index,
                            Map.of("sameBarAmbiguous", true, "resolution", global.sameBarPolicy().name()));
                    event(events, exitOnStop ? "STOP_FILLED" : target ? "TAKE_PROFIT_FILLED" : "TIMEOUT", p.id, p.plan.signal(), day.getKey().toLocalDate(), index,
                            Map.of("exitPrice", exit, "resultR", resultR, "holdingBars", p.holdingBars, "closed", true, "remainingQuantity", 0,
                                    "timeoutExitMode", properties.getTimeoutExitMode().name()));
                    event(events, "TRADE_CLOSED", p.id, p.plan.signal(), day.getKey().toLocalDate(), index,
                            Map.of("exitReason", reason, "grossPnl", gross, "netPnl", net, "remainingQuantity", 0,
                                    "commission", exitCommission, "slippageCost", exit.multiply(slippage).divide(bd(100),MATH).multiply(bd(p.quantity)),
                                    "exitCosts", exitCosts, "totalCosts", costs));
                    trades.add(trade(p, index, day.getKey().toLocalDate(), exit, reason, gross, net, costs, resultR, researchR, true));
                    var notional = exit.multiply(bd(p.quantity));
                    cash = cash.add(p.isLong() ? notional : notional.negate()).subtract(exitCosts);
                    positions.remove(symbol);
                }
            }
            for (var p : positions.values()) if (p.quantity <= 0 || p.holdingBars >= p.maxHoldingBars)
                throw new IllegalStateException("OPEN holding/quantity invariant: " + p.id);
            if (!risk.isUseLeverage() && cash.signum() < 0) throw new IllegalStateException("Negative cash without leverage");
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                var indicator=global.timeframe()==BarInterval.DAILY?indicators.computeIfAbsent(symbol,ignored->new BollingerAtr(auditConfiguration)).onBar(series.get(symbol).get(index)):new BollingerAtr.Value(null,null,null,null);
                for (var signal : strategy.onClosedBar(symbol, series.get(symbol), index)) {
                    signalEquities.put(signalId(datasetId,signal,series),markedEquity(cash,positions,lastPrices));
                    event(events, "PATTERN_DETECTED", null, signal, day.getKey().toLocalDate(), index,
                            Map.of("patternDate", day.getKey().toLocalDate(), "patternLow", signal.patternLow(), "patternHigh", signal.patternHigh(),
                                    "patternClose", signal.patternClose(), "configuredTargetR", signal.targetR(), "patternStartIndex", signal.detection().patternStartBarIndex()));
                    var range = new PatternRange(signal.patternHigh(),signal.patternLow(),signal.patternClose());
                    boolean accepted = !configuration.patternRangeFilterEnabled() || range.reaches(configuration.minimumPatternRangePct());
                    signalAudits.add(new PatternRangeAnalysis.SignalAudit(symbol,signal.detection().direction().name(),signal.detection().patternType().name(),
                            signal.detection().signalDate(),series.get(symbol).get(index).timestamp().toString(),index,range,
                            configuration.patternRangeFilterEnabled(),configuration.minimumPatternRangePct(),accepted,accepted?null:"PATTERN_RANGE_FILTER"));
                    var closeAudit=CloseStrengthAnalysis.audit(signalAudits.getLast(),signal.targetR(),auditConfiguration);
                    closeAudits.add(closeAudit);
                    var bollingerAudit=BollingerAtrAnalysis.audit(signal,series.get(symbol),indicator,auditConfiguration,accepted&&closeAudit.status()!=CloseStrengthAnalysis.Status.REJECTED&&closeAudit.status()!=CloseStrengthAnalysis.Status.INVALID_DATA);
                    bollingerAudits.add(bollingerAudit);
                    if (!accepted) {
                        Map<String,Object> detail = new LinkedHashMap<>();
                        detail.put("rejectReason","PATTERN_RANGE_FILTER");detail.put("signalDate",signal.detection().signalDate());
                        detail.put("signalTimestamp",series.get(symbol).get(index).timestamp().toString());
                        detail.put("patternHigh",range.high());detail.put("patternLow",range.low());detail.put("patternClose",range.close());
                        detail.put("patternRangePct",range.percent());detail.put("minimumPatternRangePct",configuration.minimumPatternRangePct());
                        event(events,"REJECTED_PATTERN_RANGE",null,signal,day.getKey().toLocalDate(),index,detail);
                        continue;
                    }
                    if(closeAudit.status()==CloseStrengthAnalysis.Status.REJECTED||closeAudit.status()==CloseStrengthAnalysis.Status.INVALID_DATA) {
                        Map<String,Object> detail=new LinkedHashMap<>();
                        detail.put("rejectReason",closeAudit.rejectReason());detail.put("signalTimestamp",closeAudit.signalTimestamp());
                        detail.put("patternHigh",range.high());detail.put("patternLow",range.low());detail.put("patternClose",range.close());detail.put("patternRange",range.range());
                        if(closeAudit.closeStrength()!=null)detail.put("closeStrength",closeAudit.closeStrength());
                        detail.put("maxCloseStrength",configuration.maxCloseStrength());detail.put("closeStrengthStatus",closeAudit.status().name());
                        event(events,closeAudit.status()==CloseStrengthAnalysis.Status.REJECTED?"REJECTED_CLOSE_STRENGTH":"INVALID_CLOSE_STRENGTH_DATA",null,signal,day.getKey().toLocalDate(),index,detail);
                        continue;
                    }
                    if(bollingerAudit.rejectReason()!=null) {
                        Map<String,Object> detail=new LinkedHashMap<>();detail.put("rejectReason",bollingerAudit.rejectReason());detail.put("maxDistanceAtr",configuration.maxBollingerDistanceAtr());
                        detail.put("patternHigh",signal.patternHigh());detail.put("patternLow",signal.patternLow());detail.put("extremeTimestamp",bollingerAudit.extremeTimestamp());detail.put("extremePrice",bollingerAudit.extremePrice());
                        if(bollingerAudit.distanceAtr()!=null)detail.put("distanceAtr",bollingerAudit.distanceAtr());if(bollingerAudit.diagnostic()!=null)detail.put("diagnostic",bollingerAudit.diagnostic());
                        if(indicator.middle()!=null){detail.put("bollingerMiddle",indicator.middle());detail.put("bollingerUpper",indicator.upper());detail.put("bollingerLower",indicator.lower());}if(indicator.atr()!=null)detail.put("atr",indicator.atr());
                        event(events,bollingerAudit.rejectReason(),null,signal,day.getKey().toLocalDate(),index,detail);continue;
                    }
                    if (positions.containsKey(symbol)) event(events, "SIGNAL_SKIPPED_POSITION_ALREADY_OPEN", null, signal, day.getKey().toLocalDate(), index, Map.of());
                    else {
                        event(events, "SIGNAL", null, signal, day.getKey().toLocalDate(), index, Map.of("entryMethod", "NEXT_BAR_OPEN"));
                        if (index + 1 == series.get(symbol).size()) event(events, "SIGNAL_SKIPPED_NO_NEXT_BAR", null, signal, day.getKey().toLocalDate(), index, Map.of());
                        else pending.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(signal);
                    }
                }
            }
            var longValue = marketValue(positions, lastPrices, true); var shortValue = marketValue(positions, lastPrices, false);
            equity = cash.add(longValue).subtract(shortValue); peak = peak.max(equity);
            var drawdown = peak.subtract(equity); var grossExposure = longValue.add(shortValue);
            double ddPercent = percent(drawdown, peak), exposurePercent = percent(grossExposure, equity);
            maximumDrawdown = Math.max(maximumDrawdown, drawdown.doubleValue()); maximumDrawdownPercent = Math.max(maximumDrawdownPercent, ddPercent);
            totalExposurePercent += exposurePercent; if (inMarket) daysInMarket++;
            equityCurve.add(new EquityPoint(day.getKey().toLocalDate(), equity, drawdown, ddPercent, positions.size(), exposurePercent / 100,
                    cash, availableCash(cash, positions), longValue, shortValue, grossExposure, longValue.subtract(shortValue), peak, exposurePercent, inMarket));
        }
        for (var p : positions.values()) {
            var bars = series.get(p.plan.signal().symbol());
            trades.add(trade(p, bars.size() - 1, date(bars.getLast()), lastPrices.get(p.plan.signal().symbol()), "OPEN",
                    BigDecimal.ZERO, BigDecimal.ZERO, p.entryCosts, null, null, false));
        }
        trades.sort(Comparator.comparingInt(Trade::number));
        BigDecimal reconciled = initial;
        for (var t : trades) { PatternV1PortfolioEngine.validateTrade(t, configuration.symbols().stream().filter(s->s.symbol().equals(t.symbol())).findFirst().orElseThrow().holding(global)); reconciled = reconciled.add(t.closed() ? t.netPnl() : t.netUnrealizedPnl()); }
        if (equity.compareTo(reconciled) != 0) throw new IllegalStateException("Equity / realized + unrealized reconciliation failed");
        var dates=new TreeSet<LocalDate>();timeline.keySet().forEach(t->dates.add(t.toLocalDate()));
        var symbolResults = new ArrayList<SymbolResult>();
        for(var e:configurations.entrySet()) for(var d:PatternDirection.values()) {
            var c=e.getValue().direction(d);if(c==null||!c.enabled())continue;
            var group=trades.stream().filter(t->t.symbol().equals(e.getKey())&&t.direction()==d).toList();
            double dd=attributionDrawdown(group,series,dates);
            symbolResults.add(new SymbolResult(e.getKey(),d,c.pattern(),c.targetR(),PatternV1MetricsCalculator.calculate(group,events.stream().filter(v->v.symbol().equals(e.getKey())&&v.direction()==d).toList(),dd),dd,null,null,null,List.of()));
        }
        symbolResults.forEach(row -> row.warnings().forEach(w -> warnings.add(row.symbol() + " " + row.direction() + ": " + w)));
        var summary = PatternV1MetricsCalculator.calculate(trades, events, maximumDrawdown);
        if (summary.signalsDetected() > 0 && summary.skippedCapital() > summary.signalsDetected() * .25)
            warnings.add("HIGH_CAPITAL_SKIPS: " + summary.skippedCapital() + "/" + summary.signalsDetected());
        warnings.add("Holding bar 1 is the entry bar. TIMEOUT uses global/symbol holding limits; incomplete windows remain OPEN.");
        warnings.add("Same-bar policy: " + global.sameBarPolicy() + "; raw exit-candle excursions reported separately.");
        warnings.add("Alphabetical entry priority; intrabar exit proceeds fund orders from the next session. Entry equity uses known opens, never today's Close.");
        warnings.add("PF PnL uses realized gross closed-trade PnL; PF net is separate. Win rate uses net wins/(wins+losses), breakevens excluded. Price R excludes costs.");
        if(configuration.splitCosts()!=null) warnings.add("Split-aware per-share costs: quantity remains in the dataset share basis; execution-equivalent quantity = dataset quantity / factor on each execution date. Minimum/fixed fee and percentage slippage are unchanged. This does not undo dividend adjustments or model whole-share historical lots.");
        Map<String, List<ChartBar>> chartSeries = new TreeMap<>();
        series.forEach((symbol, bars) -> {
            List<ChartBar> values = new ArrayList<>();
            for (int i = 0; i < bars.size(); i++) { var b = bars.get(i); values.add(new ChartBar(i, b.timestamp().toString(), b.label(), b.open(), b.high(), b.low(), b.close(), b.volume())); }
            chartSeries.put(symbol, values);
        });
        LocalDate actualStart = timeline.isEmpty() ? null : timeline.firstKey().toLocalDate(), actualEnd = timeline.isEmpty() ? null : timeline.lastKey().toLocalDate();
        Map<PatternDirection, Metrics> directions = new EnumMap<>(PatternDirection.class);
        for (var direction : PatternDirection.values()) {
            var group = trades.stream().filter(t -> t.direction() == direction).toList();
            directions.put(direction, PatternV1MetricsCalculator.calculate(group, events.stream().filter(e -> e.direction() == direction).toList(),
                    attributionDrawdown(group, series, dates)));
        }
        var mode = properties.getTimeoutExitMode().name();
        risk=riskFor(configuration,null,initial);
        RunSettings settings = new RunSettings(global.timeframe().name(), global.entryMode().name(), global.stopMode().name(), global.stopBufferTicks(), global.maxHoldingBars(), global.sameBarPolicy().name(), mode,
                PatternDetectorParameters.defaults(), risk.getRiskPerTradePercent(), risk.isUseLeverage(), risk.getMaxLongNotional(), risk.getMaxShortNotional(),
                risk.getMaxOrderValue(), risk.getMaxPositionValue(), risk.getMaxQuantityPerOrder(), commission.fixedPerOrder(), slippage, bd(risk.getTransactionCostsPerShare()),
                mode, "ENTRY_BAR_IS_HOLDING_BAR_1", properties.getMinQuantity(), properties.getMinOrderValue(), properties.getSmallQuantityWarningThreshold(),
                "Cash includes short proceeds; proceeds + 100% initial short margin restricted; equity=cash+longMV-shortMV",
                "Mean of EOD sum(abs(current market value))/current equity across available trading dates",
                "Trading dates with any position during the session / all available trading dates", commission);
        var finalLong = marketValue(positions, lastPrices, true); var finalShort = marketValue(positions, lastPrices, false);
        double averageExposure = timeline.isEmpty() ? 0 : totalExposurePercent / timeline.size();
        return new Simulation(new PatternV1Result(id, "PATTERN_V1", from, to, actualStart, actualEnd, initial, equity, percent(equity.subtract(initial), initial),
                PatternV1MetricsCalculator.cagr(initial.doubleValue(), equity.doubleValue(), actualStart, actualEnd),
                maximumDrawdown, maximumDrawdownPercent, averageExposure / 100, positions.size(), summary, directions,
                List.copyOf(symbolResults), List.copyOf(trades), List.copyOf(equityCurve), List.copyOf(events), Map.copyOf(configurations), Map.copyOf(chartSeries),
                settings, List.copyOf(warnings), 2, cash, availableCash(cash, positions), finalLong, finalShort, finalLong.subtract(finalShort),
                averageExposure, timeline.isEmpty() ? 0 : daysInMarket * 100.0 / timeline.size()), timeline.keySet().stream().map(t->t.toInstant(java.time.ZoneOffset.UTC).toString()).toList(), List.copyOf(signalAudits),List.copyOf(closeAudits),List.copyOf(capitalAudits),Map.copyOf(capitalLimitsAudits),List.copyOf(bollingerAudits));
    }
    private static double attributionDrawdown(List<Trade> trades, Map<String, List<HistoricalBar>> series, Set<LocalDate> dates) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
        for (var t : trades) closes.computeIfAbsent(t.symbol(), symbol -> {
            var prices = new TreeMap<LocalDate, BigDecimal>(); series.get(symbol).forEach(b -> prices.put(date(b), b.getClose())); return prices;
        });
        double max = 0, peak = 0;
        for (var day : dates) {
            double pnl = 0;
            for (var t : trades) {
                if (day.isBefore(t.entryDate())) continue;
                if (t.closed() && !day.isBefore(t.exitDate())) pnl += t.netPnl().doubleValue();
                else {
                    double price = closes.get(t.symbol()).floorEntry(day).getValue().doubleValue();
                    pnl += (t.direction() == PatternDirection.LONG ? price - t.entryPrice().doubleValue() : t.entryPrice().doubleValue() - price)
                            * t.quantity() - t.entryCosts().doubleValue();
                }
            }
            peak = Math.max(peak, pnl); max = Math.max(max, peak - pnl);
        }
        return max;
    }
    private static void event(List<Event> events, String type, String tradeId, PatternV1Strategy.Signal signal, LocalDate date, int index, Map<String, Object> details) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("signalIndex",(long)signal.detection().signalBarIndex());
        details.forEach((key, value) -> normalized.put(key, value instanceof LocalDate d ? d.toString()
                : value instanceof Integer i ? i.longValue()
                : value instanceof BigDecimal b && b.scale() <= 0 ? b.setScale(1) : value));
        events.add(new Event(type, tradeId, signal.symbol(), signal.detection().patternType(), signal.detection().direction(), date, index, Map.copyOf(normalized)));
    }
    private static BigDecimal availableCash(BigDecimal cash, Map<String, Position> positions) {
        BigDecimal restricted = BigDecimal.ZERO;
        for (var p : positions.values()) if (!p.isLong()) restricted = restricted.add(p.audit.orderValue().multiply(bd(2)));
        return cash.subtract(restricted);
    }
    private static BigDecimal marketValue(Map<String, Position> positions, Map<String, BigDecimal> prices, boolean isLong) {
        BigDecimal value = BigDecimal.ZERO;
        for (var p : positions.values()) if (p.isLong() == isLong) value = value.add(prices.get(p.plan.signal().symbol()).multiply(bd(p.quantity)));
        return value;
    }
    private static BigDecimal markedEquity(BigDecimal cash, Map<String, Position> positions, Map<String, BigDecimal> prices) {
        return cash.add(marketValue(positions, prices, true)).subtract(marketValue(positions, prices, false));
    }
    private static BigDecimal pnl(Position p, BigDecimal price) {
        return (p.isLong() ? price.subtract(p.plan.entryPrice()) : p.plan.entryPrice().subtract(price)).multiply(bd(p.quantity));
    }
    private static Trade trade(Position p, int lastIndex, LocalDate lastDate, BigDecimal lastPrice, String reason,
            BigDecimal gross, BigDecimal net, BigDecimal costs, BigDecimal resultR, BigDecimal researchR, boolean closed) {
        var plan = p.plan; var signal = plan.signal(); var d = signal.detection(); var a = p.audit;
        BigDecimal unrealized = closed ? BigDecimal.ZERO : pnl(p, lastPrice);
        BigDecimal netUnrealized = closed ? BigDecimal.ZERO : unrealized.subtract(p.entryCosts);
        var mfeR = divide(p.mfe, plan.riskPerShare()); var maeR = divide(p.mae, plan.riskPerShare());
        return new Trade(p.id, p.number, signal.symbol(), "PATTERN_V1", d.patternType(), d.direction(), d.signalDate(), plan.entryDate(), closed ? lastDate : null,
                d.patternStartBarIndex(), d.signalBarIndex(), plan.entryIndex(), closed ? lastIndex : -1, signal.patternLow(), signal.patternHigh(), signal.patternClose(),
                plan.entryPrice(), plan.stopPrice(), plan.takeProfitPrice(), signal.targetR(), plan.riskPerShare(), p.quantity, closed ? lastPrice : null, reason,
                gross, net, p.entryCosts, costs, resultR, researchR, p.mfe, p.mae, mfeR, maeR, p.holdingBars, p.ambiguous, closed,
                closed ? 0 : p.quantity, a.desiredQuantity(), p.quantity, a.reason(), a.reasons(), a.sizeConstrained(), a.equity(), a.availableBefore(), a.availableAfter(),
                a.orderValue(), a.desiredRiskAmount(), a.desiredRiskPercent(), a.actualRiskAmount(), a.actualRiskPercent(), net, unrealized, netUnrealized,
                mfeR, maeR, divide(p.rawMfe, plan.riskPerShare()), divide(p.rawMae, plan.riskPerShare()),
                closed ? null : lastDate, closed ? null : lastPrice, closed ? -1 : lastIndex, "TIMEOUT".equals(reason) ? lastPrice : null);
    }
    static TradingSystemProperties.RiskProperties riskFor(PatternV2Configuration config, PatternV2Configuration.Symbol symbol, BigDecimal equity) {
        var g=config.global();var r=new TradingSystemProperties.RiskProperties();
        r.setTradingCapital(g.initialCapital());r.setRiskPerTradePercent(symbol!=null&&symbol.riskPercent()!=null?symbol.riskPercent():g.riskPercent());
        var effective=PatternV2CapitalLimits.effective(config,symbol,equity);
        r.setMaxOrderValue(effective.maxOrderValue().doubleValue());
        r.setMaxQuantityPerOrder(symbol!=null&&symbol.maxQuantity()!=null?symbol.maxQuantity():g.maxQuantity());
        r.setMaxPositionValue(effective.maxPositionValue().doubleValue());r.setMaxLongNotional(effective.maxLongNotional().doubleValue());r.setMaxShortNotional(effective.maxShortNotional().doubleValue());
        r.setUseLeverage(g.useLeverage());r.setAllowLong(g.allowLong());r.setAllowShortSelling(g.allowShort());r.setTransactionCostsPerShare(g.transactionCostsPerShare());
        return r;
    }
    private static String signalId(String datasetId,PatternV1Strategy.Signal signal,Map<String,List<HistoricalBar>> series){var d=signal.detection();var key=datasetId+"|"+signal.symbol()+"|"+d.direction()+"|"+d.patternType()+"|"+signal.targetR().stripTrailingZeros()+"|"+series.get(signal.symbol()).get(d.signalBarIndex()).timestamp();return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private static LocalDate date(HistoricalBar bar) { return bar.dateTime().toLocalDate(); }
    private static BigDecimal bd(double value) { return BigDecimal.valueOf(value); }
    private static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
    private static BigDecimal divide(BigDecimal value, BigDecimal denominator) { return value.divide(denominator, 12, RoundingMode.HALF_UP); }
    private static double percent(BigDecimal value, BigDecimal denominator) { return denominator.signum() > 0 ? value.doubleValue() / denominator.doubleValue() * 100 : 0; }
    private static final class Position {
        final String id; final int number; final PatternV1Strategy.EntryPlan plan; final long quantity; final BigDecimal entryCosts;
        final PatternV1PositionSizing.Decision audit;
        BigDecimal mfe = BigDecimal.ZERO, mae = BigDecimal.ZERO, rawMfe = BigDecimal.ZERO, rawMae = BigDecimal.ZERO;
        int holdingBars; boolean ambiguous; final int maxHoldingBars;
        Position(String id, int number, PatternV1Strategy.EntryPlan plan, PatternV1PositionSizing.Decision audit, int maxHoldingBars) {
            this.maxHoldingBars=maxHoldingBars;
            this.id = id; this.number = number; this.plan = plan; this.audit = audit; this.quantity = audit.quantity(); this.entryCosts = audit.entryCosts();
        }
        boolean isLong() { return plan.signal().detection().direction() == PatternDirection.LONG; }
        BigDecimal favorable(BigDecimal price) { return (isLong() ? price.subtract(plan.entryPrice()) : plan.entryPrice().subtract(price)).max(BigDecimal.ZERO); }
        BigDecimal adverse(BigDecimal price) { return (isLong() ? plan.entryPrice().subtract(price) : price.subtract(plan.entryPrice())).max(BigDecimal.ZERO); }
        void measure(HistoricalBar bar, boolean stop, boolean target) {
            var values=new PatternV2BarRules.Excursions();values.mfe=mfe;values.mae=mae;values.rawMfe=rawMfe;values.rawMae=rawMae;
            values.measure(plan,bar,stop,target);mfe=values.mfe;mae=values.mae;rawMfe=values.rawMfe;rawMae=values.rawMae;
        }
    }
}
