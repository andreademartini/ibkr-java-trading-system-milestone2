package it.andreademartini.trading.backtest.patternv3;

import org.apache.poi.ss.usermodel.*;
import java.util.*;

/** V3-only diagnostics. Leaves every pre-existing sheet intact. */
public final class ResearchFilterExcel {
    public static final List<String> TRADE_HEADERS=List.of("CloseStrengthAtSignal","PatternRangePctAtSignal","SignalVolume","RelativeVolumeAtSignal","RelativeVolumeAverageAtSignal","PatternRangeFilterEnabled","RelativeVolumeFilterEnabled","RelativeVolumeIssue","CloseStrengthRule","CloseStrengthThreshold","PatternRangeMinPct","PatternRangeMaxPct","RelativeVolumePeriod","RelativeVolumeMin","RelativeVolumeMax","RelativeVolumeBasis");
    private ResearchFilterExcel(){}
    public static List<Object> tradeValues(SignalFilterAudit.Row s){return s==null?Arrays.asList(new Object[TRADE_HEADERS.size()]):Arrays.asList(s.closeStrength(),s.patternRangePct(),s.signalVolume(),s.relativeVolume(),s.relativeVolumeAverage(),s.patternRangeFilterEnabled(),s.relativeVolumeFilterEnabled(),s.relativeVolumeIssue(),s.closeStrengthRule(),s.closeStrengthThreshold(),s.patternRangeMinPct(),s.patternRangeMaxPct(),s.relativeVolumePeriod(),s.relativeVolumeMin(),s.relativeVolumeMax(),s.relativeVolumeBasis());}
    private static void row(Sheet sheet,Object...values){var row=sheet.createRow(sheet.getLastRowNum()+1);for(int i=0;i<values.length;i++){var c=row.createCell(i);Object v=values[i];if(v==null)continue;if(v instanceof Number n){if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Non-finite Excel filter value");c.setCellValue(n.doubleValue());}else if(v instanceof Boolean b)c.setCellValue(b);else c.setCellValue(v instanceof List<?> list?String.join("; ",list.stream().map(Object::toString).toList()):v.toString());}}
    private static Sheet sheet(Workbook book,String name,List<String> headers){var s=book.createSheet(name);var row=s.createRow(0);for(int i=0;i<headers.size();i++){row.createCell(i).setCellValue(headers.get(i));s.setColumnWidth(i,Math.min(16000,Math.max(4500,headers.get(i).length()*280)));}s.createFreezePane(0,1);return s;}
    public static void append(Workbook book,PatternV3Run run)throws ReflectiveOperationException{
        var components=SignalFilterAudit.Row.class.getRecordComponents();var headers=new ArrayList<String>();for(var c:components)headers.add(c.getName().equals("adx14")?"ADX14":Character.toUpperCase(c.getName().charAt(0))+c.getName().substring(1));
        var audit=sheet(book,"SIGNAL_FILTER_AUDIT",headers);
        if(run.signalFilterAudit()!=null)for(var s:run.signalFilterAudit()){var values=new ArrayList<Object>();for(var c:components)values.add(c.getAccessor().invoke(s));row(audit,values.toArray());}
        var analysis=sheet(book,"FILTER_ANALYSIS",List.of("Classification","Section","Stratum","Bucket","Thresholds","Trades","Observations","Missing","Wins","Losses","WinRatePercent","ProfitFactorNet","ExpectancyNetR","NetPnL","MaxLossNetR","GapStopCount"));
        row(analysis,ResearchFilterAnalysis.METHODOLOGY);
        if(run.signalFilterAudit()==null)row(analysis,"NOT_AVAILABLE — historical run without persisted filter audit");
        else for(var g:ResearchFilterAnalysis.groups(run)){var s=g.statistics();row(analysis,"DESCRIPTIVE_ONLY — NOT A PORTFOLIO BACKTEST",g.section(),g.stratum(),g.bucket(),g.thresholds(),s.trades(),s.observations(),s.missing(),s.wins(),s.losses(),s.winRatePercent(),s.profitFactorNet(),s.expectancyNetR(),s.netPnl(),s.maxLossNetR(),s.gapStopCount());}
    }
}
