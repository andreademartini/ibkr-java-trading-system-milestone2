package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Actual executions/commissions form the ledger; account NetLiquidation is never strategy equity. */
public final class PatternPaperLedger {
    private PatternPaperLedger() {}
    public static boolean execution(Session s,ExecutionRecord fill){
        var order=s.orders.stream().filter(o->o.orderId==fill.orderId()).findFirst().orElseThrow(()->new IllegalStateException("Execution without durable order intent"));
        var economic=new ExecutionRecord(fill.executionId(),order.signalId,fill.orderId(),fill.permId(),fill.account(),fill.symbol(),fill.side(),fill.quantity(),fill.price(),fill.time(),order.role);
        var prior=s.executions.stream().filter(e->e.executionId().equals(fill.executionId())).findFirst();
        if(prior.isPresent()){
            if(!prior.get().equals(economic))throw new IllegalStateException("Execution ID changed: "+fill.executionId());
            return false;
        }
        if(!s.account.equals(fill.account()))throw new IllegalStateException("Execution account mismatch");
        if(!order.symbol.equals(fill.symbol())||!order.action.equals(fill.side())||fill.quantity().signum()<=0||fill.price().signum()<=0)throw new IllegalStateException("Execution economic identity mismatch");
        var sameOrder=s.executions.stream().filter(e->e.orderId()==fill.orderId()).map(ExecutionRecord::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sameOrder.add(fill.quantity()).compareTo(BigDecimal.valueOf(order.quantity))>0)throw new IllegalStateException("Overfill detected");
        if(order.role!=Role.ENTRY){var available=s.trades.stream().filter(t->t.signalId.equals(order.signalId)).findFirst().map(Trade::remaining).orElse(BigDecimal.ZERO);
            if(fill.quantity().compareTo(available)>0)throw new IllegalStateException("Exit exceeds actual entry fills; reconciliation required");}
        s.executions.add(economic);order.permId=fill.permId();
        s.pendingCommissions.stream().filter(c->c.executionId().equals(fill.executionId())).findFirst().ifPresent(c->{
            s.commissions.removeIf(existing->existing.executionId().equals(c.executionId()));s.commissions.add(c);
        });
        s.pendingCommissions.removeIf(c->c.executionId().equals(fill.executionId()));
        rebuild(s,order.signalId);return true;
    }
    public static boolean commission(Session s,CommissionRecord fee){
        if(!"USD".equals(fee.currency())||fee.amount()==null||fee.amount().abs().compareTo(BigDecimal.valueOf(1000000))>0)throw new IllegalStateException("Commission currency/value unsupported");
        if(s.executions.stream().noneMatch(e->e.executionId().equals(fee.executionId()))){
            var pending=s.pendingCommissions.stream().filter(c->c.executionId().equals(fee.executionId())).findFirst();
            if(pending.isPresent()&&pending.get().amount().compareTo(fee.amount())==0&&pending.get().currency().equals(fee.currency()))return false;
            s.pendingCommissions.removeIf(c->c.executionId().equals(fee.executionId()));s.pendingCommissions.add(fee);return true;
        }
        var existing=s.commissions.stream().filter(c->c.executionId().equals(fee.executionId())).findFirst();
        if(existing.isPresent()&&existing.get().amount().compareTo(fee.amount())==0&&existing.get().currency().equals(fee.currency()))return false;
        s.commissions.removeIf(c->c.executionId().equals(fee.executionId()));s.commissions.add(fee);
        s.executions.stream().filter(e->e.executionId().equals(fee.executionId())).findFirst().ifPresent(e->rebuild(s,e.signalId()));return true;
    }
    private static void rebuild(Session s,String signalId){
        var signal=s.signals.stream().filter(r->r.id.equals(signalId)).findFirst().orElseThrow();
        var trade=s.trades.stream().filter(t->t.signalId.equals(signalId)).findFirst().orElseGet(()->{
            var t=new Trade();t.signalId=signalId;t.symbol=signal.symbol;t.direction=signal.signal.detection().direction().name();
            t.pattern=signal.signal.detection().patternType().name();t.signalDate=signal.signal.detection().signalDate();t.mode="PAPER";
            t.stop=signal.expectedStop;t.target=signal.expectedTarget;t.expectedEntry=signal.expectedEntry;t.expectedQuantity=signal.expectedQuantity;
            t.expectedSlippage=BigDecimal.valueOf(s.frozen.configuration().global().slippagePercentPerSide());
            t.expectedRisk=signal.expectedEntry.subtract(signal.expectedStop).abs();s.trades.add(t);return t;});
        trade.entered=BigDecimal.ZERO;trade.exited=BigDecimal.ZERO;trade.entryValue=BigDecimal.ZERO;trade.exitValue=BigDecimal.ZERO;trade.commission=BigDecimal.ZERO;
        for(var e:s.executions.stream().filter(e->signalId.equals(e.signalId())).sorted(Comparator.comparing(ExecutionRecord::time)).toList()){
            if(e.role()==Role.ENTRY){
                trade.entered=trade.entered.add(e.quantity());trade.entryValue=trade.entryValue.add(e.quantity().multiply(e.price()));
                if(trade.entryAt==null||e.time().isBefore(trade.entryAt)){trade.entryAt=e.time();trade.entryDate=e.time().atZone(ZoneId.of("America/New_York")).toLocalDate();}
            }else{
                trade.exited=trade.exited.add(e.quantity());trade.exitValue=trade.exitValue.add(e.quantity().multiply(e.price()));
                trade.exitAt=e.time();trade.exitReason=e.role()==Role.TAKE_PROFIT?"TARGET":e.role()==Role.STOP_LOSS?"STOP":"TIMEOUT";
                if(e.role()==Role.TAKE_PROFIT)trade.expectedExit=trade.target;else if(e.role()==Role.STOP_LOSS)trade.expectedExit=trade.stop;
            }
            var fee=s.commissions.stream().filter(c->c.executionId().equals(e.executionId())).findFirst();if(fee.isPresent())trade.commission=trade.commission.add(fee.get().amount());
        }
        if(trade.exited.compareTo(trade.entered)>0)throw new IllegalStateException("Exit exceeds actual entry fills");
        trade.realized=trade.exitValue.subtract(trade.exited.multiply(trade.entryPrice())).multiply(trade.direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate());
    }
    public static EquityPoint capital(Session s,Instant now){
        BigDecimal realized=BigDecimal.ZERO,unrealized=BigDecimal.ZERO,fees=BigDecimal.ZERO,committed=BigDecimal.ZERO,longs=BigDecimal.ZERO,shorts=BigDecimal.ZERO;
        for(var t:s.trades){
            realized=realized.add(t.realized);unrealized=unrealized.add(t.unrealized());fees=fees.add(t.commission).add(t.shadowSlippage);
            var remaining=t.remaining();var mark=t.mark==null?t.entryPrice():t.mark;
            var value=mark.multiply(remaining);
            if(t.direction.equals("LONG"))longs=longs.add(value);else shorts=shorts.add(value);
            // 100% SHORT collateral is reserved separately from short proceeds: no leverage.
            committed=committed.add(t.entryPrice().multiply(remaining));
        }
        for(var o:s.orders)if(o.role==Role.ENTRY&&!o.terminal()){
            var intent=s.signals.stream().filter(r->r.id.equals(o.signalId)).findFirst().orElseThrow();
            var filled=s.executions.stream().filter(e->e.orderId()==o.orderId).map(ExecutionRecord::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            var pending=BigDecimal.valueOf(o.quantity).subtract(filled).max(BigDecimal.ZERO).multiply(intent.expectedEntry);
            var pendingQuantity=BigDecimal.valueOf(o.quantity).subtract(filled).max(BigDecimal.ZERO);
            var anticipatedCosts=s.frozen.configuration().global().commission().charge(pendingQuantity.longValueExact(),intent.expectedEntry)
                    .add(pending.multiply(BigDecimal.valueOf(s.frozen.configuration().global().slippagePercentPerSide()/100)));
            committed=committed.add(pending).add(anticipatedCosts);
            if(o.action.equals("BUY"))longs=longs.add(pending);else shorts=shorts.add(pending);
        }
        var equity=s.initialCapital.add(realized).add(unrealized).subtract(fees);
        var available=s.initialCapital.add(realized).subtract(fees).subtract(committed).max(BigDecimal.ZERO);
        var peak=s.peakEquity.max(equity);var drawdown=peak.signum()==0?BigDecimal.ZERO:peak.subtract(equity).multiply(BigDecimal.valueOf(100)).divide(peak,MathContext.DECIMAL64);
        return new EquityPoint(now,"PAPER",equity,realized.subtract(fees),unrealized,fees,available,committed,longs,shorts,drawdown);
    }
    public static Map<String,Object> performance(Session s,EquityPoint point){
        var closed=s.trades.stream().filter(Trade::closed).toList();var wins=closed.stream().filter(t->t.netPnl().signum()>0).count();
        var positive=closed.stream().map(Trade::netPnl).filter(p->p.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);
        var negative=closed.stream().map(Trade::netPnl).filter(p->p.signum()<0).reduce(BigDecimal.ZERO,BigDecimal::add).abs();
        var r=closed.stream().filter(t->t.expectedRisk!=null&&t.expectedRisk.signum()>0).map(t->t.netPnl().divide(t.expectedRisk.multiply(t.entered),MathContext.DECIMAL64)).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("closedTrades",closed.size());metrics.put("openPositions",s.trades.stream().filter(t->t.remaining().signum()>0).count());
        metrics.put("winRate",closed.isEmpty()?null:BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed.size()),MathContext.DECIMAL64));
        metrics.put("profitFactorNet",negative.signum()==0?null:positive.divide(negative,MathContext.DECIMAL64));
        metrics.put("expectancyR",closed.isEmpty()?null:r.divide(BigDecimal.valueOf(closed.size()),MathContext.DECIMAL64));
        metrics.put("maximumDrawdownPercent",s.maximumDrawdown);metrics.put("cumulativeReturnPercent",point.equity().subtract(s.initialCapital).multiply(BigDecimal.valueOf(100)).divide(s.initialCapital,MathContext.DECIMAL64));
        metrics.put("commissionsPending",s.executions.stream().filter(e->s.commissions.stream().noneMatch(c->c.executionId().equals(e.executionId()))).count());
        return metrics;
    }
}
