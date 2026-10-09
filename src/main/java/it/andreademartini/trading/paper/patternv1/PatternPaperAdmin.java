package it.andreademartini.trading.paper.patternv1;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Dedicated admin session, never persisted in browser storage or audit logs. */
@Component
public class PatternPaperAdmin {
    private final String token;
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>();
    public PatternPaperAdmin(@Value("${pattern-paper.admin-token:${PATTERN_PAPER_ADMIN_TOKEN:}}") String token){this.token=token;}
    public boolean configured(){return token!=null&&token.length()>=32;}
    public String login(String supplied,HttpServletRequest request,HttpServletResponse response){
        if(!configured())throw new IllegalStateException("Configurare PATTERN_PAPER_ADMIN_TOKEN (almeno 32 caratteri) prima dei comandi amministrativi");
        var now=System.currentTimeMillis();String address=request.getRemoteAddr();var old=attempts.get(address);
        if(old!=null&&now-old.since<300_000&&old.failures>=5)throw new SecurityException("Troppi tentativi: riprovare tra 5 minuti");
        if(supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
            attempts.compute(address,(k,a)->a==null||now-a.since>=300_000?new Attempt(now,1):new Attempt(a.since,a.failures+1));throw new SecurityException("Accesso amministrativo negato");}
        attempts.remove(address);var prior=request.getSession(false);if(prior!=null)prior.invalidate();
        var session=request.getSession(true);session.setMaxInactiveInterval(1800);String csrf=UUID.randomUUID().toString();session.setAttribute("PATTERN_PAPER_ADMIN",Boolean.TRUE);session.setAttribute("PATTERN_PAPER_CSRF",csrf);
        response.addHeader("Set-Cookie","JSESSIONID="+session.getId()+"; Path=/; HttpOnly; SameSite=Strict"+(request.isSecure()?"; Secure":""));return csrf;
    }
    public boolean authorized(HttpServletRequest request){var s=request.getSession(false);return s!=null&&Boolean.TRUE.equals(s.getAttribute("PATTERN_PAPER_ADMIN"));}
    public boolean validCsrf(HttpServletRequest request){var s=request.getSession(false);return s!=null&&Objects.equals(s.getAttribute("PATTERN_PAPER_CSRF"),request.getHeader("X-Paper-CSRF"));}
    private record Attempt(long since,int failures) {}
}
