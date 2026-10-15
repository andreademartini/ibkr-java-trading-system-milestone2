package it.andreademartini.trading.paper.patternv2;

import it.andreademartini.trading.paper.patternv1.*;

import jakarta.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.time.Instant;
import java.util.concurrent.*;

/** Polling only observes exchange sessions. DISABLED performs no broker requests. */
@Component
public class PatternV2PaperScheduler {
    private static final Logger log=LoggerFactory.getLogger(PatternV2PaperScheduler.class);
    private final PatternV2PaperService service;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"pattern-v2-paper-scheduler");t.setDaemon(true);return t;});
    public PatternV2PaperScheduler(PatternV2PaperService service){this.service=service;}
    @PostConstruct void start(){executor.scheduleWithFixedDelay(()->{try{service.tick(Instant.now());}catch(RuntimeException e){log.error("PATTERN_V2 PAPER scheduler failed; no automatic order retry",e);}},30,1,TimeUnit.SECONDS);}
    @PreDestroy void stop(){executor.shutdownNow();}
}
