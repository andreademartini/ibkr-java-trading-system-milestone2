package it.andreademartini.trading.backtest.rebound;

import org.springframework.stereotype.Service;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import java.io.*;

/** Compact research report: original signals/trades, no repeated DAILY source archive. */
@Service
public class TrendReboundExcelExporter {
    private static Map<String,Object> map(Object value){return TrendReboundStore.JSON.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}
    public byte[] export(TrendReboundRun r){
        try(var book=new SXSSFWorkbook(100);var out=new ByteArrayOutputStream()){
            book.setCompressTempFiles(true);
            table(book,"DASHBOARD",parameters((Map<String,Object>)r.analysis().get("performance")),List.of("parameter","value"));
            var config=map(r.configuration());config.put("Strategy",r.strategy());config.put("StrategyVersion",r.strategyVersion());config.put("RunId",r.runId());config.put("RunTimestamp",r.timestamp());config.put("ConfigurationHash",r.configurationHash());config.put("DatasetFingerprint",r.datasetId());config.put("CalendarVersion",r.calendarVersion());config.put("Timeframe","DAILY");config.put("Direction","LONG");config.put("UseLeverage",false);config.put("ExecutionModel","OPEN_FIRST/1");
            var performance=(Map<String,Object>)r.analysis().get("performance");config.put("EffectiveStartDate",performance.get("effectiveStartDate"));config.put("EffectiveEndDate",performance.get("effectiveEndDate"));
            var excluded=new TreeSet<>(r.configuration().excludedSymbols());r.dataQuality().stream().filter(i->Set.of("NO_DATA","CSV_EXCLUDED","IDENTICAL_PRICE_VOLUME_DATA","EXCLUDED_INVALID_DATA","EXPLICIT_EXCLUSION").contains(i.code())).map(TrendReboundRun.DataIssue::symbol).forEach(excluded::add);config.put("ActualExcludedSymbols",excluded);config.put("EffectiveSymbols",r.configuration().symbols().stream().filter(s->!excluded.contains(s)).toList());
            var rows=parameters(config);String json=TrendReboundStore.JSON.writeValueAsString(r.configuration());for(int i=0;i<json.length();i+=30000)rows.add(row("parameter","ExactConfigurationJson["+(i/30000)+"]","value",json.substring(i,Math.min(json.length(),i+30000))));table(book,"CONFIGURATION",rows,List.of("parameter","value"));
            var signals=new ArrayList<Map<String,Object>>();for(var signal:r.signals()){var m=map(signal);m.put("runId",r.runId());fourCloses(m,signal.closes);signals.add(m);}table(book,"SIGNALS",signals,List.of("signalId","symbol","signalDate","plannedEntryDate","closes","trendSma","atr","status","reason"));
            var closed=new ArrayList<Map<String,Object>>();var open=new ArrayList<Map<String,Object>>();for(var t:r.trades()){var m=map(t);m.put("runId",r.runId());fourCloses(m,t.signalCloses());for(String key:List.of("entryBasePrice","entryExecutionPrice","exitBasePrice","exitExecutionPrice","initialStop","initialRisk","netPnL","netR","entryCommission","exitCommission","slippageCosts")){var v=m.get(key);m.put(key+"Exact",v==null?null:v.toString());}if(t.exitDate()==null)open.add(m);else closed.add(m);}
            table(book,"TRADES",closed,List.of("tradeId","signalId","symbol","entryDate","exitDate","netPnL","netR","exitReason"));table(book,"OPEN_POSITIONS",open,List.of("tradeId","symbol","quantity","initialStop","lastMark","unrealizedNetPnL","dataStatus"));
            table(book,"EQUITY",r.equity().stream().map(TrendReboundExcelExporter::map).toList(),List.of("date","equity","cash","notional","drawdownPercent"));
            var annual=new ArrayList<Map<String,Object>>();for(var x:(List<?>)r.analysis().get("annualResults")){var m=map(x);var metrics=(Map<String,Object>)m.remove("closedTradeMetrics");metrics.forEach((k,v)->m.put("closed_"+k,v));annual.add(m);}table(book,"ANNUAL_RESULTS",annual,List.of("year","openingEquity","closingEquity","portfolioNetPnL"));
            table(book,"RECONCILIATION",parameters((Map<String,Object>)r.analysis().get("reconciliation")),List.of("parameter","value"));table(book,"DATA_QUALITY",r.dataQuality().stream().map(TrendReboundExcelExporter::map).toList(),List.of("symbol","severity","code","detail"));
            var notes=new ArrayList<Map<String,Object>>();r.warnings().forEach(w->notes.add(row("note",w)));notes.add(row("note","N/A = unavailable/undefined. Profit factor requires losing closed trades. Warm-up is excluded from performance."));notes.add(row("note","RiskPercent is portfolio risk; the fixed price stop is ATR × multiplier. InitialRisk is quantity × entry-stop; sizingRiskWithCosts additionally reserves commissions and stop slippage."));notes.add(row("note","Slippage is already embedded in execution prices; do not subtract it twice. Closed PnL + open mark-to-market (including entry costs) reconciles final equity."));notes.add(row("note","Chronological signal closes are oldest to newest. LastFour fields are supplied for audit; when decliningCloses differs from 3, the complete closes array remains authoritative."));notes.add(row("note",r.analysis().get("priority")));table(book,"NOTES",notes,List.of("note"));it.andreademartini.trading.backtest.reporting.PortfolioSummaryExcel.write(book,r);book.write(out);return out.toByteArray();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static void fourCloses(Map<String,Object> row,List<Double> xs){for(int offset=3;offset>=0;offset--)row.put("closeTMinus"+offset,xs.size()>offset?xs.get(xs.size()-1-offset):null);}
    private static Map<String,Object> row(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),pairs[i+1]);return result;}
    private static ArrayList<Map<String,Object>> parameters(Map<String,Object> values){var rows=new ArrayList<Map<String,Object>>();values.forEach((k,v)->rows.add(row("parameter",k,"value",v)));return rows;}
    private static void table(SXSSFWorkbook book,String name,List<Map<String,Object>> values,List<String> fallback)throws IOException{
        var columns=new LinkedHashSet<>(fallback);values.forEach(m->columns.addAll(m.keySet()));var keys=List.copyOf(columns);int page=1,index=1;var sheet=sheet(book,name,keys);
        for(var m:values){if(index==1048576){sheet.setAutoFilter(new CellRangeAddress(0,index-1,0,keys.size()-1));sheet=sheet(book,name+"_"+(++page),keys);index=1;}var row=sheet.createRow(index++);for(int i=0;i<keys.size();i++){var cell=row.createCell(i);var value=m.get(keys.get(i));if(value==null)cell.setCellValue("N/A");else if(value instanceof Number n&&Double.isFinite(n.doubleValue()))cell.setCellValue(n.doubleValue());else if(value instanceof Boolean b)cell.setCellValue(b);else{String text=value instanceof Map<?,?>||value instanceof Collection<?>?TrendReboundStore.JSON.writeValueAsString(value):value.toString();if(text.length()>32767)throw new IllegalArgumentException("Excel cell exceeds text limit: "+keys.get(i));cell.setCellValue(text);}}}sheet.setAutoFilter(new CellRangeAddress(0,index-1,0,keys.size()-1));
    }
    private static Sheet sheet(SXSSFWorkbook book,String name,List<String> keys){var sheet=book.createSheet(name);sheet.createFreezePane(0,1);var header=sheet.createRow(0);var style=book.createCellStyle();var font=book.createFont();font.setBold(true);style.setFont(font);for(int i=0;i<keys.size();i++){var cell=header.createCell(i);cell.setCellValue(keys.get(i));cell.setCellStyle(style);sheet.setColumnWidth(i,Math.min(60,Math.max(18,keys.get(i).length()+2))*256);}return sheet;}
}
