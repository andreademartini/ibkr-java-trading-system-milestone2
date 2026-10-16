package it.andreademartini.trading.backtest.patternv3;
import it.andreademartini.trading.backtest.execution.BarExecution;
import it.andreademartini.trading.backtest.patternv2.ExitFillPolicy;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.SameBarResolution;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan;
import java.math.BigDecimal;

/** Compatibility facade. Unqualified calls retain LEVEL_ONLY, including PAPER shadow callers. */
public final class PatternV3BarRules {
    private PatternV3BarRules(){}
    public record Exit(boolean stop,boolean target,boolean exitOnStop,boolean completed,String reason,BigDecimal price,boolean atOpen){
        public Exit(boolean stop,boolean target,boolean exitOnStop,boolean completed,String reason,BigDecimal price){this(stop,target,exitOnStop,completed,reason,price,false);}
        BarExecution.Exit shared(){return new BarExecution.Exit(stop,target,exitOnStop,completed,reason,price,atOpen);}
    }
    public static Exit exit(EntryPlan plan,HistoricalBar bar,int holding,int maximum,SameBarResolution policy){return exit(plan,bar,holding,maximum,policy,null);}
    public static Exit exit(EntryPlan plan,HistoricalBar bar,int holding,int maximum,SameBarResolution policy,ExitFillPolicy fillPolicy){
        var e=BarExecution.exit(plan,bar,holding,maximum,policy,fillPolicy);
        return new Exit(e.stop(),e.target(),e.exitOnStop(),e.completed(),e.reason(),e.price(),e.atOpen());
    }
    public static final class Excursions extends BarExecution.Excursions {
        public void measure(EntryPlan plan,HistoricalBar bar,Exit exit){super.measure(plan,bar,exit.shared());}
    }
}
