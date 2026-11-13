package it.andreademartini.trading.monitor;

import it.andreademartini.trading.ibkr.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionMonitorTest {
    ExecutionMonitorStore store;ExecutionMonitor monitor;
    @BeforeEach void setup(){store=mock(ExecutionMonitorStore.class);monitor=new ExecutionMonitor(store,new IbkrEWrapper());}
    @ParameterizedTest @CsvSource({"1,LIVE","2,FROZEN","3,DELAYED","4,DELAYED_FROZEN","0,UNKNOWN"})
    void marketType(int code,String expected){assertEquals(expected,ExecutionMonitor.type(code));}
    @ParameterizedTest @CsvSource({"100.4,false,false","100.6,true,false","101.1,false,true"})
    void thresholds(double price,boolean warning,boolean critical){
        var signal=new ExecutionMonitor.Signal("PATTERN_V2_STABLE","1.2","s","SPY","LONG",Instant.EPOCH,BigDecimal.valueOf(100),"UNKNOWN",null,null,true,false,"DAILY",null,null,null,null,null,null,true,"IBKR");
        var order=new ExecutionMonitor.Intent("s",1,"DU1","BUY","LMT",100.,100.,null,10,Instant.EPOCH,null);
        when(store.read("SIGNAL",ExecutionMonitor.Signal.class)).thenReturn(List.of(signal));when(store.read("INTENT",ExecutionMonitor.Intent.class)).thenReturn(List.of(order));
        var row=monitor.row(new ExecutionMonitor.Fill("e",1,3,"SPY",1,"BOT",BigDecimal.ONE,price,"",null,Instant.EPOCH));
        assertEquals(warning,row.states().contains("EXECUTION_DEVIATION_WARNING"));assertEquals(critical,row.states().contains("EXECUTION_DEVIATION_CRITICAL"));
        assertNull(row.quoteDeviation());assertNull(row.slippage());assertTrue(row.states().contains("LATENCY_UNKNOWN"));assertEquals("UNFAVORABLE",row.favorability());
    }
    @Test void missingSignalIsExplicit(){var row=monitor.row(new ExecutionMonitor.Fill("e",1,0,"SPY",0,"BOT",BigDecimal.ONE,100,"",null,Instant.EPOCH));assertNull(row.deviation());assertTrue(row.states().contains("SIGNAL_NOT_ASSOCIATED"));}
    @Test void missingPrice(){assertNull(ExecutionMonitor.deviation(100,null));assertNull(ExecutionMonitor.deviation(100,BigDecimal.ZERO));}
    @Test void disconnectRequiresFreshData(){monitor.onNextValidId(1);monitor.onError(new IbkrError(-1,1100,"lost",null));assertEquals(false,monitor.dashboard().get("connected"));monitor.onError(new IbkrError(-1,1101,"restored",null));assertEquals(true,monitor.dashboard().get("connected"));assertTrue(monitor.unreliable("SPY"));}
    @Test void exportRetainsOtherSheets()throws Exception{try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook()){book.createSheet("TRADES").createRow(0).createCell(0).setCellValue("original");ExecutionMonitorController.sheet(book,List.of());assertEquals("original",book.getSheet("TRADES").getRow(0).getCell(0).getStringCellValue());assertNotNull(book.getSheet("PATTERN_V2_EXECUTION_MONITOR"));}}
    @Test void observationDefaultAndInvalidSettings(){assertFalse(monitor.settings().blockNewPaperOrdersOnUnreliableMarketData());assertThrows(IllegalArgumentException.class,()->new ExecutionMonitor.Settings(Double.NaN,1,false));}
}
