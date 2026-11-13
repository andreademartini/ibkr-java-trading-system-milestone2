package it.andreademartini.trading.paper.multi;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.paper.patternv1.PatternPaperStore;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

/** Actual PAPER events only; shadow outcomes never enter PAPER sheets. */
@Service
public class PaperDeskExcelExporter {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private it.andreademartini.trading.monitor.ExecutionMonitor monitor;
    private Map<String,Object> identity(PaperDeskService.Strategy s){var row=new LinkedHashMap<String,Object>();row.put("strategyId",s.strategyId());row.put("strategyVersion",s.strategyVersion());row.put("paperSessionId",s.paperSessionId());row.put("strategyInstanceId",s.strategyInstanceId());row.put("accountId",s.accountId());row.put("configurationHash",s.configurationHash());return row;}
    private Map<String,Object> record(PaperDeskService.Strategy strategy,Object value,String signalId){var row=identity(strategy);row.put("strategyPositionId",signalId==null?null:strategy.strategyInstanceId()+":"+signalId);row.putAll(PatternStableStore.JSON.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));return row;}
    public byte[] export(PaperDeskService.Desk desk,List<Event> events,String selected,Instant from,Instant to){
        PaperDeskService.validateDates(from,to);if(selected!=null&&!Set.of("PATTERN_V1","PATTERN_V2").contains(selected))throw new IllegalArgumentException("Unsupported strategy");
        try(var book=new XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()){
            var configuration=new ArrayList<Map<String,Object>>();var strategies=new ArrayList<Map<String,Object>>();var signals=new ArrayList<Map<String,Object>>();var orders=new ArrayList<Map<String,Object>>();var executions=new ArrayList<Map<String,Object>>();var positions=new ArrayList<Map<String,Object>>();var closed=new ArrayList<Map<String,Object>>();var equity=new ArrayList<Map<String,Object>>();
            for(var strategy:desk.strategies())if(selected==null||selected.equals(strategy.strategyId())){
                var s=strategy.dashboard().session();var config=identity(strategy);config.put("configurationSnapshot",PatternPaperStore.json(strategy.configurationSnapshot()));if(strategy.regime()!=null){config.put("marketRegimeFilterEnabled",strategy.regime().enabled());config.put("marketRegimeFilterAdxPeriod",strategy.regime().adxPeriod());config.put("marketRegimeFilterAdxThreshold",strategy.regime().adxThreshold());config.put("marketRegimeFilterMode",strategy.regime().mode());}config.put("from",from);config.put("to",to);config.put("source","ACTUAL IBKR PAPER FILLS; no SHADOW/BACKTEST PnL");config.put("positionScope",to==null?"Current strategy ledger":"As-of positions rebuilt from actual fills; historical mark and holding bars unavailable");configuration.add(config);
                var state=identity(strategy);state.put("state",s.mode.name());state.put("killSwitch",s.killSwitch);state.put("initialCapital",s.initialCapital);state.put("equity",strategy.dashboard().capital().equity());state.put("reason",s.reason);strategies.add(state);
                for(var r:s.signals)if("PAPER".equals(r.mode)&&PaperDeskService.inside(r.generatedAt,from,to))signals.add(record(strategy,r,r.id));
                for(var r:s.orders)if(PaperDeskService.inside(r.createdAt,from,to)){var row=record(strategy,r,r.signalId);row.put("stateScope","Current order status; selected by submission time");row.put("commission",knownFees(s,s.executions.stream().filter(e->e.orderId()==r.orderId&&(to==null||!e.time().isAfter(to))).map(ExecutionRecord::executionId).toList(),to));orders.add(row);}
                for(var r:s.executions)if(PaperDeskService.inside(r.time(),from,to)){var row=record(strategy,r,r.signalId());row.put("commission",knownFees(s,List.of(r.executionId()),to));executions.add(row);}
                Session at=s;
                if(to!=null){at=PatternPaperStore.read(PatternPaperStore.json(s),Session.class);at.executions.clear();at.commissions.clear();at.trades.clear();at.pendingCommissions.clear();for(var f:s.executions)if(!f.time().isAfter(to))it.andreademartini.trading.paper.patternv1.PatternPaperLedger.execution(at,f);for(var f:s.commissions)if(f.reportedAt()!=null&&!f.reportedAt().isAfter(to))it.andreademartini.trading.paper.patternv1.PatternPaperLedger.commission(at,f);}
                for(var t:at.trades)if("PAPER".equals(t.mode)&&t.remaining().signum()>0&&(from==null||t.entryAt==null||!t.entryAt.isAfter(to==null?Instant.now():to))){var row=record(strategy,t,t.signalId);row.put("remainingQuantity",t.remaining());row.put("averageEntry",t.entryPrice());row.put("positionAsOf",to==null?Instant.now():to);if(to!=null){row.put("mark",null);row.put("unrealizedPnl",null);row.put("holdingBars",null);}else row.put("unrealizedPnl",t.unrealized());positions.add(row);}
                for(var t:s.trades)if("PAPER".equals(t.mode)&&t.closed()&&PaperDeskService.inside(t.exitAt,from,to)){var row=record(strategy,t,t.signalId);row.put("netPnl",t.netPnl());closed.add(row);}
                for(var p:s.equityCurve)if("PAPER".equals(p.mode())&&PaperDeskService.inside(p.time(),from,to))equity.add(record(strategy,p,null));
            }
            sheet(book,"PAPER_CONFIGURATION",configuration);sheet(book,"PAPER_STRATEGIES",strategies);sheet(book,"PAPER_SIGNALS",signals);sheet(book,"PAPER_ORDERS",orders);sheet(book,"PAPER_EXECUTIONS",executions);sheet(book,"PAPER_POSITIONS",positions);sheet(book,"PAPER_CLOSED_TRADES",closed);sheet(book,"PAPER_EQUITY_CURVES",equity);
            var comparison=new ArrayList<Map<String,Object>>();for(var m:desk.comparison().metrics()){var row=new LinkedHashMap<String,Object>();row.put("metric",m.metric());if(selected==null||selected.equals("PATTERN_V1"))row.put("V1",m.v1());if(selected==null||selected.equals("PATTERN_V2"))row.put("V2",m.v2());row.put("from",desk.comparison().from());row.put("to",desk.comparison().to());comparison.add(row);}if(comparison.isEmpty())comparison.add(new LinkedHashMap<>(Map.of("status",desk.comparison().reason())));sheet(book,"PAPER_COMPARISON",comparison);
            var errors=new ArrayList<Map<String,Object>>();for(var e:events)if((e.critical()||e.type().contains("ERROR"))&&(selected==null||selected.equals(e.strategyId())||e.strategyId().equals("PAPER_ACCOUNT"))&&PaperDeskService.inside(e.time(),from,to))errors.add(PatternStableStore.JSON.convertValue(e,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));sheet(book,"PAPER_ERRORS",errors);
            var recon=new ArrayList<Map<String,Object>>();for(var q:desk.reconciliation().positions()){var row=PatternStableStore.JSON.convertValue(q,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});row.put("accountId",desk.reconciliation().account());row.put("snapshotTime",desk.reconciliation().time());row.put("issues",String.join("; ",desk.reconciliation().issues()));row.put("scope","Current aggregate account snapshot; independent of historical event filter");recon.add(row);}if(recon.isEmpty()){var row=new LinkedHashMap<String,Object>();row.put("complete",desk.reconciliation().complete());row.put("issues",String.join("; ",desk.reconciliation().issues()));recon.add(row);}sheet(book,"PAPER_ACCOUNT_RECONCILIATION",recon);
            if(monitor!=null && (selected==null || selected.equals("PATTERN_V2")))it.andreademartini.trading.monitor.ExecutionMonitorController.sheet(book,monitor.rows().stream().filter(r->r.fill().executionTime()!=null && PaperDeskService.inside(r.fill().executionTime(),from,to)).toList());
            book.write(out);return out.toByteArray();
        }catch(java.io.IOException e){throw new IllegalStateException("PAPER export failed",e);}
    }
    private BigDecimal knownFees(Session s,List<String> executionIds,Instant to){
        if(executionIds.isEmpty())return null;var total=BigDecimal.ZERO;
        for(var id:executionIds){var fee=s.commissions.stream().filter(f->f.executionId().equals(id)&&(to==null||f.reportedAt()!=null&&!f.reportedAt().isAfter(to))).findFirst();if(fee.isEmpty())return null;total=total.add(fee.get().amount());}return total;
    }
    private void sheet(Workbook book,String name,List<Map<String,Object>> rows){var sheet=book.createSheet(name);var keys=new LinkedHashSet<String>();rows.forEach(r->keys.addAll(r.keySet()));if(keys.isEmpty())keys.add("No actual records in selection");var cols=List.copyOf(keys);var header=sheet.createRow(0);for(int i=0;i<cols.size();i++){header.createCell(i).setCellValue(cols.get(i));sheet.setColumnWidth(i,Math.min(18000,Math.max(4000,cols.get(i).length()*260)));}int n=1;for(var row:rows){var r=sheet.createRow(n++);for(int c=0;c<cols.size();c++){var v=row.get(cols.get(c));if(v==null)continue;var cell=r.createCell(c);if(v instanceof Number value)cell.setCellValue(value.doubleValue());else if(v instanceof Boolean value)cell.setCellValue(value);else {var text=v instanceof Map||v instanceof Collection?PatternPaperStore.json(v):v.toString();cell.setCellValue(text.length()>32767?text.substring(0,32767):text);}}}sheet.createFreezePane(0,1);if(n>1)sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,n-1,0,cols.size()-1));}
}
