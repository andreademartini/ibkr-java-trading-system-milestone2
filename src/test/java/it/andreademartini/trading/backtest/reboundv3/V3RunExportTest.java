package it.andreademartini.trading.backtest.reboundv3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class V3RunExportTest {
 @TempDir Path temp;
 static TrendReboundV3Run fixture(){
  var bars=TrendReboundV3EngineTest.series("TEST");bars.set(10,TrendReboundV3EngineTest.bar("TEST",10,106.5,112,106,111));
  return TrendReboundV3EngineTest.simulate(TrendReboundV3EngineTest.config(),Map.of("TEST",bars));
 }
 @Test void selectedPersistedRunDownloadsValidExcelThroughHttp()throws Exception{
  var run=fixture();var store=new TrendReboundV3Store(temp);store.save(run);
  var controller=new TrendReboundV3Controller(store,mock(TrendReboundV3Service.class),new TrendReboundV3ExcelExporter());
  var mvc=MockMvcBuilders.standaloneSetup(controller).build();
  var response=mvc.perform(get("/api/backtest/trend-pullback-rebound-v3/runs/"+run.runId()+"/export/excel"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
  assertTrue(response.getContentAsByteArray().length>1000);
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){assertNotNull(book.getSheet("TRADES"));assertNotNull(book.getSheet("PORTFOLIO_SUMMARY"));assertNotNull(book.getSheet("MINIMUM_TICK_AUDIT"));}
 }
 @Test void blockedPrevalidationStillHasDownloadableDiagnosticWorkbook()throws Exception{
  var c=TrendReboundV3EngineTest.config();var service=mock(TrendReboundV3Service.class);
  var report=V3TickSupport.prevalidate(c,Map.of("TEST",TrendReboundV3EngineTest.series("TEST")),"fixture");
  assertFalse(report.passed());when(service.withLocalMetadata(any())).thenAnswer(i->i.getArgument(0));when(service.prevalidate(any())).thenReturn(report);
  var mvc=MockMvcBuilders.standaloneSetup(new TrendReboundV3Controller(new TrendReboundV3Store(temp),service,new TrendReboundV3ExcelExporter())).build();
  var response=mvc.perform(post("/api/backtest/trend-pullback-rebound-v3/minimum-ticks/export/excel").contentType("application/json").content(TrendReboundV3Store.JSON.writeValueAsBytes(c)))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){assertNotNull(book.getSheet("MINIMUM_TICK_AUDIT"));assertEquals(1,book.getSheet("TRADES").getPhysicalNumberOfRows());}
  verify(service,never()).run(any());
 }
 @Test void largeMetadataCatalogIsSplitIntoExcelSafeConfigurationCells()throws Exception{
  var bars=TrendReboundV3EngineTest.series("TEST");bars.set(10,TrendReboundV3EngineTest.bar("TEST",10,106.5,112,106,111));var input=Map.of("TEST",bars);
  var c=TrendReboundV3EngineTest.configured(TrendReboundV3EngineTest.config(),input);var m=c.minimumTickMetadata();
  var rules=new ArrayList<it.andreademartini.trading.marketdata.ticks.MinimumTickResolver.Rule>();
  for(int i=0;i<100;i++)rules.add(new it.andreademartini.trading.marketdata.ticks.MinimumTickResolver.Rule("UNUSED_FIXTURE_"+i,"TEST","TEST_EXCHANGE","USD","STK",TrendReboundV3EngineTest.day(0),TrendReboundV3EngineTest.day(100),"SAVED_MARKET_RULE","TEST ONLY, NOT VALIDATED: "+"x".repeat(600),false,"RAW",null,List.of(new it.andreademartini.trading.marketdata.ticks.MinimumTickResolver.Band(java.math.BigDecimal.ZERO,new java.math.BigDecimal("0.01")))));
  var node=(com.fasterxml.jackson.databind.node.ObjectNode)TrendReboundV3Store.JSON.valueToTree(c);
  node.set("minimumTickMetadata",TrendReboundV3Store.JSON.valueToTree(new it.andreademartini.trading.marketdata.ticks.MinimumTickResolver.Catalog("LARGE_FIXTURE/1",m.instruments(),rules,m.priceBases())));
  c=TrendReboundV3Store.JSON.treeToValue(node,TrendReboundV3Configuration.class);var run=TrendReboundV3EngineTest.simulate(c,input);
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(new TrendReboundV3ExcelExporter().export(run)))){
   boolean chunked=false;for(var row:book.getSheet("CONFIGURATION")){if(row.getCell(0).toString().equals("minimumTickMetadata[0]"))chunked=true;for(var cell:row)if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.STRING)assertTrue(cell.getStringCellValue().length()<=32767);}
   assertTrue(chunked);assertNotNull(book.getSheet("TRADES"));
  }
 }
}
