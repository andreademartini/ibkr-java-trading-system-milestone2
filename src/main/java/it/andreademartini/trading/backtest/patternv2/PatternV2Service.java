package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class PatternV2Service {
    private final PatternV1Properties frozen;
    private final TradingSystemProperties trading;
    private final HistoricalDataService historical;
    private final List<PatternDetector> detectors;
    private final PositionSizingService sizing;
    private final PatternV2Store store;
    public PatternV2Service(PatternV1Properties frozen,TradingSystemProperties trading,HistoricalDataService historical,
            List<PatternDetector> detectors,PositionSizingService sizing,PatternV2Store store) {
        this.frozen=frozen;this.trading=trading;this.historical=historical;this.detectors=List.copyOf(detectors);this.sizing=sizing;this.store=store;
    }
    public PatternV2Configuration baseline() {return PatternV2Configuration.baseline(frozen,trading.getRisk());}
    public PatternV2Configuration current() {return store.initialize(baseline()).forNewRun();}
    public PatternV2Configuration reset() {current();var config=store.reset().forNewRun();store.current(config);return config;}
    public PatternV2Configuration loadBaseline() {var copy=baseline();copy.validate(detectors);current(copy);return copy;}
    public void current(PatternV2Configuration config) {config.validate(detectors);store.initialize(baseline());store.current(config);}
    public PatternV2Store.Saved save(PatternV2Configuration config) {config=config.forNewRun();current(config);return store.saveConfiguration(config);}
    public Map<String,List<String>> patterns() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(var d:PatternDirection.values())result.put(d.name(),detectors.stream().filter(p->p.direction()==d).map(p->p.patternType().name()).distinct().sorted().toList());
        return result;
    }
    public PatternV2Run run(PatternV2Configuration config) {
        config=config.forNewRun();
        current(config);
        var g=config.global();LocalDate end=g.endDate();
        if(end==null)end=config.symbols().stream().filter(PatternV2Configuration.Symbol::enabled)
                .map(s->historical.status(s.symbol(),g.timeframe()).lastDate()).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(LocalDate.now().minusDays(1));
        if(!end.isBefore(LocalDate.now()))end=LocalDate.now().minusDays(1);
        var normalized=config.withGlobal(new PatternV2Configuration.Global(g.initialCapital(),g.riskPercent(),g.maxOrderValuePercent(),g.maxOrderValue(),g.maxOrderValueMode(),g.maxPositionValue(),g.maxQuantity(),g.useLeverage(),g.minQuantity(),g.minOrderValue(),
                g.startDate(),end,g.timeframe(),g.maxHoldingBars(),g.stopBufferTicks(),g.tickSize(),g.sameBarPolicy(),g.entryMode(),g.stopMode(),g.commissionEnabled(),g.commission(),g.slippageEnabled(),g.slippagePercentPerSide(),g.maxLongNotional(),g.maxShortNotional(),g.transactionCostsPerShare(),g.allowLong(),g.allowShort(),g.smallQuantityWarningThreshold(),g.maxPositionValueMode(),g.maxPositionValuePercent(),g.maxLongNotionalMode(),g.maxLongNotionalPercent(),g.maxShortNotionalMode(),g.maxShortNotionalPercent()));
        normalized.validate(detectors);Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        for(var s:normalized.symbols())if(s.enabled())bars.put(s.symbol(),historical.load(s.symbol(),g.startDate(),end,g.timeframe()));
        return runOnSnapshot(normalized,bars);
    }
    public PatternV2Run simulate(PatternV2Configuration config,Map<String,List<HistoricalBar>> bars) {
        return simulate(config,bars,UUID.randomUUID().toString());
    }
    PatternV2Run simulate(PatternV2Configuration config,Map<String,List<HistoricalBar>> bars,String identity) {
        config=config.forNewRun();
        var simulation=new PatternPortfolioSimulator(sizing,detectors).run(config,bars,identity);
        var p=simulation.portfolio();var g=config.global();
        var fee=g.commissionEnabled()?g.commission():PatternV1Commission.fixed(BigDecimal.ZERO);
        var slipPercent=BigDecimal.valueOf(g.slippageEnabled()?g.slippagePercentPerSide():0);
        List<PatternV2Run.Execution> executions=new ArrayList<>();
        BigDecimal fees=BigDecimal.ZERO,slippage=BigDecimal.ZERO,other=BigDecimal.ZERO;
        for(var t:p.trades()) {
            var q=BigDecimal.valueOf(t.quantity());boolean isLong=t.direction()==PatternDirection.LONG;
            var entryBar=bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.entryIndex()).timestamp())).findFirst().orElseThrow();
            var entryFactor=config.splitCosts().factor(t.symbol(),entryBar);
            var exitBar=t.closed()?bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.exitIndex()).timestamp())).findFirst().orElseThrow():null;
            var exitFactor=t.closed()?config.splitCosts().factor(t.symbol(),exitBar):null;
            var commission=PatternV2SplitCosts.commission(fee,entryFactor).charge(t.quantity(),t.entryPrice())
                    .add(t.closed()?PatternV2SplitCosts.commission(fee,exitFactor).charge(t.quantity(),t.exitPrice()):BigDecimal.ZERO);
            var entrySlip=t.entryPrice().multiply(slipPercent).divide(BigDecimal.valueOf(100),java.math.MathContext.DECIMAL64);
            var exitSlip=t.closed()?t.exitPrice().multiply(slipPercent).divide(BigDecimal.valueOf(100),java.math.MathContext.DECIMAL64):BigDecimal.ZERO;
            var slipCost=entrySlip.add(exitSlip).multiply(q);
            var extra=BigDecimal.valueOf(PatternV2SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),entryFactor).doubleValue()).multiply(q);
            if(t.closed())extra=extra.add(BigDecimal.valueOf(PatternV2SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),exitFactor).doubleValue()).multiply(q));
            var prices=p.priceSeries().get(t.symbol());
            var entry=isLong?t.entryPrice().add(entrySlip):t.entryPrice().subtract(entrySlip);
            var exit=!t.closed()?null:isLong?t.exitPrice().subtract(exitSlip):t.exitPrice().add(exitSlip);
            var splitExecution=new PatternV2Run.SplitExecution(entryFactor,exitFactor,PatternV2SplitCosts.equivalentQuantity(t.quantity(),entryFactor),
                    t.closed()?PatternV2SplitCosts.equivalentQuantity(t.quantity(),exitFactor):null,t.entryPrice().multiply(entryFactor),t.closed()?t.exitPrice().multiply(exitFactor):null);
            executions.add(new PatternV2Run.Execution(t,prices.get(t.entryIndex()).timestamp(),t.closed()?prices.get(t.exitIndex()).timestamp():null,entry,exit,commission,slipCost,extra,splitExecution,new PatternRange(t.patternHigh(),t.patternLow(),t.patternClose()),CloseStrengthAnalysis.value(t),true,simulation.capitalLimitsAudits().get(t.tradeId()),simulation.bollingerAtrSignals().stream().filter(a->a.symbol().equals(t.symbol())&&a.direction().equals(t.direction().name())&&a.pattern().equals(t.pattern().name())&&a.signalIndex()==t.signalIndex()).findFirst().orElse(null)));
            fees=fees.add(commission);slippage=slippage.add(slipCost);other=other.add(extra);
            if(t.costs().compareTo(commission.add(slipCost).add(extra))!=0)throw new IllegalStateException("Cost reconciliation failed: "+t.tradeId());
        }
        var result = new PatternV2Run(p.backtestId(),"PATTERN_V2",Instant.now(),config,p,simulation.equityTimestamps(),List.copyOf(executions),PatternV2Analysis.calculate(p,null,null),new PatternV2Run.Costs(fees,slippage,other),null,simulation.signals(),null,null,null,simulation.closeStrengthSignals(),null);
        result=result.withBollingerAtr(simulation.bollingerAtrSignals(),null);result=result.withBollingerAtr(simulation.bollingerAtrSignals(),BollingerAtrAnalysis.calculate(result));
        result=result.withCapitalSkipAudits(simulation.capitalSkipAudits()).withResearch(PatternRangeAnalysis.calculate(result,null),null,null).withCloseStrengthResearch(CloseStrengthAnalysis.calculate(result,null));
        if(config.capitalSkipShadowAnalysisEnabled()){var source=result.withResearch(result.patternRangeAnalysis(),PatternV2Dataset.capture(bars).id(),null);result=result.withCapitalSkipShadowAnalysis(new CapitalSkipShadowAnalysis(sizing).analyze(source,bars));}
        result=result.withMarketRegimeFilterAnalysis(MarketRegimeFilter.calculate(result,simulation.marketRegimeSignals(),null));
        if(config.marketRegimeAnalysisEnabled())result=result.withMarketRegimeAnalysis(MarketRegimeAnalysis.calculate(result));
        return result;
    }
    /** No current configuration write here: paired baseline is research-only. */
    public PatternV2Run runOnSnapshot(PatternV2Configuration config,Map<String,List<HistoricalBar>> input) {return runOnSnapshot(config,input,null);}
    private PatternV2Run runOnSnapshot(PatternV2Configuration config,Map<String,List<HistoricalBar>> input,PatternV2Run reference) {
        config=config.forNewRun();config.validate(detectors);
        var dataset=PatternV2Dataset.capture(input);var bars=dataset.bars();
        PatternV2Run baseline=reference;
        if(baseline==null&&(config.patternRangeFilterEnabled()||config.closeStrengthFilterEnabled()||config.bollingerAtrFilterEnabled())) {
            var baseConfig=config.withName("V2_BASELINE").withRangeFilter(false,config.minimumPatternRangePct()).withCloseStrengthFilter(false,config.maxCloseStrength()).withBollingerAtr(false,config.bollingerPeriod(),config.bollingerStdDev(),config.atrPeriod(),config.maxBollingerDistanceAtr()).withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold());
            PatternV2RunComparison.requireSameTradingConfiguration(baseConfig,config);
            baseline=simulate(baseConfig,bars);
        }
        PatternV2Run regimeBaseline=null;
        if(config.marketRegimeFilterEnabled())regimeBaseline=simulate(config.withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold()),bars);
        var actual=simulate(config,bars);
        if(regimeBaseline!=null)actual=actual.withMarketRegimeFilterAnalysis(MarketRegimeFilter.withIndependentPeriods(MarketRegimeFilter.calculate(actual,actual.marketRegimeFilterAnalysis().signals(),regimeBaseline),marketRegimePeriods(config,bars,actual)));
        if(baseline!=null&&reference==null)baseline=baseline.withResearch(PatternRangeAnalysis.calculate(baseline,null),dataset.id(),actual.runId()).withCloseStrengthResearch(CloseStrengthAnalysis.calculate(baseline,null));
        actual=actual.withResearch(PatternRangeAnalysis.calculate(actual,baseline),dataset.id(),baseline==null?null:baseline.runId());
        actual=actual.withCloseStrengthResearch(CloseStrengthAnalysis.calculate(actual,baseline));
        if(regimeBaseline!=null) {regimeBaseline=regimeBaseline.withResearch(regimeBaseline.patternRangeAnalysis(),dataset.id(),actual.runId());store.save(regimeBaseline);if(baseline==null)actual=actual.withResearch(actual.patternRangeAnalysis(),dataset.id(),regimeBaseline.runId());}
        store.saveDataset(dataset);if(baseline!=null&&reference==null)store.save(baseline);store.save(actual);return actual;
    }
    private List<MarketRegimeFilter.PeriodComparison> marketRegimePeriods(PatternV2Configuration config,Map<String,List<HistoricalBar>> bars,PatternV2Run actual) {
        var result=new ArrayList<MarketRegimeFilter.PeriodComparison>();var p=actual.portfolio();
        if(p.actualStartDate()==null)return List.of();
        for(int year:new int[]{2010,2014,2018,2022}) {
            var from=LocalDate.of(year,1,1);var to=year==2022?p.actualEndDate():LocalDate.of(year+3,12,31);
            if(from.isBefore(config.global().startDate()))from=config.global().startDate();if(to.isAfter(p.actualEndDate()))to=p.actualEndDate();
            if(to.isBefore(from)||to.isBefore(p.actualStartDate()))continue;
            var periodConfig=config.withGlobal(config.global().withDates(from,to));
            // Disposable real simulations, original sizing/costs/other filters, no providers or orders.
            var off=simulate(periodConfig.withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold()),bars);
            var on=simulate(periodConfig,bars);
            if(on.portfolio().actualStartDate()!=null)result.add(MarketRegimeFilter.independentPeriod(year+"-"+(year==2022?to.getYear():year+3),off,on));
        }
        return List.copyOf(result);
    }
    public PatternV2Run replay(String id) {return replay(id,null);}
    public PatternV2Run replay(String id,PatternV2Configuration configuration) {
        if(configuration==null)return replayOriginal(id);
        var source=store.run(id);if(source.datasetId()==null)throw new IllegalArgumentException("Exact input snapshot unavailable");
        configuration=configuration.forNewRun();configuration.validate(detectors);
        PatternV2RunComparison.requireSameTradingConfiguration(source.configuration(),configuration);
        var reference=findUnfilteredBaseline(source);
        return runOnSnapshot(configuration,store.dataset(source.datasetId()).bars(),reference);
    }
    private PatternV2Run findUnfilteredBaseline(PatternV2Run source) {
        if(!source.configuration().patternRangeFilterEnabled()&&!source.configuration().closeStrengthFilterEnabled()&&!source.configuration().bollingerAtrFilterEnabled()&&!source.configuration().marketRegimeFilterEnabled())return source;
        for(var candidate:store.runs())if(Objects.equals(source.datasetId(),candidate.datasetId())&&!candidate.configuration().patternRangeFilterEnabled()&&!candidate.configuration().closeStrengthFilterEnabled()&&!candidate.configuration().bollingerAtrFilterEnabled()&&!candidate.configuration().marketRegimeFilterEnabled()) {
            try {PatternV2RunComparison.requireSameTradingConfiguration(source.configuration(),candidate.configuration());return candidate;}catch(IllegalArgumentException incompatible) { /* Try another baseline. */ }
        }
        return null;
    }
    public PatternV2RunComparison.Comparison compare(List<String> ids) {
        if(ids==null||ids.size()<2||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select distinct runs");
        return PatternV2RunComparison.compare(ids.stream().map(store::run).toList());
    }
    public PatternV2Run replayCapitalLimits(String id,PatternV2Configuration configuration) {
        var source=store.run(id);if(source.datasetId()==null)throw new IllegalArgumentException("Exact input snapshot unavailable");
        configuration=configuration.forNewRun();configuration.validate(detectors);
        PatternV2CapitalComparison.requireSameConfiguration(source.configuration(),configuration);
        return runOnSnapshot(configuration,store.dataset(source.datasetId()).bars());
    }
    public PatternV2CapitalComparison.Comparison compareCapitalLimits(List<String> ids) {
        if(ids==null||ids.size()<2||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select distinct runs");
        return PatternV2CapitalComparison.compare(ids.stream().map(store::run).toList());
    }
    public PatternV2Store.Saved savePreset(PatternV2Configuration config) {config.validate(detectors);return store.saveConfiguration(config,false);}
    private PatternV2Run replayOriginal(String id) {
        var run=store.run(id);if(run.datasetId()==null)throw new IllegalArgumentException("Legacy run has no exact input snapshot");
        return runOnSnapshot(run.configuration(),store.dataset(run.datasetId()).bars());
    }
    /** Computes a disposable benchmark. Never calls V1 service/store.save or edits frozen beans. */
    public PatternV2Run benchmark(String id) {
        var run=store.run(id);var g=run.configuration().global();Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        for(var symbol:frozen.getSymbols().keySet())bars.put(symbol,historical.load(symbol,g.startDate(),g.endDate(),BarInterval.DAILY));
        var request=new PatternV1Request(null,g.startDate(),g.endDate(),BarInterval.DAILY,BigDecimal.valueOf(g.initialCapital()),null,null);
        var benchmark=new PatternV1PortfolioEngine(sizing,detectors).run(request,frozen,trading.getRisk(),bars);
        var result=run.withBenchmark(benchmark);store.save(result);return result;
    }
    public PatternV2Run result(String id){var run=store.run(id);return store.shadowAnalysis(id).map(run::withCapitalSkipShadowAnalysis).orElse(run);}
    public CapitalSkipShadowAnalysis.Analysis capitalSkipsAnalysis(String id){var run=store.run(id);if(run.capitalSkipAudits()==null)throw new IllegalArgumentException("Legacy run lacks decision context; replay its saved dataset first");var analysis=new CapitalSkipShadowAnalysis(sizing).analyze(run,store.dataset(run.datasetId()).bars());store.saveShadowAnalysis(id,analysis);return analysis;}
}
