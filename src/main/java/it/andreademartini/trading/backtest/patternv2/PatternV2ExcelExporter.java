package it.andreademartini.trading.backtest.patternv2;

import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class PatternV2ExcelExporter {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public byte[] export(PatternV2Run run) {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var p=run.portfolio();var summary=sheet(book,"SUMMARY","Metric","Value");
            row(summary,"Initial Capital",p.initialCapital());row(summary,"Final Equity",p.finalCapital());row(summary,"Total Return %",p.returnPercent());
            row(summary,"CAGR",p.cagr());row(summary,"Max Drawdown $",p.maxDrawdown());row(summary,"Max Drawdown %",p.maxDrawdownPercent());
            row(summary,"Average Gross Exposure %",p.averageGrossExposurePercent());row(summary,"Time In Market %",p.timeInMarketPercent());
            for(var c:PatternV1Result.Metrics.class.getRecordComponents())row(summary,c.getName(),c.getAccessor().invoke(p.summary()));
            row(summary,"Total Commissions",run.costs().commissions());row(summary,"Total Slippage",run.costs().slippage());
            row(summary,"Other Transaction Costs",run.costs().transactionCosts());row(summary,"Total Transaction Costs",run.costs().total());
            row(summary,"CAGR / Max Drawdown",p.cagr()!=null&&p.maxDrawdownPercent()>0?p.cagr()/p.maxDrawdownPercent():null);
            row(summary,"Average Actual Risk %",p.summary().averageActualRiskPercent());row(summary,"Average Order Value",p.summary().averageOrderValue());row(summary,"Capital Skips",p.summary().skippedCapital());
            boolean completeAudit=run.executions().stream().allMatch(e->e.capitalLimits()!=null);
            for(var constraint:List.of("MAX_POSITION_VALUE","MAX_LONG_NOTIONAL","MAX_SHORT_NOTIONAL")) {
                String label=switch(constraint){case "MAX_POSITION_VALUE"->"Max Position";case "MAX_LONG_NOTIONAL"->"Max LONG Notional";default->"Max SHORT Notional";};
                row(summary,"Number of Trades Limited by "+label,completeAudit?PatternV2CapitalComparison.limited(run,constraint,false):null);
                row(summary,"Number of Trades Binding at "+label,completeAudit?PatternV2CapitalComparison.limited(run,constraint,true):null);
            }
            row(summary,"Number of Trades Limited by Available Cash",completeAudit?run.executions().stream().filter(e->e.capitalLimits().cashLimited()).count():null);
            row(summary,"Capital Limits Audit Methodology","Reduction counts are sequential and overlapping; binding counts include tied constraints. Nominal stages precede cost/cash/minimum checks; final quantity includes all checks. Legacy missing audits are N/D.");
            var limitAudit=sheet(book,"CAPITAL_LIMITS_AUDIT","Trade ID","Constraint","Quantity Before","Quantity After","Maximum Quantity","Reduced","Binding");
            for(var execution:run.executions())if(execution.capitalLimits()!=null)for(var step:execution.capitalLimits().steps())row(limitAudit,execution.trade().tradeId(),step.constraint(),step.quantityBefore(),step.quantityAfter(),step.maximumQuantity(),step.reduced(),step.binding());
            groups(book,"SYMBOL_RESULTS",run.analysis().symbolResults(),run.patternRangeAnalysis());groups(book,"RESULTS_BY_TARGET_R",run.analysis().byTargetR());
            groups(book,"RESULTS_BY_PATTERN",run.analysis().byPattern());groups(book,"RESULTS_BY_SYMBOL",run.analysis().bySymbol());
            var components=PatternV1Result.Trade.class.getRecordComponents();var headers=new ArrayList<String>();
            for(var c:components)headers.add(c.getName());headers.addAll(List.of("entryTimestamp","exitTimestamp","entryPriceAfterSlippage","exitPriceAfterSlippage","commission","slippageCost","transactionCosts",
                    "entrySplitFactor","exitSplitFactor","entryQuantityExecutionEquivalent","exitQuantityExecutionEquivalent","entryPriceSplitUnadjusted","exitPriceSplitUnadjusted"));
            headers.addAll(List.of("Pattern High","Pattern Low","Pattern Close","Pattern Range","Pattern Range %","Pattern Range Filter Enabled","Minimum Pattern Range %","Pattern Range Accepted"));
            headers.addAll(List.of("PatternHigh","PatternLow","PatternClose","PatternRange","CloseStrength","CloseStrengthFilterEnabled","MaxCloseStrength","CloseStrengthAccepted"));
            headers.addAll(List.of("EquityAtEntry","MaxOrderValueEffective","MaxPositionValueEffective","MaxLongNotionalEffective","MaxShortNotionalEffective","DesiredQuantity","FinalQuantity","DesiredOrderValue","FinalOrderValue","ActualRiskPercent","QuantityLimitingReason"));
            headers.addAll(List.of("BollingerMiddle","BollingerUpper","BollingerLower","ATR14","BollingerDistanceATR","BollingerFilterAccepted","PatternExtremeTimestamp","PatternExtremePrice"));
            if(run.marketRegimeAnalysis()!=null)headers.addAll(List.of("SignalADX14","SignalPlusDI14","SignalMinusDI14","SignalEMA20","SignalEMA50","SignalEMA200","SignalATR14","SignalMarketRegime","SignalTrendDirection","SignalTrendStrength","SignalWithOrCounterTrend","SignalCloseAboveEMA20","SignalCloseAboveEMA50","SignalCloseAboveEMA200","SignalEMA20AboveEMA50","SignalEMA50AboveEMA200","NetR","SignalSecondaryRegime","SignalEMAWithOrCounterTrend","SignalPlusDIAboveMinusDI","WinLoss"));
            if(run.marketRegimeFilterAnalysis()!=null){if(run.marketRegimeAnalysis()==null)headers.addAll(List.of("SignalADX14","SignalPlusDI14","SignalMinusDI14","SignalMarketRegime"));headers.addAll(List.of("SignalTrendAlignment","MarketRegimeFilterEnabled","MarketRegimeFilterAccepted"));}
            var trades=sheet(book,"TRADES",headers.toArray(String[]::new));
            for(var execution:run.executions()) {
                List<Object> values=new ArrayList<>();for(var c:components)values.add(c.getAccessor().invoke(execution.trade()));
                values.add(execution.entryTimestamp());values.add(execution.exitTimestamp());values.add(execution.entryPriceAfterSlippage());values.add(execution.exitPriceAfterSlippage());
                values.add(execution.commission());values.add(execution.slippageCost());values.add(execution.transactionCosts());
                var split=execution.splitExecution();values.add(split==null?null:split.entryFactor());values.add(split==null?null:split.exitFactor());
                values.add(split==null?null:split.entryQuantityEquivalent());values.add(split==null?null:split.exitQuantityEquivalent());
                values.add(split==null?null:split.entryPriceSplitUnadjusted());values.add(split==null?null:split.exitPriceSplitUnadjusted());
                var range=execution.patternRange();
                values.add(range==null?null:range.high());values.add(range==null?null:range.low());values.add(range==null?null:range.close());
                values.add(range==null?null:range.range());values.add(range==null?null:range.percent());
                values.add(range==null?null:run.configuration().patternRangeFilterEnabled());values.add(range==null?null:run.configuration().minimumPatternRangePct());
                values.add(range==null?null:!run.configuration().patternRangeFilterEnabled()||range.reaches(run.configuration().minimumPatternRangePct()));values.add(range==null?null:range.high());values.add(range==null?null:range.low());values.add(range==null?null:range.close());values.add(range==null?null:range.range());values.add(execution.closeStrength());values.add(run.configuration().closeStrengthFilterEnabled());values.add(run.configuration().maxCloseStrength());values.add(execution.closeStrengthAccepted());
                var audit=execution.capitalLimits();var t=execution.trade();
                values.add(t.equityAtEntry());values.add(audit==null?null:audit.maxOrderValueEffective());values.add(audit==null?null:audit.maxPositionValueEffective());values.add(audit==null?null:audit.maxLongNotionalEffective());values.add(audit==null?null:audit.maxShortNotionalEffective());
                values.add(t.desiredQuantity());values.add(t.quantity());values.add(t.entryPrice().multiply(java.math.BigDecimal.valueOf(t.desiredQuantity())));values.add(t.orderValue());values.add(t.actualRiskPercent());values.add(t.quantityLimitingReason());
                var ba=execution.bollingerAtr();var bi=ba==null?null:ba.indicators();values.add(bi==null?null:bi.middle());values.add(bi==null?null:bi.upper());values.add(bi==null?null:bi.lower());values.add(bi==null?null:bi.atr());values.add(ba==null?null:ba.distanceAtr());values.add(ba==null?null:ba.accepted());values.add(ba==null?null:ba.extremeTimestamp());values.add(ba==null?null:ba.extremePrice());if(run.marketRegimeAnalysis()!=null){var mr=run.marketRegimeAnalysis().trades().stream().filter(r->r.tradeId().equals(t.tradeId())).findFirst().orElseThrow();var c=mr.context();var i=c.indicators();Collections.addAll(values,i.adx14(),i.plusDI14(),i.minusDI14(),i.ema20(),i.ema50(),i.ema200(),i.atr14(),c.marketRegime(),c.trendDirection(),c.trendStrength(),c.withOrCounterTrend(),c.closeAboveEMA20(),c.closeAboveEMA50(),c.closeAboveEMA200(),c.ema20AboveEMA50(),c.ema50AboveEMA200(),mr.netR(),c.secondaryRegime(),c.emaWithOrCounterTrend(),c.plusDIAboveMinusDI(),mr.winLoss());}if(run.marketRegimeFilterAnalysis()!=null){var signal=run.marketRegimeFilterAnalysis().signals().stream().filter(a->a.symbol().equals(t.symbol())&&a.pattern().equals(t.pattern().name())&&a.direction().equals(t.direction().name())&&a.signalIndex()==t.signalIndex()).findFirst().orElseThrow();var v=signal.indicators();if(run.marketRegimeAnalysis()==null)Collections.addAll(values,v==null?null:v.adx14(),v==null?null:v.plusDI14(),v==null?null:v.minusDI14(),signal.marketRegime());Collections.addAll(values,signal.trendAlignment(),signal.enabled(),signal.accepted());}row(trades,values.toArray());
            }
            var ba=run.bollingerAtrAnalysis();if(ba!=null){
                var bs=sheet(book,"BOLLINGER_ATR_ANALYSIS","Source / Metric","Group / Value","Signals","Trades","Wins","Losses","Win Rate %","PF Net","Expectancy R (price)","Expectancy Net R","Net PnL","Average Distance ATR Winners","Average Distance ATR Losers","Period CAGR %","Period Max DD %","Period PF Net","Period Expectancy Price R","Period Trades");
                row(bs,"METHODOLOGY",ba.source());bollingerRow(bs,"ACCEPTED EXECUTED TRADES",new BollingerAtrAnalysis.Group("ALL",ba.statistics().signalsAccepted(),ba.statistics().acceptedTrades()));for(var c:BollingerAtrAnalysis.Statistics.class.getRecordComponents())if(!c.getName().equals("acceptedTrades"))row(bs,c.getName(),c.getAccessor().invoke(ba.statistics()));
                for(var group:ba.buckets())bollingerRow(bs,"DISTANCE",group);for(var group:ba.symbols())bollingerRow(bs,"SYMBOL / DIRECTION / PATTERN / TARGET R",group);
                row(bs,"PERIODS","Continuous portfolio: CAGR / Max DD / PF Net / Expectancy Price R / Closed Trades");for(var period:ba.periods())row(bs,"PERIOD",period.label(),null,null,null,null,null,null,null,null,null,null,null,period.cagr(),period.maxDrawdownPercent(),period.pfNet(),period.expectancyR(),period.trades());
                var signals=sheet(book,"BOLLINGER_ATR_SIGNALS","Signal Date","Symbol","Direction","Pattern","Target R","Signal Index","Middle","Upper","Lower","ATR","PatternHigh","PatternLow","Extreme Timestamp","Extreme Price","Distance ATR","Accepted","Status","Reject Reason","Diagnostic");
                for(var a:run.bollingerAtrSignals()){var i=a.indicators();row(signals,a.signalDate(),a.symbol(),a.direction(),a.pattern(),a.targetR(),a.signalIndex(),i.middle(),i.upper(),i.lower(),i.atr(),a.patternHigh(),a.patternLow(),a.extremeTimestamp(),a.extremePrice(),a.distanceAtr(),a.accepted(),a.status(),a.rejectReason(),a.diagnostic());}
            }
            var equity=sheet(book,"EQUITY_CURVE","Timestamp","Date","Equity","Drawdown $","Drawdown %","Cash","Available Cash","Gross Exposure %","Open Positions");
            for(int i=0;i<p.equityCurve().size();i++) {var e=p.equityCurve().get(i);row(equity,run.equityTimestamps().get(i),e.date(),e.equity(),e.drawdown(),e.drawdownPercent(),e.cash(),e.availableCash(),e.grossExposurePercent(),e.openPositions());}
            var config=sheet(book,"CONFIGURATION","Parameter","Value");flatten(config,"",mapper.valueToTree(run.configuration()));if(!run.configuration().marketRegimeAnalysisEnabled())row(config,"marketRegimeAnalysisEnabled",false);
            if(!run.configuration().marketRegimeFilterEnabled())row(config,"marketRegimeFilterEnabled",false);if(run.configuration().marketRegimeFilterAdxPeriod()==14)row(config,"marketRegimeFilterAdxPeriod",14);if(run.configuration().marketRegimeFilterAdxThreshold()==25)row(config,"marketRegimeFilterAdxThreshold",25.0);row(config,"marketRegimeFilterMode",run.configuration().marketRegimeFilterMode());
            row(config,"PatternRangeFilterEnabled",run.configuration().patternRangeFilterEnabled());row(config,"MinimumPatternRangePct",run.configuration().minimumPatternRangePct());
            row(config,"CapitalSkipShadowAnalysisEnabled",run.configuration().capitalSkipShadowAnalysisEnabled());
            row(config,"CloseStrengthFilterEnabled",run.configuration().closeStrengthFilterEnabled());row(config,"MaxCloseStrength",run.configuration().maxCloseStrength());
            var periods=sheet(book,"PERIOD_ANALYSIS","Mode","Period","Start","End","Trades","Return %","CAGR","Max DD %","PF Net","Expectancy R","Win Rate","Net PnL");
            for(var e:run.analysis().periods())row(periods,"DEFAULT",e.label(),e.start(),e.end(),e.trades(),e.returnPercent(),e.cagr(),e.maxDrawdownPercent(),e.pfNet(),e.expectancyR(),e.winRate(),e.netPnl());
            for(var e:run.analysis().yearly())row(periods,"YEARLY",e.label(),e.start(),e.end(),e.trades(),e.returnPercent(),e.cagr(),e.maxDrawdownPercent(),e.pfNet(),e.expectancyR(),e.winRate());
            marketRegime(book,run);marketRegimeFilter(book,run);
            rangeAnalysis(book,run);closeStrengthAnalysis(book,run);capitalSkipAnalysis(book,run);
            if(run.patternRangeAnalysis()!=null) {
                for(var e:run.patternRangeAnalysis().baselinePeriods())row(periods,"BASELINE",e.label(),e.start(),e.end(),e.trades(),e.returnPercent(),e.cagr(),e.maxDrawdownPercent(),e.pfNet(),e.expectancyR(),e.winRate(),e.netPnl());
            }
            var info=sheet(book,"RUN_INFO","Field","Value");row(info,"Run ID",run.runId());row(info,"Strategy",run.strategy());row(info,"Timestamp",run.timestamp());
            row(info,"Dataset ID",run.datasetId());row(info,"Paired Run ID",run.pairedRunId());
            row(info,"Configuration Name",run.configuration().name());row(info,"Requested Start",p.startDate());row(info,"Requested End",p.endDate());
            row(info,"Actual Start",p.actualStartDate());row(info,"Actual End",p.actualEndDate());
            row(info,"Period methodology","Continuous portfolio equity, period-local peaks; closed trades assigned by exit date. Not independent re-funded period backtests.");
            row(info,"Group drawdown","Marked PnL attribution; not independent symbol portfolios");
            row(info,"PF convention","Null means no realized losses / undefined; winners use net PnL; price R excludes costs");
            row(info,"Share cost basis",run.configuration().splitCosts()==null?"LEGACY: per-share costs on dataset quantities, no split correction":"SPLIT_AWARE_V1: per-share rate divided by dated dataset split factor; minimum/order-value cap unchanged");
            row(info,"Split methodology","Execution-equivalent shares may be fractional. Split-unadjusted prices retain any dividend adjustment in the source. Quantity/maxQuantity/stop tick buffer remain in dataset units.");
            if(run.benchmark()!=null)row(info,"Benchmark","Read-only V1 snapshot on DAILY; ID: "+run.benchmark().backtestId());
            for(var warning:p.warnings())row(info,"Warning",warning);
            for(var s:book) {s.createFreezePane(0,1);if(s.getLastRowNum()>0)s.setAutoFilter(new CellRangeAddress(0,s.getLastRowNum(),0,s.getRow(0).getLastCellNum()-1));for(int c=0;c<s.getRow(0).getLastCellNum();c++)s.setColumnWidth(c,c==0?42*256:24*256);}
            book.write(out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("Export PATTERN_V2 fallito",e);}
    }
    private void marketRegimeFilter(Workbook book,PatternV2Run run) throws Exception {
        var a=run.marketRegimeFilterAnalysis();if(a==null)return;
        var summary=sheet(book,"MARKET_REGIME_FILTER_SUMMARY","Metric","Value");row(summary,"METHODOLOGY",a.methodology());
        for(var c:MarketRegimeFilter.Summary.class.getRecordComponents())row(summary,c.getName(),c.getAccessor().invoke(a.summary()));
        row(summary,"ACCEPTED EXECUTED TRADE CATEGORIES");
        row(summary,"Category","Trades","Winners","Losers","Win Rate %","Gross Profit","Gross Loss","PF Net","Net PnL","Expectancy Price R","Expectancy Net R","Average Winner Net R","Average Loser Net R","Average Holding Bars","Stop Loss Count","Take Profit Count","Timeout Count");
        for(var g:a.acceptedGroups()){var m=g.metrics();row(summary,g.key(),m.trades(),m.winners(),m.losers(),m.winRate(),m.grossProfit(),m.grossLoss(),m.pfNet(),m.netPnl(),m.expectancyPriceR(),m.expectancyNetR(),m.averageWinNetR(),m.averageLossNetR(),m.averageHoldingBars(),m.stopLossCount(),m.takeProfitCount(),m.timeoutCount());}
        String[] signalHeaders={"SignalDate","Symbol","Pattern","Direction","SignalIndex","ADX14","PlusDI14","MinusDI14","MarketRegime","TrendAlignment","FilterThreshold","FilterEnabled","FilterAccepted","RejectionReason"};
        var rejected=sheet(book,"MARKET_REGIME_REJECTIONS",signalHeaders);var signals=sheet(book,"MARKET_REGIME_FILTER_SIGNALS",signalHeaders);
        for(var r:a.signals()){var i=r.indicators();Object[] values={r.signalDate(),r.symbol(),r.pattern(),r.direction(),r.signalIndex(),i==null?null:i.adx14(),i==null?null:i.plusDI14(),i==null?null:i.minusDI14(),r.marketRegime(),r.trendAlignment(),r.filterThreshold(),r.enabled(),r.accepted(),r.rejectionReason()};row(signals,values);if(!r.accepted())row(rejected,values);}
        var symbols=sheet(book,"MARKET_REGIME_FILTER_SYMBOLS","Symbol","Pattern","Direction","Rejected Signals","Trades Baseline","Trades Filtered","Win Rate Baseline","Win Rate Filtered","PF Net Baseline","PF Net Filtered","Net PnL Baseline","Net PnL Filtered","Net Expectancy R Baseline","Net Expectancy R Filtered");
        for(var r:a.symbols()){var b=r.baseline();var f=r.filtered();row(symbols,r.symbol(),r.pattern(),r.direction(),r.rejectedSignals(),b==null?null:b.trades(),f.trades(),b==null?null:b.winRate(),f.winRate(),b==null?null:b.pfNet(),f.pfNet(),b==null?null:b.netPnl(),f.netPnl(),b==null?null:b.expectancyNetR(),f.expectancyNetR());}
        var periods=sheet(book,"MARKET_REGIME_FILTER_PERIODS","Type","Period","Rejected Signals","Trades Baseline","Trades Filtered","CAGR Baseline","CAGR Filtered","Max DD Baseline","Max DD Filtered","PF Net Baseline","PF Net Filtered","Net Expectancy R Baseline","Net Expectancy R Filtered","Win Rate Baseline","Win Rate Filtered","Net PnL Baseline","Net PnL Filtered","Return % Baseline","Return % Filtered");
        for(var r:a.periods()){var b=r.baseline();var f=r.filtered();row(periods,r.kind(),r.period(),r.rejectedSignals(),b==null?null:b.trades(),f.trades(),b==null?null:b.cagr(),f.cagr(),b==null?null:b.maxDrawdownPercent(),f.maxDrawdownPercent(),b==null?null:b.pfNet(),f.pfNet(),r.baselineNetExpectancyR(),r.filteredNetExpectancyR(),b==null||b.winRate()==null?null:b.winRate()*100,f.winRate()==null?null:f.winRate()*100,b==null?null:b.netPnl(),f.netPnl(),b==null?null:b.returnPercent(),f.returnPercent());}
        var comparison=sheet(book,"MARKET_REGIME_FILTER_COMPARISON","Metric","Baseline","Filtered","Difference");
        for(var r:a.comparison())row(comparison,r.metric(),r.baseline(),r.filtered(),r.difference());
    }
    private void marketRegime(Workbook book,PatternV2Run run) throws Exception {
        var a=run.marketRegimeAnalysis();if(a==null)return;
        var summary=regimeGroups(book,"MARKET_REGIME_SUMMARY",a.overview());row(summary,"METHODOLOGY",a.methodology());
        regimeGroups(book,"SECONDARY_REGIME_SUMMARY",a.secondaryOverview());regimeGroups(book,"ADX_BUCKETS",a.adxBuckets());
        var direction=regimeGroups(book,"TREND_DIRECTION_ANALYSIS",a.trendDirection());row(direction,"EMA50/EMA200 SECONDARY COMPARISON");regimeGroupRows(direction,a.emaTrendDirection());
        regimeGroups(book,"PATTERN_REGIME_ANALYSIS",a.patternRegimes());regimeGroups(book,"REGIME_PERIOD_ANALYSIS",a.periods());
        var losers=sheet(book,"LOSER_DIAGNOSTICS","Trade ID","Symbol","Pattern","Direction","Signal Date","Loss Type","Regime","ADX14","Trend Direction","Entry Stop Distance ATR","Price R","Net R","Net PnL","Holding Bars","Exit Reason");
        for(var r:a.trades())if(r.loserType()!=null)row(losers,r.tradeId(),r.symbol(),r.pattern(),r.direction(),r.signalDate(),r.loserType(),r.context().marketRegime(),r.context().indicators().adx14(),r.context().trendDirection(),r.entryStopDistanceAtr(),r.priceR(),r.netR(),r.netPnl(),r.holdingBars(),r.exitReason());
        var signals=sheet(book,"MARKET_REGIME_SIGNALS","Symbol","Pattern","Direction","Signal Date","Signal Index","Status","Reason","Trade ID","Market Regime","Secondary Regime","ADX14","PlusDI14","MinusDI14","EMA20","EMA50","EMA200","ATR14");
        for(var r:a.signals()){var i=r.context().indicators();row(signals,r.symbol(),r.pattern(),r.direction(),r.signalDate(),r.signalIndex(),r.status(),r.reason(),r.tradeId(),r.context().marketRegime(),r.context().secondaryRegime(),i.adx14(),i.plusDI14(),i.minusDI14(),i.ema20(),i.ema50(),i.ema200(),i.atr14());}
    }
    private Sheet regimeGroups(Workbook book,String name,List<MarketRegimeAnalysis.Group> groups) throws Exception {
        var headers=new ArrayList<>(List.of("Group","Symbol","Pattern","Direction","Regime","Signal Period"));for(var c:MarketRegimeAnalysis.Metrics.class.getRecordComponents())headers.add(c.getName());var s=sheet(book,name,headers.toArray(String[]::new));regimeGroupRows(s,groups);return s;
    }
    private void regimeGroupRows(Sheet sheet,List<MarketRegimeAnalysis.Group> groups) throws Exception {for(var r:groups){var values=new ArrayList<Object>(Arrays.asList(r.key(),r.symbol(),r.pattern(),r.direction(),r.regime(),r.period()));for(var c:MarketRegimeAnalysis.Metrics.class.getRecordComponents())values.add(c.getAccessor().invoke(r.metrics()));row(sheet,values.toArray());}}
    private void groups(Workbook book,String name,List<PatternV2Analysis.Group> groups) throws Exception {groups(book,name,groups,null);}
    private void groups(Workbook book,String name,List<PatternV2Analysis.Group> groups,PatternRangeAnalysis.Analysis research) throws Exception {
        var components=PatternV1Result.Metrics.class.getRecordComponents();var headers=new ArrayList<>(List.of("Key","Symbol","Direction","Pattern","Target R","Contribution %"));
        for(var c:components)headers.add(c.getName());
        if(research!=null)headers.addAll(List.of("Average PatternRangePct","Average PatternRangePct Winners","Average PatternRangePct Losers","Signals Rejected By Pattern Range","Accepted Signals","PF Net Accepted","Expectancy Accepted"));var sheet=sheet(book,name,headers.toArray(String[]::new));
        for(var g:groups) {List<Object> values=new ArrayList<>(Arrays.asList(g.key(),g.symbol(),g.direction(),g.pattern(),g.targetR(),g.contributionPercent()));for(var c:components)values.add(c.getAccessor().invoke(g.metrics()));
            if(research!=null) {
                var r=research.symbols().stream().filter(v->Objects.equals(v.symbol(),g.symbol())&&Objects.equals(v.direction(),g.direction())&&Objects.equals(v.pattern(),g.pattern())).findFirst().orElse(null);
                values.add(r==null?null:r.averagePatternRangePct());values.add(r==null?null:r.averagePatternRangePctWinners());values.add(r==null?null:r.averagePatternRangePctLosers());
                values.add(r==null?null:r.signalsRejectedByPatternRange());values.add(r==null?null:r.acceptedSignals());values.add(r==null?null:r.pfNetAccepted());values.add(r==null?null:r.expectancyAccepted());
            }row(sheet,values.toArray());}
    }
    private void rangeAnalysis(Workbook book,PatternV2Run run) {
        var a=run.patternRangeAnalysis();if(a==null)return;
        var s=sheet(book,"PATTERN_RANGE_ANALYSIS","Dataset / Type","Group","Signals","Trades","Wins","Losses","Win Rate","PF Net","Expectancy R","Net PnL","Open Trades","Value","Average PatternRangePct","Average PatternRangePct Winners","Average PatternRangePct Losers","Signals Rejected By Pattern Range","Accepted Signals","CAGR","Max Drawdown %","Start","End");
        var stats=a.statistics();row(s,"FILTER","Enabled",null,null,null,null,null,null,null,null,null,stats.enabled());
        row(s,"FILTER","Minimum Pattern Range %",null,null,null,null,null,null,null,null,null,stats.minimumPatternRangePct());
        row(s,"FILTER","Signals Before Filter",stats.signalsBeforeFilter());row(s,"FILTER","Accepted",stats.signalsAccepted());row(s,"FILTER","Rejected",stats.signalsRejected());
        row(s,"FILTER","Acceptance Rate %",null,null,null,null,null,null,null,null,null,stats.acceptanceRatePercent());
        for(var r:a.groups())researchRow(s,"BASELINE COHORT",r);
        for(var r:a.baselineBuckets())researchRow(s,"BASELINE BUCKET",r);
        for(var r:a.filteredBuckets())researchRow(s,"ACTUAL RUN BUCKET",r);
        for(var r:a.symbols())row(s,"ACTUAL SYMBOL",r.symbol()+" / "+r.direction(),null,null,null,null,null,r.pfNetAccepted(),r.expectancyAccepted(),null,null,null,r.averagePatternRangePct(),r.averagePatternRangePctWinners(),r.averagePatternRangePctLosers(),r.signalsRejectedByPatternRange(),r.acceptedSignals());
        for(var r:run.analysis().periods())row(s,"ACTUAL PERIOD",r.label(),null,r.trades(),null,null,r.winRate(),r.pfNet(),r.expectancyR(),r.netPnl(),null,null,null,null,null,null,null,r.cagr(),r.maxDrawdownPercent(),r.start(),r.end());
        for(var r:a.baselinePeriods())row(s,"BASELINE PERIOD",r.label(),null,r.trades(),null,null,r.winRate(),r.pfNet(),r.expectancyR(),r.netPnl(),null,null,null,null,null,null,null,r.cagr(),r.maxDrawdownPercent(),r.start(),r.end());
        var audit=sheet(book,"PATTERN_RANGE_SIGNALS","Symbol","Direction","Pattern","Signal Date","Signal Timestamp","Pattern High","Pattern Low","Pattern Close","Pattern Range","Pattern Range %","Filter Enabled","Minimum Pattern Range %","Accepted","Reject Reason","Baseline Trade ID","Baseline Status","Baseline Result R","Baseline Net PnL");
        for(var o:a.baselineOutcomes()) {var q=o.signal();var r=q.range();row(audit,q.symbol(),q.direction(),q.pattern(),q.signalDate(),q.signalTimestamp(),r.high(),r.low(),r.close(),r.range(),r.percent(),q.filterEnabled(),q.minimumPatternRangePct(),q.accepted(),q.rejectReason(),o.baselineTradeId(),o.status(),o.resultR(),o.netPnl());}
        if(!a.comparison().isEmpty()) {
            var comparison=sheet(book,"BASELINE_COMPARISON","Metric","V2 Baseline",run.configuration().closeStrengthFilterEnabled()?run.configuration().name():"Range >= "+run.configuration().minimumPatternRangePct()+"%","Difference");
            for(var m:a.comparison())row(comparison,m.metric(),m.baseline(),m.filtered(),m.difference());
        }
        row(s,"METHODOLOGY","Cohorts use real baseline trades. Signals with no baseline trade have no economic outcome. Open trades excluded from outcome metrics.");
        row(s,"METHODOLOGY","Expectancy R uses existing price R excluding costs; PF Net uses closed trades after costs.");
    }
    private void closeStrengthAnalysis(Workbook book,PatternV2Run run) throws Exception {
        var a=run.closeStrengthAnalysis();if(a==null)return;
        var s=sheet(book,"CLOSE_STRENGTH_ANALYSIS","Source","Group / Metric","Symbol","Direction","Pattern","Target R","Signals","Trades (closed)","Wins","Losses","Win Rate","PF Net","Expectancy R","Net PnL","Average Winner R","Average Loser R","Average CloseStrength Winners","Average CloseStrength Losers","Rejected CloseStrength","Invalid Signals","Value");
        for(var c:CloseStrengthAnalysis.Statistics.class.getRecordComponents())row(s,"FILTER",c.getName(),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,c.getAccessor().invoke(a.statistics()));
        for(var b:a.actualBuckets())closeBucket(s,"ACTUAL RUN "+run.runId(),b);
        for(var b:a.baselineBuckets())closeBucket(s,"BASELINE REAL TRADES "+a.baselineRunId(),b);
        for(var g:a.symbols()){var m=g.metrics();row(s,"ACTUAL SYMBOL",g.symbol()+" / "+g.direction()+" / "+g.pattern()+" / "+g.targetR(),g.symbol(),g.direction(),g.pattern(),g.targetR(),null,m.trades(),m.wins(),m.losses(),m.winRate(),m.profitFactorNet(),m.expectancyR(),m.netPnl(),m.averageWinnerR(),m.averageLoserR(),g.averageCloseStrengthWinners(),g.averageCloseStrengthLosers(),g.signalsRejectedByCloseStrength(),g.invalidSignals(),g.tradesAccepted());}
        row(s,"METHODOLOGY","Closed actual / baseline trades only. Missing or open baseline outcomes N/D. Range first; CloseStrength evaluated only after Range passes. Price R excludes costs.");
        var filtered=sheet(book,"FILTERED_SIGNALS","Signal Date","Timestamp","Symbol","Direction","Pattern","TargetR","PatternHigh","PatternLow","PatternClose","PatternRange","CloseStrength","MaxCloseStrength","Status","RejectReason","Baseline Trade ID","Baseline Status","Baseline R","Baseline Net PnL");
        for(var o:a.outcomes()){var q=o.signal();if(q.rejectReason()==null)continue;var r=q.range();row(filtered,q.signalDate(),q.signalTimestamp(),q.symbol(),q.direction(),q.pattern(),q.targetR(),r.high(),r.low(),r.close(),r.range(),q.closeStrength(),q.maxCloseStrength(),q.status(),q.rejectReason(),o.baselineTradeId(),o.baselineStatus(),o.resultR(),o.netPnl());}
        var symbols=book.getSheet("SYMBOL_RESULTS");int offset=symbols.getRow(0).getLastCellNum();String[] labels={"Average CloseStrength Winners","Average CloseStrength Losers","Signals Rejected By CloseStrength","Trades Accepted","PF Net CloseStrength","Expectancy R CloseStrength","Net PnL CloseStrength"};
        for(int i=0;i<labels.length;i++)symbols.getRow(0).createCell(offset+i).setCellValue(labels[i]);
        for(int n=0;n<run.analysis().symbolResults().size();n++){var g=run.analysis().symbolResults().get(n);var c=a.symbols().stream().filter(v->Objects.equals(v.symbol(),g.symbol())&&Objects.equals(v.direction(),g.direction())&&Objects.equals(v.pattern(),g.pattern())&&v.targetR().doubleValue()==g.targetR()).findFirst().orElse(null);if(c==null)continue;Object[] values={c.averageCloseStrengthWinners(),c.averageCloseStrengthLosers(),c.signalsRejectedByCloseStrength(),c.tradesAccepted(),c.metrics().profitFactorNet(),c.metrics().expectancyR(),c.metrics().netPnl()};for(int i=0;i<values.length;i++){var cell=symbols.getRow(n+1).createCell(offset+i);if(values[i] instanceof Number number)cell.setCellValue(number.doubleValue());}}
    }
    private void closeBucket(Sheet sheet,String source,CloseStrengthAnalysis.Bucket b){var m=b.metrics();row(sheet,source,b.bucket(),null,null,null,null,b.signals(),m.trades(),m.wins(),m.losses(),m.winRate(),m.profitFactorNet(),m.expectancyR(),m.netPnl(),m.averageWinnerR(),m.averageLoserR(),null,null,null,b.invalidSignals());}
    private void capitalSkipAnalysis(Workbook book,PatternV2Run run) throws Exception {
        var a=run.capitalSkipShadowAnalysis();if(a==null)return;
        var summary=sheet(book,"CAPITAL_SKIPS_SUMMARY","Section","Metric","Executed Trades","Shadow Capital Skips","Value");
        row(summary,"CONFIG","Analysis Available",null,null,true);row(summary,"CONFIG","Analysis Mode",null,null,run.configuration().capitalSkipShadowAnalysisEnabled()?"AFTER_BACKTEST":"SEPARATE");
        row(summary,"CONFIG","CapitalSkipShadowAnalysisEnabled",null,null,run.configuration().capitalSkipShadowAnalysisEnabled());
        row(summary,"CONFIG","Configuration",null,null,mapper.writeValueAsString(a.configuration()));row(summary,"SOURCE","Run ID",null,null,a.runId());row(summary,"SOURCE","Dataset ID",null,null,a.datasetId());
        row(summary,"COUNTS","Total Capital Skips",null,null,a.totalCapitalSkips());row(summary,"COUNTS","Completed Shadow Trades",null,null,a.completedShadowTrades());row(summary,"COUNTS","Incomplete Shadow Trades",null,null,a.incompleteShadowTrades());row(summary,"COUNTS","Not Executable",null,null,a.notExecutableTrades());
        for(var m:a.comparison())row(summary,"COMPARISON",m.metric(),m.executed(),m.shadow());row(summary,"CLASSIFICATION",a.classification(),null,null,a.classificationReason());row(summary,"METHODOLOGY",a.methodology());
        var components=CapitalSkipShadowAnalysis.ShadowTrade.class.getRecordComponents();var names=Arrays.stream(components).map(c->shadowHeader(c.getName())).toArray(String[]::new);var trades=sheet(book,"SHADOW_TRADES",names);
        for(var t:a.trades()){List<Object> values=new ArrayList<>();for(var c:components)values.add(c.getAccessor().invoke(t));row(trades,values.toArray());}
        var symbols=sheet(book,"CAPITAL_SKIPS_BY_SYMBOL","Symbol","Direction","Pattern","Target R","Executed Trades","Skipped Capital Signals","Completed Shadow Trades","Incomplete","Not Executable","Shadow Wins","Shadow Losses","Shadow Win Rate","Shadow PF Gross","Shadow PF Net","Shadow Gross Expectancy R","Shadow Net Expectancy R","Shadow Average Net PnL","Shadow Total Net PnL");
        for(var g:a.symbols()){var m=g.shadow();row(symbols,g.symbol(),g.direction(),g.pattern(),g.targetR(),g.executedTrades(),g.skippedCapitalSignals(),m.trades(),g.incompleteTrades(),g.notExecutableTrades(),m.wins(),m.losses(),m.winRate(),m.pfGross(),m.pfNet(),m.grossExpectancyR(),m.netExpectancyR(),m.averageNetPnl(),m.totalNetPnl());}
        var periods=sheet(book,"CAPITAL_SKIPS_BY_PERIOD","Period","Skipped Signals","Completed Shadow Trades","Incomplete","Not Executable","Shadow Win Rate","Shadow PF Gross","Shadow PF Net","Shadow Gross Expectancy R","Shadow Net Expectancy R","Shadow Average Net PnL");
        for(var g:a.periods()){var m=g.shadow();row(periods,g.period(),g.skippedSignals(),g.completedShadowTrades(),g.incompleteTrades(),g.notExecutableTrades(),m.winRate(),m.pfGross(),m.pfNet(),m.grossExpectancyR(),m.netExpectancyR(),m.averageNetPnl());}
        var context=sheet(book,"CAPITAL_SKIPS_CONTEXT","Signal ID","Signal Timestamp","Decision Timestamp","Symbol","Direction","Pattern","Target R","EquityReferenceAtSignal","Available Cash","Invested Capital","Equity At Decision","Open Positions","Aggregate Open Risk","Signals Competing At Same Timestamp","Processing Order","Capital Required By Rejected Signal","Capital Shortfall","Positions JSON","Ordered Candidates JSON","Reject Reason");
        for(var q:a.contexts()){var c=q.context();var d=q.signal().detection();row(context,q.signalId(),q.signalTimestamp(),q.decisionTimestamp(),q.signal().symbol(),d.direction(),d.patternType(),q.signal().targetR(),q.equityReferenceAtSignal(),c.availableCash(),c.investedCapital(),c.equity(),c.openPositions(),c.aggregateOpenRisk(),c.competingSignals().size(),c.processingOrder(),c.capitalRequired(),c.capitalShortfall(),mapper.writeValueAsString(c.positions()),mapper.writeValueAsString(c.competingSignals()),q.rejectReason());}
    }
    private String shadowHeader(String field){return switch(field){case "signalId"->"Signal ID";case "targetR"->"Target R";case "equityReferenceAtSignal"->"Reference Equity";case "shadowQuantity"->"Quantity";case "shadowOrderValue"->"Order Value";case "actualShadowRisk"->"Initial Risk";case "totalCommission"->"Commission";case "grossPnl"->"Gross PnL";case "netPnl"->"Net PnL";case "grossResultR"->"Gross Result R";case "netResultR"->"Net Result R";case "mfeR"->"MFE R";case "maeR"->"MAE R";default->Character.toUpperCase(field.charAt(0))+field.substring(1).replaceAll("([A-Z])"," $1");};}
    private void researchRow(Sheet sheet,String source,PatternRangeAnalysis.Row r) {row(sheet,source,r.group(),r.signals(),r.trades(),r.wins(),r.losses(),r.winRate(),r.pfNet(),r.expectancyR(),r.netPnl(),r.openTrades());}
    private void flatten(Sheet sheet,String path,JsonNode node) {
        if(node.isObject())node.fields().forEachRemaining(e->flatten(sheet,path.isEmpty()?e.getKey():path+"."+e.getKey(),e.getValue()));
        else if(node.isArray()) {for(int i=0;i<node.size();i++)flatten(sheet,path+"["+i+"]",node.get(i));}
        else row(sheet,path,node.isNull()?null:node.isNumber()?node.numberValue():node.isBoolean()?node.booleanValue():node.asText());
    }
    private static void bollingerRow(Sheet s,String source,BollingerAtrAnalysis.Group group){var m=group.metrics();row(s,source,group.label(),group.signals(),m.trades(),m.wins(),m.losses(),m.winRate(),m.pfNet(),m.expectancyR(),m.expectancyNetR(),m.netPnl(),m.averageDistanceAtrWinners(),m.averageDistanceAtrLosers());}
    private static Sheet sheet(Workbook book,String name,String...headers) {var s=book.createSheet(name);row(s,(Object[])headers);var style=book.createCellStyle();var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());style.setFont(font);style.setFillForegroundColor(IndexedColors.VIOLET.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);for(var c:s.getRow(0))c.setCellStyle(style);return s;}
    private static void row(Sheet sheet,Object...values) {var r=sheet.createRow(sheet.getLastRowNum()==0&&sheet.getRow(0)==null?0:sheet.getLastRowNum()+1);for(int i=0;i<values.length;i++){var c=r.createCell(i);var v=values[i];if(v instanceof Number n)c.setCellValue(n.doubleValue());else if(v instanceof Boolean b)c.setCellValue(b);else if(v!=null)c.setCellValue(v.toString());}}
}
