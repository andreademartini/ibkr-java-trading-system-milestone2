package it.andreademartini.trading.paper.multi;

import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.paper.patternv2.PatternV2PaperStore;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import org.springframework.context.annotation.*;

@Configuration
public class PaperBrokerRouting {
    @Bean public MultiStrategyPaperRouter multiStrategyPaperRouter(PatternPaperIbkrBroker broker,PatternPaperStore v1,PatternV2PaperStore v2,HistoricalDataService history,PaperDeskStore state,it.andreademartini.trading.risk.KillSwitchService kill){
        var router=new MultiStrategyPaperRouter(broker,v1::session,v2::session,history,false);
        router.onAccountFault(reason->{boolean changed=state.update(s->{boolean fresh=!s.globalKill;s.globalKill=true;s.reason="Account reconciliation: "+reason;return fresh;});kill.activate("PAPER account reconciliation failure");if(changed)state.event("ACCOUNT_RECONCILIATION_ERROR",reason,true);});return router;
    }
    @Bean @Primary public PatternPaperBroker patternV1PaperBroker(MultiStrategyPaperRouter router){return router.view(MultiStrategyPaperRouter.V1);}
    @Bean public PatternPaperBroker patternV2PaperBroker(MultiStrategyPaperRouter router){return router.view(MultiStrategyPaperRouter.V2);}
}
