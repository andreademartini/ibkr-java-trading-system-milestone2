package it.andreademartini.trading.monitor;

import org.springframework.web.bind.annotation.*;
import org.apache.poi.ss.usermodel.Workbook;
import java.util.*;

@RestController
@RequestMapping("/api/paper-trading/pattern-v2-monitor")
public class ExecutionMonitorController {
    private final ExecutionMonitor monitor;
    public ExecutionMonitorController(ExecutionMonitor monitor){this.monitor=monitor;}
    @GetMapping public Object dashboard(){return monitor.dashboard();}
    @PutMapping("/settings") public Object settings(@RequestBody ExecutionMonitor.Settings settings){monitor.configure(settings);return settings;}
    public static void sheet(Workbook book,List<ExecutionMonitor.Row> rows){
        var sheet=book.createSheet("PATTERN_V2_EXECUTION_MONITOR");
        var labels=List.of("Strategy Version","Symbol","Direction","Signal Timestamp UTC","Signal Price","Order Timestamp UTC","Order ID","Perm ID","Exec ID","Fill Timestamp UTC","Fill Price","Market Data Type","Source Market Timestamp","Estimated Feed Latency","Signal-to-Fill Deviation %","Quote-to-Fill Deviation %","Warning Level","Anomaly Reason");
        var header=sheet.createRow(0);for(int i=0;i<labels.size();i++)header.createCell(i).setCellValue(labels.get(i));
        int n=1;for(var r:rows){var s=r.signal();var o=r.order();var f=r.fill();
            Object[] values={s==null?null:s.version(),f.symbol(),s==null?null:s.direction(),s==null?null:s.generatedAt(),s==null?null:s.price(),o==null?null:o.sentAt(),f.orderId(),f.permId(),f.execId(),f.executionTime(),f.price(),s==null?"UNKNOWN":s.dataType(),s==null?null:s.sourceTime(),"LATENCY_UNKNOWN",r.deviation(),r.quoteDeviation(),r.states().contains("EXECUTION_DEVIATION_CRITICAL")?"CRITICAL":r.states().contains("EXECUTION_DEVIATION_WARNING")?"WARNING":"UNKNOWN",String.join(", ",r.states())};
            var row=sheet.createRow(n++);for(int i=0;i<values.length;i++){var cell=row.createCell(i);if(values[i] instanceof Number number)cell.setCellValue(number.doubleValue());else if(values[i]!=null)cell.setCellValue(values[i].toString());}
        }
        sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,n-1),0,labels.size()-1));
    }
    @GetMapping(value="/export",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") public byte[] export()throws java.io.IOException{
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()){sheet(book,monitor.rows());book.write(out);return out.toByteArray();}
    }
}
