package it.andreademartini.trading.backtest.patternv2stable;

import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.ExitFillPolicy;
import org.junit.jupiter.api.Test;

class StableRiskRevisionTest {
    @Test void onlyRiskChangesAndHistoricalVersionsRemainAvailable(){
        var v1=new PatternStableService(null,null,null,null);
        var v2=new FrozenPatternV2Service(null,null,java.nio.file.Path.of("target/unused-risk-test"));
        for(var pair:java.util.List.of(java.util.List.of(v1.version("2.2"),v1.version()),java.util.List.of(v2.version("1.1"),v2.version()))){
            var old=pair.getFirst();var fresh=pair.getLast();assertEquals(2,old.configuration().global().riskPercent());assertEquals(4,fresh.configuration().global().riskPercent());
            var expected=PatternStableStore.JSON.valueToTree(old.configuration());((com.fasterxml.jackson.databind.node.ObjectNode)expected.path("global")).put("riskPercent",4);
            assertEquals(PatternStableStore.hash(expected),fresh.configurationHash());assertNotEquals(old.configurationHash(),fresh.configurationHash());
            assertEquals(ExitFillPolicy.OPEN_FIRST,fresh.configuration().exitFillPolicy());assertFalse(fresh.safety().orderSubmissionEnabled());
            assertEquals(4,fresh.configuration().global().riskPercent());
        }
        assertEquals(2,v1.version("2.1").configuration().global().riskPercent());assertEquals(2,v2.version("1.0").configuration().global().riskPercent());
    }
}
