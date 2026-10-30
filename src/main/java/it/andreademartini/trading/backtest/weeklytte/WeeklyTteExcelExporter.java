package it.andreademartini.trading.backtest.weeklytte;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;

@Service
public class WeeklyTteExcelExporter {
    public byte[] export(WeeklyTteRun run){
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            table(book,"WEEKLY_LEVEL_STATISTICS",run.weeklyLevels(),WeeklyTteRun.WeeklyLevel.class);
            if(run.configuration().historical()){
                table(book,"HISTORICAL_LEVELS",(List<?>)run.analysis().getOrDefault("historicalLevels",List.of()),WeeklyTteRun.HistoricalLevel.class);
                table(book,"DIRECTION_PERFORMANCE",(List<?>)run.analysis().getOrDefault("directionPerformance",List.of()),Map.class);
            }
            table(book,"TTE_SETUP_AUDIT",run.setups(),WeeklyTteRun.Setup.class);
            table(book,"TRADES",run.trades(),WeeklyTteRun.Trade.class);
            table(book,"EQUITY",run.equity(),WeeklyTteRun.Equity.class);
            var fills=new ArrayList<Map<String,Object>>();for(var t:run.trades())for(var f:t.exits()){var m=map(f);m.put("tradeId",t.tradeId());m.put("setupId",t.setupId());fills.add(m);}table(book,"EXIT_FILLS",fills,Map.class);
            var rows=new ArrayList<Map<String,Object>>();flatten("",run.analysis(),rows);table(book,"TTE_PERFORMANCE_ANALYSIS",rows,Map.class);
            rows=new ArrayList<>();flatten("configuration",map(run.configuration()),rows);
            rows.add(row("Strategy",run.strategy()));rows.add(row("RunId",run.runId()));rows.add(row("ConfigurationHash",run.configurationHash()));rows.add(row("DatasetHash",run.datasetId()));rows.add(row("CalendarVersion",run.calendarVersion()));
            rows.add(row("ResearchRulesVersion",run.configuration().historical()?WeeklyTteConfiguration.HISTORICAL:"WEEKLY_TTE/1"));for(var w:run.warnings())rows.add(row("Warning",w));table(book,"CONFIGURATION",rows,Map.class);
            table(book,"RUN_HISTORY",List.of(Map.of("runId",run.runId(),"timestamp",run.timestamp().toString(),"configurationHash",run.configurationHash(),"datasetId",run.datasetId(),"mode",run.configuration().mode().name())),Map.class);
            it.andreademartini.trading.backtest.reporting.PortfolioSummaryExcel.write(book,run);book.write(out);return out.toByteArray();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public byte[] comparison(Map<String,Object> comparison){try(var b=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
        var runs=(List<Map<String,Object>>)comparison.get("rows");
        var columns=new LinkedHashMap<String,Map<String,Object>>();
        for(var run:runs){String column=run.get("mode").toString();if(columns.containsKey(column))column+=" "+run.get("runId");columns.put(column,(Map<String,Object>)run.get("metrics"));}
        var metrics=new LinkedHashSet<String>();columns.values().forEach(m->metrics.addAll(m.keySet()));
        var rows=new ArrayList<Map<String,Object>>();for(String metric:metrics){var row=new LinkedHashMap<String,Object>();row.put("Metric",metric);columns.forEach((name,m)->row.put(name,m.get(metric)));rows.add(row);}
        table(b,"MODE_COMPARISON",rows,Map.class);
        rows=new ArrayList<>();flatten("comparison",comparison,rows);table(b,"COMPARISON_CONTEXT",rows,Map.class);
        b.write(out);return out.toByteArray();
    }catch(IOException e){throw new UncheckedIOException(e);}}
    private static Map<String,Object> map(Object o){return WeeklyTteStore.JSON.convertValue(o,new TypeReference<LinkedHashMap<String,Object>>(){});}
    private static Map<String,Object> row(String key,Object value){var m=new LinkedHashMap<String,Object>();m.put("Metric",key);m.put("Value",value);return m;}
    private static void flatten(String path,Object o,List<Map<String,Object>> rows){
        if(o instanceof Map<?,?> m)m.forEach((k,v)->flatten(path.isEmpty()?k.toString():path+"."+k,v,rows));
        else if(o instanceof List<?> l){for(int i=0;i<l.size();i++)flatten(path+"["+i+"]",l.get(i),rows);}
        else rows.add(row(path,o));
    }
    private static void table(XSSFWorkbook book,String name,List<?> objects,Class<?> type){
        var sheet=book.createSheet(name);var maps=objects.stream().map(o->o instanceof Map<?,?>?map(o):map(o)).toList();
        Set<String> fields=new LinkedHashSet<>();for(var m:maps)fields.addAll(m.keySet());
        if(fields.isEmpty()&&type!=Map.class){if(type.isRecord())for(var f:type.getRecordComponents())fields.add(f.getName());else for(var f:type.getFields())fields.add(f.getName());}
        var header=sheet.createRow(0);var style=book.createCellStyle();var font=book.createFont();font.setBold(true);style.setFont(font);
        var keys=new ArrayList<>(fields);for(int i=0;i<keys.size();i++){header.createCell(i).setCellValue(pascal(keys.get(i)));header.getCell(i).setCellStyle(style);sheet.setColumnWidth(i,Math.min(60,Math.max(18,keys.get(i).length()+2))*256);}
        int index=1;for(var m:maps){if(index>=1048576)throw new IllegalArgumentException("Excel row limit exceeded: "+name);var r=sheet.createRow(index++);
            for(int i=0;i<keys.size();i++){var cell=r.createCell(i);var v=m.get(keys.get(i));if(v==null)continue;if(v instanceof Number n&&Double.isFinite(n.doubleValue()))cell.setCellValue(n.doubleValue());else if(v instanceof Boolean flag)cell.setCellValue(flag);else {String text=v instanceof Map<?,?>||v instanceof List<?>?toJson(v):v.toString();if(text.length()>32767)throw new IllegalArgumentException("Excel cell limit: "+name);cell.setCellValue(text);}}
        }
        sheet.createFreezePane(0,1);if(!keys.isEmpty())sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,index-1),0,keys.size()-1));
    }
    private static String toJson(Object v){try{return WeeklyTteStore.JSON.writeValueAsString(v);}catch(IOException e){throw new UncheckedIOException(e);}}
    private static String pascal(String s){return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
