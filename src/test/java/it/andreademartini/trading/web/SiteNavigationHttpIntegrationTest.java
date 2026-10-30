package it.andreademartini.trading.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:site-navigation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop","trading.mode=BACKTEST","trading.ibkr.auto-connect=false",
        "trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
class SiteNavigationHttpIntegrationTest {
    @Autowired Environment environment;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> get(String path)throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getProperty("local.server.port")+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void everyDestinationServesTheSameDropdownsAndDisablesHtmlCaching()throws Exception {
        for(var path:List.of("/","/index.html","/pattern-v1.html","/pattern-v2.html","/pattern-v3.html","/pattern-v1-legacy.html","/paper-trading.html","/research.html","/weekly-high-tte.html","/config.html","/provider.html","/trend-pullback-rebound.html")){
            var response=get(path+"?nav=20261030-v3");assertEquals(200,response.statusCode(),path);
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"),path);
            var html=response.body();assertTrue(html.contains("top-actions site-nav"),path);
            for(var label:List.of("Strategie","Pattern Research","Configurazione"))assertTrue(html.contains("<summary>"+label+"</summary>"),path);
            assertTrue(html.contains("/site-navigation.js?v=20261030-v3"),path);
            assertFalse(html.contains("weekly-level-momentum"),path);
        }
    }
    @Test void navigationAssetRemainsAvailableAndApiIsOutsideTheHtmlCachePolicy()throws Exception {
        assertEquals(200,get("/site-navigation.js?v=20261030-v3").statusCode());
        var response=get("/api/backtest/weekly-high-tte/configuration");assertEquals(200,response.statusCode());
        assertFalse(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
}
