package it.andreademartini.trading.web;

import it.andreademartini.trading.backtest.rebound.TrendReboundConfiguration;
import it.andreademartini.trading.backtest.weeklytte.WeeklyResearchStrategy;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Research catalog is deliberately separate from the operational StrategyType and broker factories. */
@RestController
public class ResearchStrategyCatalog {
    public record Descriptor(WeeklyResearchStrategy id,String name,String description,String page,String api,boolean experimental){}
    @GetMapping("/api/backtest/research-strategies")public List<Descriptor> strategies(){return List.of(new Descriptor(WeeklyResearchStrategy.TREND_PULLBACK_REBOUND_V3_RESEARCH,"TREND PULLBACK REBOUND V3 RESEARCH","BUY STOP quarta barra e analisi spazio ATR","/trend-pullback-rebound-v3.html","/api/backtest/trend-pullback-rebound-v3",true),
            new Descriptor(WeeklyResearchStrategy.WEEKLY_HIGH_TTE_RESEARCH,"Weekly High TTE","Ricerca su livelli settimanali","/weekly-high-tte.html","/api/backtest/weekly-high-tte",true),
            new Descriptor(WeeklyResearchStrategy.TREND_PULLBACK_REBOUND_V1,"Trend Pullback Rebound","Acquisto dopo tre chiusure consecutive in discesa, dentro un trend rialzista, per cercare un recupero entro cinque sedute","/trend-pullback-rebound.html","/api/backtest/trend-pullback-rebound",true),
            new Descriptor(WeeklyResearchStrategy.TREND_PULLBACK_REBOUND_V2_RESEARCH,"TREND PULLBACK REBOUND V2 — RESEARCH","Variante sperimentale con target ATR fisso e confronto RECOVERY_ONLY / ATR_TARGET_ONLY / FIRST_OF_BOTH","/trend-pullback-rebound-v2.html","/api/backtest/trend-pullback-rebound-v2",true));}
}
