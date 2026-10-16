package it.andreademartini.trading.backtest.execution;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv3.PatternV3Run;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Read-only projection for shared diagnostics. Never copies or changes strategy configurations. */
public record ExecutionRunView(PatternV1Result portfolio,List<Execution> executions,List<String> enabledSymbols,
        LocalDate requestedEnd,ExitFillPolicy fillPolicy,BigDecimal slippagePercent,boolean splitAware,
        Set<String> splitRuleSymbols,GapExecutionAudit executionAudit){
    public record Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,
            BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,
            BigDecimal commission,BigDecimal slippageCost,BigDecimal transactionCosts){}
    public static ExecutionRunView of(PatternV2Run run){
        var c=run.configuration();
        return new ExecutionRunView(run.portfolio(),run.executions().stream().map(e->new Execution(e.trade(),e.entryTimestamp(),e.exitTimestamp(),e.entryPriceAfterSlippage(),e.exitPriceAfterSlippage(),e.commission(),e.slippageCost(),e.transactionCosts())).toList(),
            c.symbols().stream().filter(s->s.enabled()).map(s->s.symbol()).toList(),c.global().endDate(),c.exitFillPolicy(),
            BigDecimal.valueOf(c.global().slippageEnabled()?c.global().slippagePercentPerSide():0),c.splitCosts()!=null,
            c.splitCosts()==null?Set.of():new HashSet<>(c.splitCosts().rules().stream().map(r->r.symbol()).toList()),run.executionAudit());
    }
    public static ExecutionRunView of(PatternV3Run run){
        var c=run.configuration();
        return new ExecutionRunView(run.portfolio(),run.executions().stream().map(e->new Execution(e.trade(),e.entryTimestamp(),e.exitTimestamp(),e.entryPriceAfterSlippage(),e.exitPriceAfterSlippage(),e.commission(),e.slippageCost(),e.transactionCosts())).toList(),
            c.symbols().stream().filter(s->s.enabled()).map(s->s.symbol()).toList(),c.global().endDate(),run.executionPolicy(),
            BigDecimal.valueOf(c.global().slippageEnabled()?c.global().slippagePercentPerSide():0),c.splitCosts()!=null,
            c.splitCosts()==null?Set.of():new HashSet<>(c.splitCosts().rules().stream().map(r->r.symbol()).toList()),run.executionAudit());
    }
}
