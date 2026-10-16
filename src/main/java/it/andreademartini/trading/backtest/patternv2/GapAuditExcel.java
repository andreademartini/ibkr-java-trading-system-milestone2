package it.andreademartini.trading.backtest.patternv2;

import org.apache.poi.ss.usermodel.*;
import java.util.*;

/** Additive export of persisted audit only; absent data stays blank, never reconstructed. */
public final class GapAuditExcel {
    private GapAuditExcel() {}
    public static final List<String> HEADERS=List.of("ExitBarOpen","GapExitType","GapSizePercent","GapSizeR",
        "ExitBasePrice","ExitExecutionPrice","InitialStopPrice","TriggerLevel","ExitSlippageAmount","GapExitFlag",
        "InitialRiskPerShareExecution","InitialRiskPerSharePriceBasis","GapSizeRPriceBasis","GapAuditIssue");
    public static List<Object> values(GapExecutionAudit.TradeAudit a){
        if(a==null)return new ArrayList<>(Collections.nCopies(HEADERS.size(),null));
        return Arrays.asList(a.exitBarOpen(),a.gapExitType(),a.gapSizePercent(),a.gapSizeR(),a.exitBasePrice(),a.exitExecutionPrice(),
            a.initialStopPrice(),a.triggerLevel(),a.exitSlippageAmount(),a.gapExitFlag(),a.initialRiskPerShareExecution(),
            a.initialRiskPerSharePriceBasis(),a.gapSizeRPriceBasis(),a.issue());
    }
    public static void append(Workbook book,Sheet info,PatternV2Run run) throws Exception {
        append(book,info,it.andreademartini.trading.backtest.execution.ExecutionRunView.of(run));
    }
    public static void append(Workbook book,Sheet info,it.andreademartini.trading.backtest.execution.ExecutionRunView run) throws Exception {
        var audit=run.executionAudit();
        row(info,"Execution Audit Available",audit!=null);
        row(info,"Execution Audit Schema",audit==null?"N/D":audit.schemaVersion());
        row(info,"Execution Model Version",audit==null?"N/D":audit.executionModelVersion());
        row(info,"Slippage Model",audit==null?"N/D":audit.slippageModel());
        row(info,"Slippage Percent Per Side",audit==null?null:audit.slippagePercentPerSide());
        row(info,"Price Adjustment Model",audit==null?"N/D":audit.priceAdjustmentModel());
        row(info,"GapSizePercent definition","Directional distance beyond STOP/TARGET, not previous Close. NONE has blank size; equality gives 0.");
        row(info,"GapSizeR definition","abs(ExitBarOpen - TriggerLevel) / abs(EntryExecutionPrice - InitialStopPrice). Existing Price R stays before slippage.");
        row(info,"GapSizeRPriceBasis definition","abs(ExitBarOpen - TriggerLevel) / initial riskPerShare used by existing Price R and sizing.");
        row(info,"ExitSlippageAmount units","USD per dataset share, exit only. slippageCost is total entry + exit slippage for the trade.");
        row(info,"Gap analysis methodology","Actual closed trades by exit date; no counterfactual benefit. Costs include both entry/exit of gap trades. Incidence denominator is sum(abs(grossPnl)) of gap trades. Undefined ratios blank; missing historical audit N/D.");
        if(audit!=null){
            for(var e:new TreeMap<>(audit.priceBasisBySymbol()).entrySet())row(info,"Price Basis "+e.getKey(),e.getValue().toString());
            for(var limitation:audit.limitations())row(info,"Execution Model Limitation",limitation);
        }
        var sheet=book.createSheet("GAP_EXIT_ANALYSIS");
        List<Object> headers=new ArrayList<>(List.of("Section","Group"));
        for(var c:GapExitAnalysis.Stats.class.getRecordComponents())headers.add(c.getName());
        row(sheet,headers.toArray());
        for(var group:GapExitAnalysis.groups(run)){
            List<Object> values=new ArrayList<>(List.of(group.section(),group.group()));
            for(var c:GapExitAnalysis.Stats.class.getRecordComponents())values.add(c.getAccessor().invoke(group.statistics()));
            row(sheet,values.toArray());
        }
    }
    private static void row(Sheet sheet,Object...values){
        var row=sheet.createRow(sheet.getPhysicalNumberOfRows());
        for(int i=0;i<values.length;i++){
            var cell=row.createCell(i);var value=values[i];
            if(value instanceof Number n){if(Double.isFinite(n.doubleValue()))cell.setCellValue(n.doubleValue());}
            else if(value instanceof Boolean b)cell.setCellValue(b);
            else if(value!=null)cell.setCellValue(value.toString());
        }
    }
}
