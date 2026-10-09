package it.andreademartini.trading.paper.patternv1;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Component
public class PatternPaperSecurityFilter extends OncePerRequestFilter {
    private final PatternPaperAdmin admin;
    public PatternPaperSecurityFilter(PatternPaperAdmin admin){this.admin=admin;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/api/pattern-v1/paper/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        String path=request.getRequestURI();
        if(path.endsWith("/access")&&request.getMethod().equals("GET")){chain.doFilter(request,response);return;}
        if(!request.isSecure()&&!Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(request.getRemoteAddr())){deny(response,403,"Usare HTTPS per accesso amministrativo remoto");return;}
        String origin=request.getHeader("Origin");
        if(origin!=null)try{var parsed=URI.create(origin);int port=parsed.getPort()<0?(parsed.getScheme().equals("https")?443:80):parsed.getPort();if(!parsed.getHost().equalsIgnoreCase(request.getServerName())||port!=request.getServerPort()||!parsed.getScheme().equals(request.getScheme())){deny(response,403,"Origin non autorizzata");return;}}catch(RuntimeException e){deny(response,403,"Origin non valida");return;}
        if(path.endsWith("/login")&&request.getMethod().equals("POST")){chain.doFilter(request,response);return;}
        if(!admin.authorized(request)){deny(response,401,"Accesso amministrativo PAPER richiesto");return;}
        if(!Set.of("GET","HEAD").contains(request.getMethod())&&!admin.validCsrf(request)){deny(response,403,"Token CSRF PAPER non valido");return;}
        chain.doFilter(request,response);
    }
    private void deny(HttpServletResponse response,int status,String message)throws IOException{response.setStatus(status);response.setContentType("application/json");response.getWriter().write(PatternPaperStore.json(java.util.Map.of("message",message)));}
}
