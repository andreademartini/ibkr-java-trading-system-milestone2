package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Component
public class PatternPaperExcelExporter {
    public byte[] export(Dashboard dashboard,List<Event> allEvents){
        var s=dashboard.session();
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            rows(book,"CONFIGURATION",List.of(s.frozen,s.limits));
            rows(book,"ACCOUNT_STATUS",List.of(dashboard.connection()));
            rows(book,"SIGNALS",s.signals);rows(book,"ORDERS",s.orders);rows(book,"EXECUTIONS",s.executions);
            rows(book,"OPEN_POSITIONS",s.trades.stream().filter(t->t.remaining().signum()>0).map(t->tradeRow(t)).toList());
            rows(book,"CLOSED_TRADES",s.trades.stream().filter(Trade::closed).map(t->tradeRow(t)).toList());
            rows(book,"EQUITY_CURVE",s.equityCurve);rows(book,"DAILY_PERFORMANCE",daily(s));
            rows(book,"ERRORS",allEvents.stream().filter(Event::critical).toList());
            rows(book,"BACKTEST_VS_PAPER",s.trades.stream().map(t->tradeRow(t)).toList());
            rows(book,"EVENTS",allEvents);rows(book,"COMMISSIONS",s.commissions);rows(book,"CHECKS",dashboard.checks());
            rows(book,"SHADOW_TRADES",s.shadowTrades.stream().map(t->tradeRow(t)).toList());rows(book,"SHADOW_EQUITY",s.shadowEquity);
            book.write(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("PAPER Excel export failed",e);}
    }
    private Map<String,Object> tradeRow(Trade t){
        var values=new LinkedHashMap<String,Object>();values.put("Signal ID",t.signalId);values.put("Mode",t.mode);values.put("Symbol",t.symbol);values.put("Pattern",t.pattern);values.put("Direction",t.direction);values.put("Signal Date",t.signalDate);
        values.put("Expected Entry Price",t.expectedEntry);values.put("Actual PAPER Entry Price",t.entryPrice());values.put("Expected Quantity",t.expectedQuantity);values.put("Filled Quantity",t.entered);values.put("Remaining Quantity",t.remaining());values.put("Initial Stop",t.stop);values.put("Target",t.target);values.put("Actual Exit Price",t.closed()?t.exitPrice():null);values.put("Exit Reason",t.exitReason);
        values.put("Expected Slippage Percent",t.expectedSlippage);values.put("Observed Entry Slippage",t.expectedEntry==null?null:t.entryPrice().subtract(t.expectedEntry).multiply(t.direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate()));
        values.put("Expected Exit Price",t.expectedExit);values.put("Observed Exit Difference",t.expectedExit==null||!t.closed()?null:t.exitPrice().subtract(t.expectedExit));
        values.put("Commission",t.commission);values.put("Net PnL",t.netPnl());values.put("Holding Bars",t.holdingBars);return values;
    }
    private List<Map<String,Object>> daily(Session s){
        var days=new TreeMap<LocalDate,EquityPoint>();for(var point:s.equityCurve)days.put(point.time().atZone(ZoneId.of("America/New_York")).toLocalDate(),point);
        var result=new ArrayList<Map<String,Object>>();BigDecimal previous=s.initialCapital;
        for(var entry:days.entrySet()){var row=new LinkedHashMap<String,Object>();row.put("Date",entry.getKey());row.put("Mode","PAPER");row.put("Equity",entry.getValue().equity());row.put("Daily PnL",entry.getValue().equity().subtract(previous));row.put("Drawdown Percent",entry.getValue().drawdownPercent());result.add(row);previous=entry.getValue().equity();}return result;
    }
    private void rows(XSSFWorkbook book,String name,List<?> values){
        var sheet=book.createSheet(name);var nodes=values.stream().map(v->PatternStableStore.JSON.<com.fasterxml.jackson.databind.JsonNode>valueToTree(v)).toList();var columns=new LinkedHashSet<String>();nodes.forEach(n->n.fieldNames().forEachRemaining(columns::add));
        if(columns.isEmpty()){sheet.createRow(0).createCell(0).setCellValue("Nessun dato registrato");return;}
        var style=book.createCellStyle();var font=book.createFont();font.setBold(true);style.setFont(font);style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var labels=new ArrayList<>(columns);var header=sheet.createRow(0);for(int i=0;i<labels.size();i++){var cell=header.createCell(i);cell.setCellValue(labels.get(i));cell.setCellStyle(style);}
        for(int row=0;row<nodes.size();row++){
            var line=sheet.createRow(row+1);for(int col=0;col<labels.size();col++){var value=nodes.get(row).get(labels.get(col));if(value==null||value.isNull())continue;var cell=line.createCell(col);
                if(value.isNumber())cell.setCellValue(value.doubleValue());else if(value.isBoolean())cell.setCellValue(value.booleanValue());else{String text=value.isTextual()?value.textValue():value.toString();cell.setCellValue(text.length()>32767?text.substring(0,32767):text);}}
        }
        sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(1,nodes.size()),0,labels.size()-1));for(int col=0;col<labels.size();col++)sheet.setColumnWidth(col,Math.min(14000,Math.max(3500,labels.get(col).length()*300)));
    }
}
