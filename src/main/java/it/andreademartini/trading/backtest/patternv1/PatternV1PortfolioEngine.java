package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.strategy.patternv1.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** All symbols' opens precede today's intrabar exits and closed-bar detection. */
public final class PatternV1PortfolioEngine {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal DEFAULT_MAX_ORDER_VALUE_PERCENT = new BigDecimal("60");
    private final PatternV1PositionSizing sizing;
    private final List<PatternDetector> detectors;
    public PatternV1PortfolioEngine(PositionSizingService sizing, List<PatternDetector> detectors) {
        this.sizing = new PatternV1PositionSizing(sizing);
        this.detectors = List.copyOf(detectors);
    }
    public PatternV1Result run(PatternV1Request request, PatternV1Properties properties,
            TradingSystemProperties.RiskProperties risk, Map<String, List<HistoricalBar>> input) {
        properties.validate();
        if (!properties.isEnabled()) throw new IllegalArgumentException("PATTERN_V1 disabled");
        if (request.timeframe() != null && request.timeframe() != BarInterval.DAILY) throw new IllegalArgumentException("DAILY only");
        LocalDate from = request.startDate() == null ? LocalDate.of(2010, 1, 1) : request.startDate();
        LocalDate to = request.endDate() == null ? LocalDate.now().minusDays(1) : request.endDate();
        if (to.isBefore(from)) throw new IllegalArgumentException("Invalid date range");
        BigDecimal initial = request.initialCapital() == null ? bd(risk.getTradingCapital()) : request.initialCapital();
        PatternV1Commission commission = request.commission() != null ? request.commission()
                : request.commissionPerOrder() != null ? PatternV1Commission.fixed(request.commissionPerOrder()) : PatternV1Commission.ibkr();
        BigDecimal slippage = request.slippagePercent() == null ? new BigDecimal("0.03") : request.slippagePercent();
        if (initial.signum() <= 0 || slippage.signum() < 0 || slippage.compareTo(bd(100)) >= 0
                || risk.getTransactionCostsPerShare() < 0) throw new IllegalArgumentException("Invalid capital/costs");
        // Keep the order cap fixed for this run without changing shared trading settings.
        risk = PatternV1BacktestService.riskSnapshot(risk);
        if (request.maxOrderValue() != null
                && (!Double.isFinite(request.maxOrderValue()) || request.maxOrderValue() <= 0))
            throw new IllegalArgumentException("maxOrderValue must be finite and positive");
        if (request.maxQuantity() != null && request.maxQuantity() < 1)
            throw new IllegalArgumentException("maxQuantity must be at least 1");
        risk.setMaxOrderValue(request.maxOrderValue() == null
                ? initial.multiply(DEFAULT_MAX_ORDER_VALUE_PERCENT.movePointLeft(2)).doubleValue() : request.maxOrderValue());
        risk.setMaxQuantityPerOrder(request.maxQuantity() == null ? 1_000_000L : request.maxQuantity());
        Map<String, SymbolPatternConfiguration> configurations = new TreeMap<>(properties.getSymbols());
        PatternV1Strategy strategy = new PatternV1Strategy(configurations, detectors);
        List<String> warnings = new ArrayList<>();
        Map<String, List<HistoricalBar>> series = new TreeMap<>();
        input.forEach((symbol, bars) -> {
            if (!configurations.containsKey(symbol)) { warnings.add(symbol + ": DISABLED_NOT_CORE"); return; }
            var filtered = bars.stream().filter(b -> !date(b).isBefore(from) && !date(b).isAfter(to))
                    .sorted(Comparator.comparing(HistoricalBar::timestamp)).toList();
            if (filtered.stream().anyMatch(b -> b.interval() != BarInterval.DAILY)) throw new IllegalArgumentException("DAILY only");
            if (filtered.stream().map(PatternV1PortfolioEngine::date).distinct().count() != filtered.size())
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
        String id = UUID.randomUUID().toString();
        TreeMap<LocalDate, Map<String, Integer>> timeline = new TreeMap<>();
        series.forEach((symbol, bars) -> {
            for (int i = 0; i < bars.size(); i++) timeline.computeIfAbsent(date(bars.get(i)), ignored -> new TreeMap<>()).put(symbol, i);
        });
        List<Event> events = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();
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
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                var bar = series.get(symbol).get(index);
                var signals = pending.remove(symbol); if (signals == null) continue;
                for (var signal : signals) {
                    if (positions.containsKey(symbol)) {
                        event(events, "SIGNAL_SKIPPED_POSITION_ALREADY_OPEN", null, signal, day.getKey(), index, Map.of()); continue;
                    }
                    boolean isLong = signal.detection().direction() == PatternDirection.LONG;
                    if ((isLong && !risk.isAllowLong()) || (!isLong && !risk.isAllowShortSelling())) {
                        event(events, "SIGNAL_SKIPPED_DIRECTION_DISABLED", null, signal, day.getKey(), index, Map.of()); continue;
                    }
                    PatternV1Strategy.EntryPlan plan;
                    try { plan = strategy.onNextOpen(signal, index, day.getKey(), bar.getOpen()); }
                    catch (IllegalArgumentException invalid) {
                        event(events, "SIGNAL_SKIPPED_INVALID_RISK", null, signal, day.getKey(), index, Map.of("reason", invalid.getMessage())); continue;
                    }
                    var equityAtEntry = markedEquity(cash, positions, openingPrices);
                    var available = availableCash(cash, positions);
                    var decision = sizing.calculate(plan, equityAtEntry, available, risk, properties, commission, slippage);
                    if (decision.quantity() == 0) {
                        event(events, decision.capitalConstrained() ? "SIGNAL_SKIPPED_CAPITAL" : "SIGNAL_SKIPPED_SIZE", null, signal, day.getKey(), index,
                                Map.of("requiredCapital", decision.requiredCapital(), "availableCapital", available, "desiredQuantity", decision.desiredQuantity(),
                                        "finalPossibleQuantity", decision.finalPossibleQuantity(), "quantityLimitingReason", decision.reason().name(),
                                        "minimumRejected", decision.minimumRejected())); continue;
                    }
                    var p = new Position(id + "-" + nextNumber, nextNumber++, plan, decision);
                    positions.put(symbol, p); inMarket = true;
                    cash = cash.add(isLong ? decision.orderValue().negate() : decision.orderValue()).subtract(decision.entryCosts());
                    if (!risk.isUseLeverage() && (cash.signum() < 0 || availableCash(cash, positions).signum() < 0))
                        throw new IllegalStateException("NO_LEVERAGE cash/collateral invariant");
                    if (p.quantity <= properties.getSmallQuantityWarningThreshold())
                        warnings.add("SMALL_QUANTITY: " + p.id + " " + symbol + " qty=" + p.quantity + " reason=" + decision.reason());
                    if (decision.capitalConstrained() && decision.quantity() < decision.desiredQuantity() * .25)
                        warnings.add("SIZE_STRONGLY_CAPITAL_CONSTRAINED: " + p.id + " desired=" + decision.desiredQuantity() + " final=" + decision.quantity());
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("entryPrice", plan.entryPrice()); detail.put("quantity", p.quantity); detail.put("entryDate", day.getKey());
                    detail.put("desiredQuantity", decision.desiredQuantity()); detail.put("finalQuantity", p.quantity);
                    detail.put("quantityLimitingReason", decision.reason().name()); detail.put("quantityLimitingReasons", decision.reasons().stream().map(Enum::name).toList());
                    detail.put("equityAtEntry", equityAtEntry); detail.put("orderValue", decision.orderValue());
                    detail.put("availableCashBeforeEntry", available); detail.put("availableCashAfterEntry", decision.availableAfter());
                    detail.put("desiredRiskAmount", decision.desiredRiskAmount()); detail.put("actualRiskAmount", decision.actualRiskAmount());
                    detail.put("desiredRiskPercent", decision.desiredRiskPercent()); detail.put("actualRiskPercent", decision.actualRiskPercent());
                    detail.put("sizeConstrained", decision.sizeConstrained());
                    detail.put("commission", commission.charge(p.quantity, plan.entryPrice()));
                    detail.put("slippageCost", decision.orderValue().multiply(slippage.movePointLeft(2)));
                    detail.put("entryCosts", decision.entryCosts());
                    event(events, "ENTRY", p.id, signal, day.getKey(), index, detail);
                    event(events, "INITIAL_STOP", p.id, signal, day.getKey(), index,
                            Map.of("stopPrice", plan.stopPrice(), "stopMethod", "PATTERN_EXTREME", "stopBufferTicks", 0, "riskPerShare", plan.riskPerShare()));
                    event(events, "TAKE_PROFIT_CREATED", p.id, signal, day.getKey(), index, Map.of("targetR", signal.targetR(),
                            "takeProfitPrice", plan.takeProfitPrice(), "formula", isLong ? "ENTRY + R * TARGET_R" : "ENTRY - R * TARGET_R"));
                    event(events, "TRADE_OPENED", p.id, signal, day.getKey(), index, Map.of());
                }
            }
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                var bar = series.get(symbol).get(index); lastPrices.put(symbol, bar.getClose());
                Position p = positions.get(symbol); if (p == null) continue;
                p.holdingBars++;
                boolean stop = p.isLong() ? bar.getLow().compareTo(p.plan.stopPrice()) <= 0 : bar.getHigh().compareTo(p.plan.stopPrice()) >= 0;
                boolean target = p.isLong() ? bar.getHigh().compareTo(p.plan.takeProfitPrice()) >= 0 : bar.getLow().compareTo(p.plan.takeProfitPrice()) <= 0;
                p.measure(bar, stop, target);
                if (stop || target || p.holdingBars >= properties.getMaxHoldingBars()) {
                    p.ambiguous = stop && target;
                    String reason = stop ? "STOP" : target ? "TARGET" : "TIMEOUT";
                    var exit = stop ? p.plan.stopPrice() : target ? p.plan.takeProfitPrice() : bar.getClose();
                    var gross = pnl(p, exit);
                    var exitCommission = commission.charge(p.quantity, exit);
                    var exitCosts = exitCommission.add(exit.multiply(slippage).divide(bd(100), MATH)
                            .add(bd(risk.getTransactionCostsPerShare())).multiply(bd(p.quantity)));
                    var costs = p.entryCosts.add(exitCosts); var net = gross.subtract(costs);
                    var resultR = divide(p.isLong() ? exit.subtract(p.plan.entryPrice()) : p.plan.entryPrice().subtract(exit), p.plan.riskPerShare());
                    var researchR = stop ? BigDecimal.ONE.negate() : target ? p.plan.signal().targetR() : null;
                    if (p.ambiguous) event(events, "SAME_BAR_AMBIGUITY", p.id, p.plan.signal(), day.getKey(), index,
                            Map.of("sameBarAmbiguous", true, "resolution", "STOP_FIRST"));
                    event(events, stop ? "STOP_FILLED" : target ? "TAKE_PROFIT_FILLED" : "TIMEOUT", p.id, p.plan.signal(), day.getKey(), index,
                            Map.of("exitPrice", exit, "resultR", resultR, "holdingBars", p.holdingBars, "closed", true, "remainingQuantity", 0,
                                    "timeoutExitMode", properties.getTimeoutExitMode().name()));
                    event(events, "TRADE_CLOSED", p.id, p.plan.signal(), day.getKey(), index,
                            Map.of("exitReason", reason, "grossPnl", gross, "netPnl", net, "remainingQuantity", 0,
                                    "commission", exitCommission, "slippageCost", exit.multiply(bd(p.quantity)).multiply(slippage.movePointLeft(2)),
                                    "exitCosts", exitCosts, "totalCosts", costs));
                    trades.add(trade(p, index, day.getKey(), exit, reason, gross, net, costs, resultR, researchR, true));
                    var notional = exit.multiply(bd(p.quantity));
                    cash = cash.add(p.isLong() ? notional : notional.negate()).subtract(exitCosts);
                    positions.remove(symbol);
                }
            }
            for (var p : positions.values()) if (p.quantity <= 0 || p.holdingBars >= properties.getMaxHoldingBars())
                throw new IllegalStateException("OPEN holding/quantity invariant: " + p.id);
            if (!risk.isUseLeverage() && cash.signum() < 0) throw new IllegalStateException("Negative cash without leverage");
            for (var symbolIndex : day.getValue().entrySet()) {
                String symbol = symbolIndex.getKey(); int index = symbolIndex.getValue();
                for (var signal : strategy.onClosedBar(symbol, series.get(symbol), index)) {
                    event(events, "PATTERN_DETECTED", null, signal, day.getKey(), index,
                            Map.of("patternDate", day.getKey(), "patternLow", signal.patternLow(), "patternHigh", signal.patternHigh(),
                                    "patternClose", signal.patternClose(), "configuredTargetR", signal.targetR(), "patternStartIndex", signal.detection().patternStartBarIndex()));
                    if (positions.containsKey(symbol)) event(events, "SIGNAL_SKIPPED_POSITION_ALREADY_OPEN", null, signal, day.getKey(), index, Map.of());
                    else {
                        event(events, "SIGNAL", null, signal, day.getKey(), index, Map.of("entryMethod", "NEXT_BAR_OPEN"));
                        if (index + 1 == series.get(symbol).size()) event(events, "SIGNAL_SKIPPED_NO_NEXT_BAR", null, signal, day.getKey(), index, Map.of());
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
            equityCurve.add(new EquityPoint(day.getKey(), equity, drawdown, ddPercent, positions.size(), exposurePercent / 100,
                    cash, availableCash(cash, positions), longValue, shortValue, grossExposure, longValue.subtract(shortValue), peak, exposurePercent, inMarket));
        }
        for (var p : positions.values()) {
            var bars = series.get(p.plan.signal().symbol());
            trades.add(trade(p, bars.size() - 1, date(bars.getLast()), lastPrices.get(p.plan.signal().symbol()), "OPEN",
                    BigDecimal.ZERO, BigDecimal.ZERO, p.entryCosts, null, null, false));
        }
        trades.sort(Comparator.comparingInt(Trade::number));
        BigDecimal reconciled = initial;
        for (var t : trades) { validateTrade(t, properties.getMaxHoldingBars()); reconciled = reconciled.add(t.closed() ? t.netPnl() : t.netUnrealizedPnl()); }
        if (equity.compareTo(reconciled) != 0) throw new IllegalStateException("Equity / realized + unrealized reconciliation failed");
        var symbolResults = comparison(series, configurations, trades, events, from, to, timeline.keySet());
        symbolResults.forEach(row -> row.warnings().forEach(w -> warnings.add(row.symbol() + " " + row.direction() + ": " + w)));
        var summary = PatternV1MetricsCalculator.calculate(trades, events, maximumDrawdown);
        if (summary.signalsDetected() > 0 && summary.skippedCapital() > summary.signalsDetected() * .25)
            warnings.add("HIGH_CAPITAL_SKIPS: " + summary.skippedCapital() + "/" + summary.signalsDetected());
        warnings.add("Holding bar 1 is the entry bar, as Pattern Research; TIMEOUT closes at bar 20 Close. Only incomplete final windows remain OPEN.");
        warnings.add("STOP_FIRST executable excursions use earlier bars, the known open and the fill; raw exit-candle excursions are reported separately.");
        warnings.add("Alphabetical entry priority; intrabar exit proceeds fund orders from the next session. Entry equity uses known opens, never today's Close.");
        warnings.add("PF PnL uses realized gross closed-trade PnL; PF net is separate. Win rate uses net wins/(wins+losses), breakevens excluded. Price R excludes costs.");
        Map<String, List<ChartBar>> chartSeries = new TreeMap<>();
        series.forEach((symbol, bars) -> {
            List<ChartBar> values = new ArrayList<>();
            for (int i = 0; i < bars.size(); i++) { var b = bars.get(i); values.add(new ChartBar(i, b.timestamp().toString(), b.label(), b.open(), b.high(), b.low(), b.close(), b.volume())); }
            chartSeries.put(symbol, values);
        });
        LocalDate actualStart = timeline.isEmpty() ? null : timeline.firstKey(), actualEnd = timeline.isEmpty() ? null : timeline.lastKey();
        Map<PatternDirection, Metrics> directions = new EnumMap<>(PatternDirection.class);
        for (var direction : PatternDirection.values()) {
            var group = trades.stream().filter(t -> t.direction() == direction).toList();
            directions.put(direction, PatternV1MetricsCalculator.calculate(group, events.stream().filter(e -> e.direction() == direction).toList(),
                    attributionDrawdown(group, series, timeline.keySet())));
        }
        var mode = properties.getTimeoutExitMode().name();
        RunSettings settings = new RunSettings("DAILY", "NEXT_BAR_OPEN", "PATTERN_EXTREME", 0, 20, "STOP_FIRST", mode,
                PatternDetectorParameters.defaults(), risk.getRiskPerTradePercent(), risk.isUseLeverage(), risk.getMaxLongNotional(), risk.getMaxShortNotional(),
                risk.getMaxOrderValue(), risk.getMaxPositionValue(), risk.getMaxQuantityPerOrder(), commission.fixedPerOrder(), slippage, bd(risk.getTransactionCostsPerShare()),
                mode, "ENTRY_BAR_IS_HOLDING_BAR_1", properties.getMinQuantity(), properties.getMinOrderValue(), properties.getSmallQuantityWarningThreshold(),
                "Cash includes short proceeds; proceeds + 100% initial short margin restricted; equity=cash+longMV-shortMV",
                "Mean of EOD sum(abs(current market value))/current equity across available trading dates",
                "Trading dates with any position during the session / all available trading dates", commission);
        var finalLong = marketValue(positions, lastPrices, true); var finalShort = marketValue(positions, lastPrices, false);
        double averageExposure = timeline.isEmpty() ? 0 : totalExposurePercent / timeline.size();
        return new PatternV1Result(id, "PATTERN_V1", from, to, actualStart, actualEnd, initial, equity, percent(equity.subtract(initial), initial),
                PatternV1MetricsCalculator.cagr(initial.doubleValue(), equity.doubleValue(), actualStart, actualEnd),
                maximumDrawdown, maximumDrawdownPercent, averageExposure / 100, positions.size(), summary, directions,
                List.copyOf(symbolResults), List.copyOf(trades), List.copyOf(equityCurve), List.copyOf(events), Map.copyOf(configurations), Map.copyOf(chartSeries),
                settings, List.copyOf(warnings), 2, cash, availableCash(cash, positions), finalLong, finalShort, finalLong.subtract(finalShort),
                averageExposure, timeline.isEmpty() ? 0 : daysInMarket * 100.0 / timeline.size());
    }
    public static void validateTrade(Trade t, int maxHoldingBars) {
        validateTrade(t,maxHoldingBars,false);
    }
    public static void validateTrade(Trade t, int maxHoldingBars, boolean openingGapFills) {
        if (t.quantity() <= 0 || t.finalQuantity() != t.quantity() || t.remainingQuantity() < 0 || t.actualRiskAmount().signum() < 0
                || t.holdingBars() > maxHoldingBars
                || (t.closed() && (t.remainingQuantity() != 0 || t.exitDate() == null || t.exitPrice() == null || t.resultR() == null))
                || (!t.closed() && (t.holdingBars() >= maxHoldingBars || t.remainingQuantity() != t.quantity() || t.exitDate() != null))
                || (Set.of("TIMEOUT", "TARGET", "STOP").contains(t.exitReason()) && !t.closed()))
            throw new IllegalStateException("Trade invariant failed: " + t.tradeId());
        if (t.actualRiskPercent().compareTo(t.desiredRiskPercent()) > 0) throw new IllegalStateException("Risk budget invariant: " + t.tradeId());
        BigDecimal maximumStopMae=openingGapFills && t.resultR()!=null ? t.resultR().negate().max(BigDecimal.ONE) : BigDecimal.ONE;
        if ("STOP".equals(t.exitReason()) && t.executedMaeR().compareTo(maximumStopMae) > 0)
            throw new IllegalStateException("Executed stop MAE exceeds fill loss");
    }
    private List<SymbolResult> comparison(Map<String, List<HistoricalBar>> series, Map<String, SymbolPatternConfiguration> configs,
            List<Trade> trades, List<Event> events, LocalDate from, LocalDate to, Set<LocalDate> dates) {
        List<SymbolResult> rows = new ArrayList<>(); var analyzer = new PatternOutcomeAnalyzer(); var stats = new PatternStatisticsService();
        var strategy = new PatternV1Strategy(configs, detectors);
        for (var entry : series.entrySet()) {
            String symbol = entry.getKey(); var bars = entry.getValue();
            for (var direction : PatternDirection.values()) {
                var config = configs.get(symbol).direction(direction); if (config == null || !config.enabled()) continue;
                var researchConfig = new PatternResearchConfiguration(from, to, List.of(symbol), List.of(config.pattern()),
                        direction == PatternDirection.LONG ? ResearchDirection.LONG : ResearchDirection.SHORT,
                        List.of(config.targetR()), List.of(), 20, SameBarResolution.STOP_FIRST, 70, 100,
                        EntryMethod.NEXT_BAR_OPEN, StopMethod.PATTERN_EXTREME, BarInterval.DAILY, bd(.01), 0, false, PatternDetectorParameters.defaults());
                List<PatternOccurrence> baseline = new ArrayList<>();
                for (int i = 0; i < bars.size() - 1; i++) for (var signal : strategy.onClosedBar(symbol, bars, i))
                    if (signal.detection().direction() == direction) baseline.addAll(analyzer.analyze("baseline", symbol, bars, signal.detection(), DatasetSegment.IN_SAMPLE, researchConfig));
                var research = stats.metrics(baseline, ignored -> true);
                var group = trades.stream().filter(t -> t.symbol().equals(symbol) && t.direction() == direction).toList();
                var groupEvents = events.stream().filter(e -> e.symbol().equals(symbol) && e.direction() == direction).toList();
                var resolved = group.stream().filter(t -> t.researchResultR() != null).toList();
                Double expectancy = PatternV1MetricsCalculator.average(resolved, t -> t.researchResultR().doubleValue());
                Double pf = PatternV1MetricsCalculator.profitFactor(resolved, t -> t.researchResultR().doubleValue());
                List<String> warnings = new ArrayList<>();
                if (research.expectancyR() != null && expectancy != null && expectancy < research.expectancyR().doubleValue() - .25) warnings.add("EXPECTANCY_DEGRADED_OVER_0.25R");
                if (research.profitFactor() != null && pf != null && pf < research.profitFactor().doubleValue() * .75) warnings.add("PROFIT_FACTOR_DEGRADED_OVER_25_PERCENT");
                double dd = attributionDrawdown(group, series, dates);
                rows.add(new SymbolResult(symbol, direction, config.pattern(), config.targetR(),
                        PatternV1MetricsCalculator.calculate(group, groupEvents, dd), dd, research, expectancy, pf, List.copyOf(warnings)));
            }
        }
        return rows;
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
    private static LocalDate date(HistoricalBar bar) { return bar.dateTime().toLocalDate(); }
    private static BigDecimal bd(double value) { return BigDecimal.valueOf(value); }
    private static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
    private static BigDecimal divide(BigDecimal value, BigDecimal denominator) { return value.divide(denominator, 12, RoundingMode.HALF_UP); }
    private static double percent(BigDecimal value, BigDecimal denominator) { return denominator.signum() > 0 ? value.doubleValue() / denominator.doubleValue() * 100 : 0; }
    private static final class Position {
        final String id; final int number; final PatternV1Strategy.EntryPlan plan; final long quantity; final BigDecimal entryCosts;
        final PatternV1PositionSizing.Decision audit;
        BigDecimal mfe = BigDecimal.ZERO, mae = BigDecimal.ZERO, rawMfe = BigDecimal.ZERO, rawMae = BigDecimal.ZERO;
        int holdingBars; boolean ambiguous;
        Position(String id, int number, PatternV1Strategy.EntryPlan plan, PatternV1PositionSizing.Decision audit) {
            this.id = id; this.number = number; this.plan = plan; this.audit = audit; this.quantity = audit.quantity(); this.entryCosts = audit.entryCosts();
        }
        boolean isLong() { return plan.signal().detection().direction() == PatternDirection.LONG; }
        BigDecimal favorable(BigDecimal price) { return (isLong() ? price.subtract(plan.entryPrice()) : plan.entryPrice().subtract(price)).max(BigDecimal.ZERO); }
        BigDecimal adverse(BigDecimal price) { return (isLong() ? plan.entryPrice().subtract(price) : price.subtract(plan.entryPrice())).max(BigDecimal.ZERO); }
        void measure(HistoricalBar bar, boolean stop, boolean target) {
            var barMfe = favorable(isLong() ? bar.getHigh() : bar.getLow()); var barMae = adverse(isLong() ? bar.getLow() : bar.getHigh());
            rawMfe = rawMfe.max(barMfe); rawMae = rawMae.max(barMae);
            if (stop) {
                mfe = mfe.max(favorable(bar.getOpen()).min(plan.riskPerShare().multiply(plan.signal().targetR())));
                mae = mae.max(plan.riskPerShare()).min(plan.riskPerShare());
            } else if (target) {
                mfe = mfe.max(plan.riskPerShare().multiply(plan.signal().targetR()));
                mae = mae.max(adverse(bar.getOpen()).min(plan.riskPerShare()));
            } else { mfe = mfe.max(barMfe); mae = mae.max(barMae); }
        }
    }
}
