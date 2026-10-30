package it.andreademartini.trading.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Revalidate the application UI after updates; API and asset caching are unchanged. */
@Component
public class HtmlPageCacheFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        var path=request.getRequestURI().substring(request.getContextPath().length());
        return !(request.getMethod().equals("GET")||request.getMethod().equals("HEAD"))
                ||path.startsWith("/api/")||!(path.equals("/")||path.endsWith(".html"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store, max-age=0");
        response.setHeader("Pragma","no-cache");response.setDateHeader("Expires",0);
        chain.doFilter(request,response);
    }
}
