package it.andreademartini.trading.backtest.reporting;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.*;
import org.springframework.mock.web.*;
import org.springframework.http.*;
import org.springframework.http.server.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PortfolioSummaryAdviceTest {
 @Test void apiEnrichmentIsAdditiveAndLeavesOriginalRunUnchanged(){
  var body=new LinkedHashMap<String,Object>();body.put("runId","test");body.put("strategy","TREND_PULLBACK_REBOUND_V1");body.put("configuration",Map.of());body.put("equity",List.of());body.put("analysis",Map.of());
  var request=new ServletServerHttpRequest(new MockHttpServletRequest("GET","/api/backtest/trend-pullback-rebound/runs/test"));
  var result=(Map<?,?>)new PortfolioSummaryAdvice().beforeBodyWrite(body,null,MediaType.APPLICATION_JSON,null,request,new ServletServerHttpResponse(new MockHttpServletResponse()));
  assertFalse(body.containsKey("portfolioSummary"));assertTrue(result.get("portfolioSummary") instanceof PortfolioSummaryDto);assertEquals("test",result.get("runId"));assertEquals(40,((PortfolioSummaryDto)result.get("portfolioSummary")).metrics().size());
 }
 @Test void paperAndBinaryExportsAreNotIntercepted(){
  var body=new LinkedHashMap<String,Object>();body.put("portfolio",Map.of("summary",Map.of()));
  var request=new ServletServerHttpRequest(new MockHttpServletRequest("GET","/api/paper/status"));var response=new ServletServerHttpResponse(new MockHttpServletResponse());var advice=new PortfolioSummaryAdvice();
  assertSame(body,advice.beforeBodyWrite(body,null,MediaType.APPLICATION_JSON,null,request,response));var bytes=new byte[]{1,2};assertSame(bytes,advice.beforeBodyWrite(bytes,null,MediaType.APPLICATION_OCTET_STREAM,null,request,response));
 }
}
