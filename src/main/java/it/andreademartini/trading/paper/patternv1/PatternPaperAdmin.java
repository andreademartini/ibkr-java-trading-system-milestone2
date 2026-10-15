package it.andreademartini.trading.paper.patternv1;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
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
        attempts.remove(address);return session(request,response,false);
    }
    /** No DNS resolution or forwarded headers may turn a remote client into a local one. */
    public boolean localAccess(HttpServletRequest request){
        var addresses=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1");
        var hosts=Set.of("localhost","127.0.0.1","::1","[::1]","0:0:0:0:0:0:0:1","[0:0:0:0:0:0:0:1]");
        if(!addresses.contains(request.getRemoteAddr())||request.getServerName()==null||!hosts.contains(request.getServerName().toLowerCase(Locale.ROOT)))return false;
        for(var header:List.of("Forwarded","X-Forwarded-For","X-Forwarded-Host","X-Forwarded-Proto"))if(request.getHeader(header)!=null)return false;
        return Set.of("http","https").contains(request.getScheme());
    }
    public String localSession(HttpServletRequest request,HttpServletResponse response){
        if(!localAccess(request)||!"1".equals(request.getHeader("X-Paper-Local-Session")))throw new SecurityException("Accesso senza chiave disponibile solo da localhost");
        if(!sameOrigin(request))throw new SecurityException("Origin non autorizzata");
        // Keep other open tabs connected instead of invalidating their session on every page load.
        var existing=request.getSession(false);
        if(authorized(request)&&existing.getAttribute("PATTERN_PAPER_CSRF") instanceof String csrf)return csrf;
        return session(request,response,true);
    }
    public boolean sameOrigin(HttpServletRequest request){
        String origin=request.getHeader("Origin");if(origin==null)return true;
        try{var uri=URI.create(origin);int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
            return uri.getHost()!=null&&request.getServerName()!=null&&host(uri.getHost()).equalsIgnoreCase(host(request.getServerName()))
                &&port==request.getServerPort()&&Objects.equals(uri.getScheme(),request.getScheme());
        }catch(IllegalArgumentException e){return false;}
    }
    private static String host(String value){return value.replace("[","").replace("]","");}
    private String session(HttpServletRequest request,HttpServletResponse response,boolean local){
        var prior=request.getSession(false);if(prior!=null)prior.invalidate();
        var session=request.getSession(true);session.setMaxInactiveInterval(1800);String csrf=UUID.randomUUID().toString();session.setAttribute("PATTERN_PAPER_ADMIN",Boolean.TRUE);session.setAttribute("PATTERN_PAPER_CSRF",csrf);
        session.setAttribute("PATTERN_PAPER_LOCAL",local);
        response.addHeader("Set-Cookie","JSESSIONID="+session.getId()+"; Path=/; HttpOnly; SameSite=Strict"+(request.isSecure()?"; Secure":""));return csrf;
    }
    public boolean authorized(HttpServletRequest request){var s=request.getSession(false);return s!=null&&Boolean.TRUE.equals(s.getAttribute("PATTERN_PAPER_ADMIN"))&&(!Boolean.TRUE.equals(s.getAttribute("PATTERN_PAPER_LOCAL"))||localAccess(request));}
    public boolean validCsrf(HttpServletRequest request){var s=request.getSession(false);return s!=null&&s.getAttribute("PATTERN_PAPER_CSRF") instanceof String csrf&&!csrf.isEmpty()&&Objects.equals(csrf,request.getHeader("X-Paper-CSRF"));}
    private record Attempt(long since,int failures) {}
}
