package it.andreademartini.trading.backtest.patternv3;

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
import it.andreademartini.trading.backtest.patternv2.ExitFillPolicy;
import it.andreademartini.trading.backtest.patternv2.GapExecutionAudit;
import it.andreademartini.trading.backtest.execution.ExecutionModel;
import it.andreademartini.trading.backtest.execution.ExecutionRunView;

@Service
public class PatternV3Service {
    private final PatternV1Properties frozen;
    private final TradingSystemProperties trading;
    private final HistoricalDataService historical;
    private final List<PatternDetector> detectors;
    private final PositionSizingService sizing;
    private final PatternV3Store store;
    public PatternV3Service(PatternV1Properties frozen,TradingSystemProperties trading,HistoricalDataService historical,
            List<PatternDetector> detectors,PositionSizingService sizing,PatternV3Store store) {
        this.frozen=frozen;this.trading=trading;this.historical=historical;this.detectors=List.copyOf(detectors);this.sizing=sizing;this.store=store;
    }
    public PatternV3Configuration baseline() {try(var in=getClass().getResourceAsStream("/pattern-research/v3-initial.json")){return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readValue(in,PatternV3Configuration.class);}catch(java.io.IOException e){throw new IllegalStateException("Initial V3 research snapshot missing",e);}}
    public PatternV3Configuration current() {return store.initialize(baseline()).forNewRun();}
    public PatternV3Configuration reset() {current();var config=store.reset().forNewRun();store.current(config);return config;}
    public PatternV3Configuration loadBaseline() {var copy=PatternV3Configuration.baseline(frozen,trading.getRisk());copy.validate(detectors);current(copy);return copy;}
    public void current(PatternV3Configuration config) {config.validate(detectors);store.initialize(baseline());store.current(config);}
    public PatternV3Store.Saved save(PatternV3Configuration config) {config=config.forNewRun();current(config);return store.saveConfiguration(config);}
    public Map<String,List<String>> patterns() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(var d:PatternDirection.values())result.put(d.name(),detectors.stream().filter(p->p.direction()==d).map(p->p.patternType().name()).distinct().sorted().toList());
        return result;
    }
    public PatternV3Run run(PatternV3Configuration config) {
        config=config.forNewRun();
        current(config);
        var g=config.global();LocalDate end=g.endDate();
        if(end==null)end=config.symbols().stream().filter(PatternV3Configuration.Symbol::enabled)
                .map(s->historical.status(s.symbol(),g.timeframe()).lastDate()).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(LocalDate.now().minusDays(1));
        if(!end.isBefore(LocalDate.now()))end=LocalDate.now().minusDays(1);
        var normalized=config.withGlobal(new PatternV3Configuration.Global(g.initialCapital(),g.riskPercent(),g.maxOrderValuePercent(),g.maxOrderValue(),g.maxOrderValueMode(),g.maxPositionValue(),g.maxQuantity(),g.useLeverage(),g.minQuantity(),g.minOrderValue(),
                g.startDate(),end,g.timeframe(),g.maxHoldingBars(),g.stopBufferTicks(),g.tickSize(),g.sameBarPolicy(),g.entryMode(),g.stopMode(),g.commissionEnabled(),g.commission(),g.slippageEnabled(),g.slippagePercentPerSide(),g.maxLongNotional(),g.maxShortNotional(),g.transactionCostsPerShare(),g.allowLong(),g.allowShort(),g.smallQuantityWarningThreshold(),g.maxPositionValueMode(),g.maxPositionValuePercent(),g.maxLongNotionalMode(),g.maxLongNotionalPercent(),g.maxShortNotionalMode(),g.maxShortNotionalPercent()));
        normalized.validate(detectors);Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        for(var s:normalized.symbols())if(s.enabled())bars.put(s.symbol(),historical.load(s.symbol(),g.startDate(),end,g.timeframe()));
        return runOnSnapshot(normalized,bars);
    }
    public PatternV3Run simulate(PatternV3Configuration config,Map<String,List<HistoricalBar>> bars) {
        return simulate(config,bars,UUID.randomUUID().toString());
    }
    PatternV3Run simulate(PatternV3Configuration config,Map<String,List<HistoricalBar>> bars,String identity) {
        return simulate(config,bars,identity,ExitFillPolicy.OPEN_FIRST);
    }
    public PatternV3Run simulate(PatternV3Configuration config,Map<String,List<HistoricalBar>> bars,ExitFillPolicy policy){
        return simulate(config,bars,UUID.randomUUID().toString(),policy);
    }
    private PatternV3Run simulate(PatternV3Configuration config,Map<String,List<HistoricalBar>> bars,String identity,ExitFillPolicy policy){
        config=config.forNewRun();
        var simulation=new PatternPortfolioSimulator(sizing,detectors).run(config,bars,identity,policy);
        var p=simulation.portfolio();var g=config.global();
        var fee=g.commissionEnabled()?g.commission():PatternV1Commission.fixed(BigDecimal.ZERO);
        var slipPercent=BigDecimal.valueOf(g.slippageEnabled()?g.slippagePercentPerSide():0);
        List<PatternV3Run.Execution> executions=new ArrayList<>();
        BigDecimal fees=BigDecimal.ZERO,slippage=BigDecimal.ZERO,other=BigDecimal.ZERO;
        for(var t:p.trades()) {
            var q=BigDecimal.valueOf(t.quantity());boolean isLong=t.direction()==PatternDirection.LONG;
            var entryBar=bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.entryIndex()).timestamp())).findFirst().orElseThrow();
            var entryFactor=config.splitCosts().factor(t.symbol(),entryBar);
            var exitBar=t.closed()?bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.exitIndex()).timestamp())).findFirst().orElseThrow():null;
            var exitFactor=t.closed()?config.splitCosts().factor(t.symbol(),exitBar):null;
            var commission=PatternV3SplitCosts.commission(fee,entryFactor).charge(t.quantity(),t.entryPrice())
                    .add(t.closed()?PatternV3SplitCosts.commission(fee,exitFactor).charge(t.quantity(),t.exitPrice()):BigDecimal.ZERO);
            var entrySlip=it.andreademartini.trading.backtest.execution.Slippage.perShare(t.entryPrice(),slipPercent);
            var exitSlip=t.closed()?it.andreademartini.trading.backtest.execution.Slippage.perShare(t.exitPrice(),slipPercent):BigDecimal.ZERO;
            var slipCost=entrySlip.add(exitSlip).multiply(q);
            var extra=BigDecimal.valueOf(PatternV3SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),entryFactor).doubleValue()).multiply(q);
            if(t.closed())extra=extra.add(BigDecimal.valueOf(PatternV3SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),exitFactor).doubleValue()).multiply(q));
            var prices=p.priceSeries().get(t.symbol());
            var entry=isLong?t.entryPrice().add(entrySlip):t.entryPrice().subtract(entrySlip);
            var exit=!t.closed()?null:isLong?t.exitPrice().subtract(exitSlip):t.exitPrice().add(exitSlip);
            var splitExecution=new PatternV3Run.SplitExecution(entryFactor,exitFactor,PatternV3SplitCosts.equivalentQuantity(t.quantity(),entryFactor),
                    t.closed()?PatternV3SplitCosts.equivalentQuantity(t.quantity(),exitFactor):null,t.entryPrice().multiply(entryFactor),t.closed()?t.exitPrice().multiply(exitFactor):null);
            executions.add(new PatternV3Run.Execution(t,prices.get(t.entryIndex()).timestamp(),t.closed()?prices.get(t.exitIndex()).timestamp():null,entry,exit,commission,slipCost,extra,splitExecution,new PatternRange(t.patternHigh(),t.patternLow(),t.patternClose()),CloseStrengthAnalysis.value(t),true,simulation.capitalLimitsAudits().get(t.tradeId()),simulation.bollingerAtrSignals().stream().filter(a->a.symbol().equals(t.symbol())&&a.direction().equals(t.direction().name())&&a.pattern().equals(t.pattern().name())&&a.signalIndex()==t.signalIndex()).findFirst().orElse(null)));
            fees=fees.add(commission);slippage=slippage.add(slipCost);other=other.add(extra);
            if(t.costs().compareTo(commission.add(slipCost).add(extra))!=0)throw new IllegalStateException("Cost reconciliation failed: "+t.tradeId());
        }
        var result = new PatternV3Run(p.backtestId(),"PATTERN_V3",Instant.now(),config,p,simulation.equityTimestamps(),List.copyOf(executions),PatternV3Analysis.calculate(p,null,null),new PatternV3Run.Costs(fees,slippage,other),null,simulation.signals(),null,null,null,simulation.closeStrengthSignals(),null);
        result=result.withExecutionMetadata(ExecutionModel.version(policy),null);
        result=result.withBollingerAtr(simulation.bollingerAtrSignals(),null);result=result.withBollingerAtr(simulation.bollingerAtrSignals(),BollingerAtrAnalysis.calculate(result));
        result=result.withCapitalSkipAudits(simulation.capitalSkipAudits()).withResearch(PatternRangeAnalysis.calculate(result,null),null,null).withCloseStrengthResearch(CloseStrengthAnalysis.calculate(result,null));
        if(config.capitalSkipShadowAnalysisEnabled()){var source=result.withResearch(result.patternRangeAnalysis(),PatternV3Dataset.capture(bars).id(),null);result=result.withCapitalSkipShadowAnalysis(new CapitalSkipShadowAnalysis(sizing).analyze(source,bars));}
        result=result.withMarketRegimeFilterAnalysis(MarketRegimeFilter.calculate(result,simulation.marketRegimeSignals(),null));
        if(config.marketRegimeAnalysisEnabled())result=result.withMarketRegimeAnalysis(MarketRegimeAnalysis.calculate(result));
        return result.withExecutionMetadata(ExecutionModel.version(policy),GapExecutionAudit.capture(ExecutionRunView.of(result),bars)).withFilterAudit(simulation.filterAudit());
    }
    /** No current configuration write here: paired baseline is research-only. */
    public PatternV3Run runOnSnapshot(PatternV3Configuration config,Map<String,List<HistoricalBar>> input) {return runOnSnapshot(config,input,null);}
    private PatternV3Run runOnSnapshot(PatternV3Configuration config,Map<String,List<HistoricalBar>> input,PatternV3Run reference) {
        return runOnSnapshot(config,input,reference,ExitFillPolicy.OPEN_FIRST);
    }
    private PatternV3Run runOnSnapshot(PatternV3Configuration config,Map<String,List<HistoricalBar>> input,PatternV3Run reference,ExitFillPolicy policy) {
        config=config.forNewRun();config.validate(detectors);
        var dataset=PatternV3Dataset.capture(input);var bars=dataset.bars();
        PatternV3Run baseline=reference;
        if(baseline==null&&(config.patternRangeFilterEnabled()||config.closeStrengthFilterEnabled()||config.rvolEnabled()||config.bollingerAtrFilterEnabled())) {
            var baseConfig=config.withName("V2_BASELINE").withRangeFilter(false,config.rangeMinimum()).withCloseStrengthFilter(false,config.maxCloseStrength()).withBollingerAtr(false,config.bollingerPeriod(),config.bollingerStdDev(),config.atrPeriod(),config.maxBollingerDistanceAtr()).withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold()).withResearchFilters(false,config.closeStrengthMin(),false,config.rangeMinimum(),config.patternRangeMaxPct(),false,config.rvolPeriod(),config.rvolMinimum(),config.relativeVolumeMax());
            PatternV3RunComparison.requireSameTradingConfiguration(baseConfig,config);
            baseline=simulate(baseConfig,bars,policy);
        }
        PatternV3Run regimeBaseline=null;
        if(config.marketRegimeFilterEnabled())regimeBaseline=simulate(config.withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold()),bars,policy);
        var actual=simulate(config,bars,policy);
        if(regimeBaseline!=null)actual=actual.withMarketRegimeFilterAnalysis(MarketRegimeFilter.withIndependentPeriods(MarketRegimeFilter.calculate(actual,actual.marketRegimeFilterAnalysis().signals(),regimeBaseline),marketRegimePeriods(config,bars,actual)));
        if(baseline!=null&&reference==null)baseline=baseline.withResearch(PatternRangeAnalysis.calculate(baseline,null),dataset.id(),actual.runId()).withCloseStrengthResearch(CloseStrengthAnalysis.calculate(baseline,null));
        actual=actual.withResearch(PatternRangeAnalysis.calculate(actual,baseline),dataset.id(),baseline==null?null:baseline.runId());
        actual=actual.withCloseStrengthResearch(CloseStrengthAnalysis.calculate(actual,baseline));
        if(regimeBaseline!=null) {regimeBaseline=regimeBaseline.withResearch(regimeBaseline.patternRangeAnalysis(),dataset.id(),actual.runId());store.save(regimeBaseline);if(baseline==null)actual=actual.withResearch(actual.patternRangeAnalysis(),dataset.id(),regimeBaseline.runId());}
        store.saveDataset(dataset);if(baseline!=null&&reference==null)store.save(baseline);store.save(actual);return actual;
    }
    private List<MarketRegimeFilter.PeriodComparison> marketRegimePeriods(PatternV3Configuration config,Map<String,List<HistoricalBar>> bars,PatternV3Run actual) {
        var result=new ArrayList<MarketRegimeFilter.PeriodComparison>();var p=actual.portfolio();
        if(p.actualStartDate()==null)return List.of();
        for(int year:new int[]{2010,2014,2018,2022}) {
            var from=LocalDate.of(year,1,1);var to=year==2022?p.actualEndDate():LocalDate.of(year+3,12,31);
            if(from.isBefore(config.global().startDate()))from=config.global().startDate();if(to.isAfter(p.actualEndDate()))to=p.actualEndDate();
            if(to.isBefore(from)||to.isBefore(p.actualStartDate()))continue;
            var periodConfig=config.withGlobal(config.global().withDates(from,to));
            // Disposable real simulations, original sizing/costs/other filters, no providers or orders.
            var off=simulate(periodConfig.withMarketRegimeFilter(false,config.marketRegimeFilterAdxPeriod(),config.marketRegimeFilterAdxThreshold()),bars,actual.executionPolicy());
            var on=simulate(periodConfig,bars,actual.executionPolicy());
            if(on.portfolio().actualStartDate()!=null)result.add(MarketRegimeFilter.independentPeriod(year+"-"+(year==2022?to.getYear():year+3),off,on));
        }
        return List.copyOf(result);
    }
    public PatternV3Run replay(String id) {return replay(id,null);}
    public PatternV3Run replay(String id,PatternV3Configuration configuration) {
        if(configuration==null)return replayOriginal(id);
        var source=store.run(id);if(source.datasetId()==null)throw new IllegalArgumentException("Exact input snapshot unavailable");
        configuration=configuration.forNewRun();configuration.validate(detectors);
        boolean sameTimeframe=source.signalTimeframe()==configuration.signalTimeframe();
        if(sameTimeframe)PatternV3RunComparison.requireSameTradingConfiguration(source.configuration(),configuration);
        else PatternV3RunComparison.requireSameSignalExperimentConfiguration(source.configuration(),configuration);
        var reference=sameTimeframe?findUnfilteredBaseline(source):null;
        return runOnSnapshot(configuration,store.dataset(source.datasetId()).bars(),reference,source.executionPolicy());
    }
    private PatternV3Run findUnfilteredBaseline(PatternV3Run source) {
        if(!source.configuration().patternRangeFilterEnabled()&&!source.configuration().closeStrengthFilterEnabled()&&!source.configuration().rvolEnabled()&&!source.configuration().bollingerAtrFilterEnabled()&&!source.configuration().marketRegimeFilterEnabled())return source;
        for(var candidate:store.runs())if(source.executionPolicy()==candidate.executionPolicy()&&Objects.equals(source.datasetId(),candidate.datasetId())&&!candidate.configuration().patternRangeFilterEnabled()&&!candidate.configuration().closeStrengthFilterEnabled()&&!candidate.configuration().rvolEnabled()&&!candidate.configuration().bollingerAtrFilterEnabled()&&!candidate.configuration().marketRegimeFilterEnabled()) {
            try {PatternV3RunComparison.requireSameTradingConfiguration(source.configuration(),candidate.configuration());return candidate;}catch(IllegalArgumentException incompatible) { /* Try another baseline. */ }
        }
        return null;
    }
    public PatternV3RunComparison.Comparison compare(List<String> ids) {
        if(ids==null||ids.size()<2||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select distinct runs");
        return PatternV3RunComparison.compare(ids.stream().map(store::run).toList());
    }
    public PatternV3Run replayCapitalLimits(String id,PatternV3Configuration configuration) {
        var source=store.run(id);if(source.datasetId()==null)throw new IllegalArgumentException("Exact input snapshot unavailable");
        configuration=configuration.forNewRun();configuration.validate(detectors);
        PatternV3CapitalComparison.requireSameConfiguration(source.configuration(),configuration);
        return runOnSnapshot(configuration,store.dataset(source.datasetId()).bars(),null,source.executionPolicy());
    }
    public PatternV3CapitalComparison.Comparison compareCapitalLimits(List<String> ids) {
        if(ids==null||ids.size()<2||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select distinct runs");
        return PatternV3CapitalComparison.compare(ids.stream().map(store::run).toList());
    }
    public PatternV3Store.Saved savePreset(PatternV3Configuration config) {config.validate(detectors);return store.saveConfiguration(config,false);}
    private PatternV3Run replayOriginal(String id) {
        var run=store.run(id);if(run.datasetId()==null)throw new IllegalArgumentException("Legacy run has no exact input snapshot");
        return runOnSnapshot(run.configuration(),store.dataset(run.datasetId()).bars(),null,run.executionPolicy());
    }
    /** Computes a disposable benchmark. Never calls V1 service/store.save or edits frozen beans. */
    public PatternV3Run benchmark(String id) {
        var run=store.run(id);var g=run.configuration().global();Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        for(var symbol:frozen.getSymbols().keySet())bars.put(symbol,historical.load(symbol,g.startDate(),g.endDate(),BarInterval.DAILY));
        var request=new PatternV1Request(null,g.startDate(),g.endDate(),BarInterval.DAILY,BigDecimal.valueOf(g.initialCapital()),null,null);
        var benchmark=new PatternV1PortfolioEngine(sizing,detectors).run(request,frozen,trading.getRisk(),bars);
        var result=run.withBenchmark(benchmark);store.save(result);return result;
    }
    public PatternV3Run result(String id){var run=store.run(id);return store.shadowAnalysis(id).map(run::withCapitalSkipShadowAnalysis).orElse(run);}
    public CapitalSkipShadowAnalysis.Analysis capitalSkipsAnalysis(String id){var run=store.run(id);if(run.capitalSkipAudits()==null)throw new IllegalArgumentException("Legacy run lacks decision context; replay its saved dataset first");var analysis=new CapitalSkipShadowAnalysis(sizing).analyze(run,store.dataset(run.datasetId()).bars());store.saveShadowAnalysis(id,analysis);return analysis;}
}
