package it.andreademartini.trading.backtest.reporting;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class PortfolioSummaryTest {
 private final ObjectMapper json=new ObjectMapper();
 private JsonNode run()throws Exception{return json.readTree("""
 {"runId":"saved","strategy":"TREND_PULLBACK_REBOUND_V3_RESEARCH","strategyVersion":"3","configuration":{"startDate":"2010-01-01","endDate":"2025-01-01","initialCapital":1000,"riskPercent":0.5},
 "analysis":{},"signals":[{"status":"EXECUTED","diagnostics":{}},{"status":"REJECTED","reason":"INSUFFICIENT_CAPITAL_OR_RISK_BUDGET","diagnostics":{}}],
 "equity":[{"date":"2024-01-02","equity":1000,"cash":1000,"notional":0,"exposurePercent":0},{"date":"2024-07-02","equity":900,"cash":900,"notional":0,"exposurePercent":0},{"date":"2025-01-02","equity":1100,"cash":1000,"notional":100,"exposurePercent":10}],
 "trades":[{"entryDate":"2024-01-02","exitDate":"2024-01-02","entryBasePrice":10,"exitBasePrice":12,"quantity":10,"netPnL":15,"netR":0.5,"exitReason":"RECOVERY"},{"entryDate":"2024-07-02","exitDate":"2024-07-02","entryBasePrice":10,"exitBasePrice":9,"quantity":10,"netPnL":-15,"netR":-1,"exitReason":"TIMEOUT"},{"entryDate":"2025-01-02","quantity":10,"entryBasePrice":10,"lastMark":10,"unrealizedNetPnL":0}]}
 """);}
 private Double metric(PortfolioSummaryDto d,String key){return d.metrics().stream().filter(m->m.key().equals(key)).findFirst().orElseThrow().value();}
 @Test void canonicalFortyOrderedAndNotMutating()throws Exception{var r=run();var original=r.deepCopy();var d=PortfolioSummaryMapper.fromNode(r);assertEquals(40,d.metrics().size());assertEquals("Initial Capital",d.metrics().getFirst().label());assertEquals("Time In Market %",d.metrics().getLast().label());assertEquals(original,r);assertEquals("saved",d.context().get("Run ID"));assertEquals(0.5,d.context().get("Risk Percent"));}
 @Test void cagrUsesEffectiveCoverageAndDrawdownSameCurve()throws Exception{var d=PortfolioSummaryMapper.fromNode(run());assertEquals(100*(Math.pow(1.1,365.25/366)-1),metric(d,"cagr"),1e-8);assertEquals(100,metric(d,"maxDrawdown"));assertEquals(10,metric(d,"maxDrawdownPercent"));assertEquals("2024-01-02",d.context().get("Effective Start Date"));}
 @Test void distinctProfitFactorsAndClosedOutcomes()throws Exception{var d=PortfolioSummaryMapper.fromNode(run());assertEquals(2,metric(d,"profitFactor"));assertEquals(1,metric(d,"profitFactorNet"));assertEquals(0.5,metric(d,"profitFactorR"));assertEquals(-0.25,metric(d,"expectancyR"));assertEquals(metric(d,"closedTrades"),metric(d,"wins")+metric(d,"losses")+metric(d,"breakevens"));assertEquals(1,metric(d,"openTrades"));}
 @Test void longAccountingAndExposureNotTimeInMarket()throws Exception{var d=PortfolioSummaryMapper.fromNode(run());assertEquals(1000,metric(d,"finalCash"));assertEquals(1000,metric(d,"finalAvailableCash"));assertEquals(100,metric(d,"finalLongMarketValue"));assertEquals(0,metric(d,"finalShortMarketValue"));assertEquals(100,metric(d,"netPnl"));assertEquals(100,metric(d,"timeInMarketPercent"));assertEquals(10.0/3,metric(d,"averageGrossExposurePercent"));assertEquals(2,metric(d,"signalsDetected"));assertEquals(1,metric(d,"skippedCapital"));}
 @Test void missingAndHistoricalRunsDoNotInventZeros()throws Exception{var d=PortfolioSummaryMapper.fromNode(json.readTree("{\"strategy\":\"UNKNOWN\",\"runId\":\"old\"}"));assertEquals(40,d.metrics().size());assertTrue(d.metrics().stream().allMatch(m->m.value()==null&&m.reason()!=null));}
 @Test void nativeShortAccountingAndDistinctFactorsArePreserved()throws Exception{
 var r=json.readTree("""
 {"backtestId":"short","strategy":"PATTERN_V1","startDate":"2017-01-01","actualStartDate":"2024-01-01","actualEndDate":"2025-01-01","initialCapital":1000,"finalCash":1400,"finalAvailableCash":800,"finalLongMarketValue":0,"finalShortMarketValue":300,"finalOpenPositionValue":-300,"finalCapital":1100,"cagr":9.9,"maxDrawdown":200,"maxDrawdownPercent":20,"averageGrossExposurePercent":30,"timeInMarketPercent":70,"summary":{"profitFactor":2,"profitFactorNet":1.5,"profitFactorR":1.1,"winRate":0.6,"closedTrades":5,"wins":3,"losses":2,"breakevens":0}}
 """);
 var d=PortfolioSummaryMapper.fromNode(r);assertEquals(1100,metric(d,"finalCapital"));assertEquals(800,metric(d,"finalAvailableCash"));assertEquals(-300,metric(d,"finalOpenPositionValue"));assertEquals(100,metric(d,"netPnl"));assertEquals(60,metric(d,"winRate"));assertEquals(1.1,metric(d,"profitFactorR"));assertEquals(9.9,metric(d,"cagr"));
 }
 @Test void excelAndApiUseIdenticalNumericSource()throws Exception{var r=run();var dto=PortfolioSummaryMapper.fromNode(r);try(var book=new XSSFWorkbook()){book.createSheet("EXISTING");PortfolioSummaryExcel.write(book,r);assertNotNull(book.getSheet("EXISTING"));var s=book.getSheet("PORTFOLIO_SUMMARY");for(int i=0;i<40;i++){var m=dto.metrics().get(i);assertEquals(m.label(),s.getRow(i+1).getCell(0).getStringCellValue());if(m.value()!=null)assertEquals(m.value(),s.getRow(i+1).getCell(1).getNumericCellValue());else assertEquals("N/A",s.getRow(i+1).getCell(1).getStringCellValue());}}}
 @Test void wrappedV2ResearchUsesOwnSavedConfiguration()throws Exception{var outer=json.createObjectNode();outer.put("runId","v2");outer.put("strategy","TREND_PULLBACK_REBOUND_V2_RESEARCH");outer.set("portfolio",run());outer.set("configuration",run().path("configuration"));var d=PortfolioSummaryMapper.fromNode(outer);assertEquals(1100,metric(d,"finalCapital"));assertEquals("v2",d.context().get("Run ID"));assertEquals(3,metric(d,"trades"));}
 @Test void unknownAmbiguitiesRemainUnavailableAndNoPositionsAreZeroOnlyWhenKnown()throws Exception{var r=(com.fasterxml.jackson.databind.node.ObjectNode)run();r.put("strategy","TREND_PULLBACK_REBOUND_V1");assertNull(metric(PortfolioSummaryMapper.fromNode(r),"ambiguities"));r.set("trades",json.createArrayNode());var d=PortfolioSummaryMapper.fromNode(r);assertEquals(0,metric(d,"openTrades"));assertEquals(0,metric(d,"realizedNetPnl"));assertNull(metric(d,"profitFactorNet"));}
 @Test void weeklyRestrictedShortCashIsReportedAsLedgerCashWithoutChangingEquity()throws Exception{
 var r=(com.fasterxml.jackson.databind.node.ObjectNode)run();r.put("strategy","WEEKLY_HIGH_TTE_RESEARCH");r.set("setups",json.createArrayNode());r.set("trades",json.createArrayNode());((com.fasterxml.jackson.databind.node.ObjectNode)r.path("configuration")).put("rulesVersion","WEEKLY_UNTOUCHED/2");
 var last=(com.fasterxml.jackson.databind.node.ObjectNode)r.path("equity").get(2);last.put("cash",500);last.put("reservedCollateral",300);last.put("longNotional",200);last.put("shortNotional",200);
 var d=PortfolioSummaryMapper.fromNode(r);assertEquals(1100,metric(d,"finalCash"));assertEquals(500,metric(d,"finalAvailableCash"));assertEquals(1100,metric(d,"finalCapital"));assertEquals(0,metric(d,"finalOpenPositionValue"));assertEquals(100,metric(d,"netPnl"));
 }
}
