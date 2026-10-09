package it.andreademartini.trading.paper.patternv1;

import jakarta.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.time.Instant;
import java.util.concurrent.*;

/** Polling only observes exchange sessions. DISABLED performs no broker requests. */
@Component
public class PatternPaperScheduler {
    private static final Logger log=LoggerFactory.getLogger(PatternPaperScheduler.class);
    private final PatternPaperService service;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"pattern-v1-paper-scheduler");t.setDaemon(true);return t;});
    public PatternPaperScheduler(PatternPaperService service){this.service=service;}
    @PostConstruct void start(){executor.scheduleWithFixedDelay(()->{try{service.tick(Instant.now());}catch(RuntimeException e){log.error("PATTERN_V1 PAPER scheduler failed; no automatic order retry",e);}},30,1,TimeUnit.SECONDS);}
    @PreDestroy void stop(){executor.shutdownNow();}
}
