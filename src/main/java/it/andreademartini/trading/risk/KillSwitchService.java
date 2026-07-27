package it.andreademartini.trading.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchService.class);

    private final AtomicBoolean active = new AtomicBoolean(false);

    public boolean activate(String reason) {
        boolean changed = active.compareAndSet(false, true);
        if (changed) {
            log.error("KILL SWITCH ATTIVATO: {}", reason);
        }
        return changed;
    }

    public boolean deactivate() {
        boolean changed = active.compareAndSet(true, false);
        if (changed) {
            log.warn("Kill switch disattivato manualmente");
        }
        return changed;
    }

    public boolean isActive() {
        return active.get();
    }

    public void assertNotActive() {
        if (active.get()) {
            throw new RiskRejectedException("Kill switch attivo: nessun ordine consentito");
        }
    }
}
