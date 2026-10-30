package it.andreademartini.trading.backtest.reporting;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import java.util.*;

/** Enrich API views only; persisted run payloads and engine return values remain unchanged. */
@ControllerAdvice(basePackages="it.andreademartini.trading")
public class PortfolioSummaryAdvice implements ResponseBodyAdvice<Object> {
 @org.springframework.beans.factory.annotation.Autowired(required=false)
 private it.andreademartini.trading.backtest.patternstable.PatternStableService stableV1;
 private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> c){return true;}
 public Object beforeBodyWrite(Object body,MethodParameter p,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
  if(body==null||body instanceof byte[]||body instanceof String||!request.getURI().getPath().startsWith("/api/backtest/"))return body;
  var node=JSON.valueToTree(body);
  if(!PortfolioSummaryMapper.isRun(node))return body;
  Map<String,Object> view=new LinkedHashMap<>();
  if(body instanceof Map<?,?> map)map.forEach((k,v)->view.put(k.toString(),v));
  else if(body.getClass().isRecord()){
   view.putAll(JSON.convertValue(node,LinkedHashMap.class));
   try{for(var component:body.getClass().getRecordComponents())if(view.containsKey(component.getName()))view.put(component.getName(),component.getAccessor().invoke(body));}
   catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot build additive reporting view",e);}
  }else view.putAll(JSON.convertValue(body,LinkedHashMap.class));
  Object source=body;
  if(stableV1!=null&&node.has("backtestId")&&"PATTERN_V1".equals(node.path("strategy").asText())){
   try{source=stableV1.result(node.path("backtestId").asText());}catch(NoSuchElementException legacy){/* legacy settings remain the authoritative available snapshot */}
  }
  view.put("portfolioSummary",PortfolioSummaryMapper.from(source));return view;
 }
}
