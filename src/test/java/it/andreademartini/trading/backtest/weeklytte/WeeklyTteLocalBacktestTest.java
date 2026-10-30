package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in offline artifact generation. Does not start Spring or connect to IBKR. */
@EnabledIfSystemProperty(named="weeklytte.local",matches="true")
class WeeklyTteLocalBacktestTest {
    @Test void firstLocalBacktestAndComparison() throws Exception {
        var store=new WeeklyTteStore();var service=new WeeklyTteService(store,new PositionSizingService(new TradePricingService()));
        var c=WeeklyTteConfiguration.defaults();
        var baseline=service.run(c);assertFalse(baseline.weeklyLevels().isEmpty());
        Path output=Path.of("outputs","weekly-high-tte-research");Files.createDirectories(output);
        Files.write(output.resolve("first-backtest.xlsx"),new WeeklyTteExcelExporter().export(baseline));
        Files.write(output.resolve("first-backtest.json"),WeeklyTteStore.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(baseline));
        var modes=service.modes(baseline.runId());
        for(var r:modes)Files.write(output.resolve(r.configuration().mode().name()+".xlsx"),new WeeklyTteExcelExporter().export(r));
        var comparison=service.compare(modes.stream().map(WeeklyTteRun::runId).toList());
        Files.write(output.resolve("mode-comparison.xlsx"),new WeeklyTteExcelExporter().comparison(comparison));
        Files.write(output.resolve("mode-comparison.json"),WeeklyTteStore.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(comparison));
        var sensitivity=service.sensitivity(baseline.runId());
        Files.write(output.resolve("intrabar-sensitivity.xlsx"),new WeeklyTteExcelExporter().export(sensitivity));
        Files.write(output.resolve("intrabar-sensitivity.json"),WeeklyTteStore.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("conservativeRunId",baseline.runId(),"conservative",baseline.analysis().get("performance"),"favorableRunId",sensitivity.runId(),"favorable",sensitivity.analysis().get("performance"))));
        System.out.println("WEEKLY_TTE_FIRST_BACKTEST "+baseline.runId()+" setups="+baseline.setups().size()+" trades="+baseline.trades().size()+" "+WeeklyTteStore.JSON.writeValueAsString(baseline.analysis().get("performance")));
        assertEquals(baseline.configurationHash(),store.run(baseline.runId()).configurationHash());
    }
}
