package it.andreademartini.trading.paper.patternv1;
import static it.andreademartini.trading.paper.patternv1.PatternPaperTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import it.andreademartini.trading.config.*;
import it.andreademartini.trading.ibkr.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.orders.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class PatternPaperIbkrBrokerTest {
    @Test void paperPrefixAndManagedAccountsCannotUnlockProductionWrites(){var properties=new TradingSystemProperties();properties.setMode(TradingMode.PAPER);properties.getIbkr().setAccountId(ACCOUNT);properties.getIbkr().setPort(4002);var connection=mock(IbkrConnectionService.class);when(connection.isReady()).thenReturn(true);var api=mock(IbkrApiClient.class);var broker=new PatternPaperIbkrBroker(properties,connection,api,mock(IbkrOrderFactory.class),mock(IbkrContractFactory.class),mock(MarketDataService.class),mock(IbkrHistoricalDataClient.class),new IbkrEWrapper());broker.onManagedAccounts(List.of(ACCOUNT));assertFalse(broker.connection().paperIdentityVerified());assertFalse(broker.connection().writesVerified());assertThrows(IllegalStateException.class,()->broker.bracket(signal("AAPL"),1,ACCOUNT));assertThrows(IllegalStateException.class,()->broker.place(mock(IbkrOrderCommand.class)));verifyNoInteractions(api);}
    @Test void initialConnectionRefusesExistingGlobalWriteFlags(){var properties=new TradingSystemProperties();properties.setMode(TradingMode.PAPER);properties.setOrderSubmissionEnabled(true);var connection=mock(IbkrConnectionService.class);var api=mock(IbkrApiClient.class);var broker=new PatternPaperIbkrBroker(properties,connection,api,mock(IbkrOrderFactory.class),mock(IbkrContractFactory.class),mock(MarketDataService.class),mock(IbkrHistoricalDataClient.class),new IbkrEWrapper());assertThrows(IllegalStateException.class,broker::connectReadOnly);verifyNoInteractions(connection,api);}
    @Test void executionTimeNeedsExplicitTimezone(){assertThrows(IllegalArgumentException.class,()->PatternPaperIbkrBroker.parseExecutionTime("20261009 10:00:00"));assertEquals(java.time.Instant.parse("2026-10-09T14:00:00Z"),PatternPaperIbkrBroker.parseExecutionTime("20261009 10:00:00 US/Eastern"));}
}
