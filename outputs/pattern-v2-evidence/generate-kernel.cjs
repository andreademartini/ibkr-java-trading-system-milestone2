const fs = require('node:fs');
const root = 'src/main/java/it/andreademartini/trading/backtest/';
let source = fs.readFileSync(root+'patternv1/PatternV1PortfolioEngine.java','utf8');
source = source.replace('package it.andreademartini.trading.backtest.patternv1;', 'package it.andreademartini.trading.backtest.patternv2;\n\nimport it.andreademartini.trading.backtest.patternv1.*;');
source = source.replace('public final class PatternV1PortfolioEngine', 'public final class PatternPortfolioSimulator');
source = source.replace('public PatternV1PortfolioEngine(', 'public PatternPortfolioSimulator(');
const start = source.indexOf('    public PatternV1Result run(');
const map = source.indexOf('        Map<String, SymbolPatternConfiguration> configurations', start);
source = source.slice(0,start)+`    public record Simulation(PatternV1Result portfolio, List<String> equityTimestamps) {}
    public Simulation run(PatternV2Configuration configuration, Map<String,List<HistoricalBar>> input) {
        configuration.validate(detectors);
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
`+source.slice(map);
source = source.replace('new TreeMap<>(properties.getSymbols())', 'new TreeMap<>(mapping)');
source = source.replace('PatternV1Strategy strategy = new PatternV1Strategy(configurations, detectors);', 'ConfiguredPatternStrategy strategy = new ConfiguredPatternStrategy(configuration, detectors);');
source = source.replace('b.interval() != BarInterval.DAILY', 'b.interval() != global.timeframe()').replace('"DAILY only"','"Timeframe mismatch"');
source = source.replace('date(bars.get(i)), ignored', 'bars.get(i).dateTime(), ignored');
source = source.replace('TreeMap<LocalDate, Map<String, Integer>>', 'TreeMap<java.time.LocalDateTime, Map<String, Integer>>');
source = source.replace('filtered.stream().map(b -> date(b)).distinct()', 'filtered.stream().map(HistoricalBar::dateTime).distinct()');
source = source.replace('filtered.stream().map(PatternV1PortfolioEngine::date)', 'filtered.stream().map(HistoricalBar::dateTime)');
source = source.replaceAll('day.getKey()', 'day.getKey().toLocalDate()');
source = source.replace('var decision = sizing.calculate(', 'var symbolConfig=configuration.symbols().stream().filter(s->s.symbol().equals(symbol)).findFirst().orElseThrow();\n                    risk=riskFor(configuration,symbolConfig,equityAtEntry);\n                    var decision = sizing.calculate(');
source = source.replace('plan, decision);','plan, decision, symbolConfig.holding(global));');
source = source.replace('p.measure(bar, stop, target);', 'boolean exitOnStop=stop && (!target || global.sameBarPolicy()==SameBarResolution.STOP_FIRST);\n                p.measure(bar, exitOnStop, target && !exitOnStop);');
source = source.replaceAll('p.holdingBars >= properties.getMaxHoldingBars()', 'p.holdingBars >= p.maxHoldingBars');
source = source.replace('String reason = stop ? "STOP"', 'String reason = exitOnStop ? "STOP"');
source = source.replace('var exit = stop ? p.plan.stopPrice()', 'var exit = exitOnStop ? p.plan.stopPrice()');
source = source.replace('var researchR = stop ?', 'var researchR = exitOnStop ?');
source = source.replace('"resolution", "STOP_FIRST"', '"resolution", global.sameBarPolicy().name()');
source = source.replace('event(events, stop ? "STOP_FILLED"', 'event(events, exitOnStop ? "STOP_FILLED"');
source = source.replace('"stopBufferTicks", 0,', '"stopBufferTicks", global.stopBufferTicks(),');
source = source.replace('validateTrade(t, properties.getMaxHoldingBars())', 'PatternV1PortfolioEngine.validateTrade(t, configuration.symbols().stream().filter(s->s.symbol().equals(t.symbol())).findFirst().orElseThrow().holding(global))');
source = source.replace('var symbolResults = comparison(series, configurations, trades, events, from, to, timeline.keySet());', 'var dates=new TreeSet<LocalDate>();timeline.keySet().forEach(t->dates.add(t.toLocalDate()));\n        var symbolResults = new ArrayList<SymbolResult>();\n        for(var e:configurations.entrySet()) for(var d:PatternDirection.values()) {\n            var c=e.getValue().direction(d);if(c==null||!c.enabled())continue;\n            var group=trades.stream().filter(t->t.symbol().equals(e.getKey())&&t.direction()==d).toList();\n            double dd=attributionDrawdown(group,series,dates);\n            symbolResults.add(new SymbolResult(e.getKey(),d,c.pattern(),c.targetR(),PatternV1MetricsCalculator.calculate(group,events.stream().filter(v->v.symbol().equals(e.getKey())&&v.direction()==d).toList(),dd),dd,null,null,null,List.of()));\n        }');
source = source.replace('attributionDrawdown(group, series, timeline.keySet())', 'attributionDrawdown(group, series, dates)');
source = source.replace('timeline.firstKey()', 'timeline.firstKey().toLocalDate()').replace('timeline.lastKey()', 'timeline.lastKey().toLocalDate()');
source = source.replace('RunSettings("DAILY", "NEXT_BAR_OPEN", "PATTERN_EXTREME", 0, 20, "STOP_FIRST", mode,', 'RunSettings(global.timeframe().name(), global.entryMode().name(), global.stopMode().name(), global.stopBufferTicks(), global.maxHoldingBars(), global.sameBarPolicy().name(), mode,');
source = source.replace('return new PatternV1Result(id, "PATTERN_V1"', 'return new Simulation(new PatternV1Result(id, "PATTERN_V2"');
source = source.replace('daysInMarket * 100.0 / timeline.size());', 'daysInMarket * 100.0 / timeline.size()), timeline.keySet().stream().map(t->t.toInstant(java.time.ZoneOffset.UTC).toString()).toList());');
source = source.replace('signal.symbol(), "PATTERN_V1"', 'signal.symbol(), "PATTERN_V2"');
source = source.replace('int holdingBars; boolean ambiguous;', 'int holdingBars; boolean ambiguous; final int maxHoldingBars;');
source = source.replace('PatternV1PositionSizing.Decision audit) {', 'PatternV1PositionSizing.Decision audit, int maxHoldingBars) {\n            this.maxHoldingBars=maxHoldingBars;');
// The V1-only research comparison and frozen validation are not part of the generic simulator.
const validation=source.indexOf('    public static void validateTrade(');
const attribution=source.indexOf('    private static double attributionDrawdown(', validation);
source=source.slice(0,validation)+source.slice(attribution);
source=source.replace('warnings.add("Holding bar 1 is the entry bar, as Pattern Research; TIMEOUT closes at bar 20 Close. Only incomplete final windows remain OPEN.");','warnings.add("Holding bar 1 is the entry bar. TIMEOUT uses global/symbol holding limits; incomplete windows remain OPEN.");');
source=source.replace('warnings.add("STOP_FIRST executable excursions use earlier bars, the known open and the fill; raw exit-candle excursions are reported separately.");','warnings.add("Same-bar policy: " + global.sameBarPolicy() + "; raw exit-candle excursions reported separately.");');
source=source.replace('    private static LocalDate date(',`    private static TradingSystemProperties.RiskProperties riskFor(PatternV2Configuration config, PatternV2Configuration.Symbol symbol, BigDecimal equity) {
        var g=config.global();var r=new TradingSystemProperties.RiskProperties();
        r.setTradingCapital(g.initialCapital());r.setRiskPerTradePercent(symbol!=null&&symbol.riskPercent()!=null?symbol.riskPercent():g.riskPercent());
        var base=g.maxOrderValueMode()==PatternV2Configuration.OrderValueMode.CURRENT_EQUITY_PERCENT?equity:bd(g.initialCapital());
        double percent=symbol!=null&&symbol.maxOrderValuePercent()!=null?symbol.maxOrderValuePercent():g.maxOrderValuePercent();
        r.setMaxOrderValue(g.maxOrderValue()!=null&&(symbol==null||symbol.maxOrderValuePercent()==null)?g.maxOrderValue():base.multiply(bd(percent)).divide(bd(100),MATH).max(BigDecimal.ZERO).doubleValue());
        r.setMaxQuantityPerOrder(symbol!=null&&symbol.maxQuantity()!=null?symbol.maxQuantity():g.maxQuantity());
        r.setMaxPositionValue(g.maxPositionValue());r.setMaxLongNotional(g.maxLongNotional());r.setMaxShortNotional(g.maxShortNotional());
        r.setUseLeverage(g.useLeverage());r.setAllowLong(g.allowLong());r.setAllowShortSelling(g.allowShort());r.setTransactionCostsPerShare(g.transactionCostsPerShare());
        return r;
    }
    private static LocalDate date(`);
source = source.replace('/** All symbols\' opens precede today\'s intrabar exits and closed-bar detection. */', '/** Configurable simulation kernel. Accounting ordering is an audited port of the frozen V1 kernel;\n * detectors, sizing, commission calculation and metrics remain shared stateless components.\n * V1 is deliberately not routed through this kernel. V1 parity tests guard this boundary. */');
fs.mkdirSync(root+'patternv2',{recursive:true});
fs.writeFileSync(root+'patternv2/PatternPortfolioSimulator.java',source);
