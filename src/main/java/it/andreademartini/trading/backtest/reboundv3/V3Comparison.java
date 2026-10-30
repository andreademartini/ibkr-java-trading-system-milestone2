package it.andreademartini.trading.backtest.reboundv3;
import java.util.*;
import it.andreademartini.trading.backtest.rebound.*;
final class V3Comparison {
 static Map<String,Object> row(String mode,List<TrendReboundRun.Trade> trades,int signals,Map<String,Object> analysis){
 var m=new LinkedHashMap<String,Object>((Map<String,Object>)analysis.get("performance"));m.put("EntryMode",mode);m.put("SignalsDetected",signals);m.put("TradesExecuted",trades.size());m.put("TradesRejected",signals-trades.size());m.put("AverageHoldingBars",trades.stream().mapToInt(TrendReboundRun.Trade::holdingBars).average().orElse(Double.NaN));m.put("TotalCosts",trades.stream().mapToDouble(t->t.entryCommission()+t.exitCommission()+t.slippageCosts()).sum());return m;
 }
}
