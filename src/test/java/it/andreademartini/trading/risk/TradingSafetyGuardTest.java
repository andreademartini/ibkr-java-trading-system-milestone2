package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradingSafetyGuardTest {

    @Test
    void paperConfigurationWithOrdersDisabledIsAllowed() {
        TradingSystemProperties properties = new TradingSystemProperties();
        TradingSafetyGuard guard = new TradingSafetyGuard(properties);

        assertDoesNotThrow(guard::validateStartupConfiguration);
        assertDoesNotThrow(guard::assertIbkrConnectionAllowed);
    }

    @Test
    void liveModeIsAlwaysRejected() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.setMode(TradingMode.LIVE);
        TradingSafetyGuard guard = new TradingSafetyGuard(properties);

        assertThrows(IllegalStateException.class, guard::validateStartupConfiguration);
    }

    @Test
    void liveDefaultPortIsRejected() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getIbkr().setPort(7496);
        TradingSafetyGuard guard = new TradingSafetyGuard(properties);

        assertThrows(IllegalStateException.class, guard::validateStartupConfiguration);
    }

    @Test
    void paperOrdersRequireAccountAndConfirmation() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.setOrderSubmissionEnabled(true);
        TradingSafetyGuard guard = new TradingSafetyGuard(properties);

        assertThrows(IllegalStateException.class, guard::validateStartupConfiguration);

        properties.getIbkr().setAccountId("PAPER_ACCOUNT");
        properties.setPaperAccountConfirmation(
                TradingSystemProperties.PAPER_CONFIRMATION_PHRASE);

        assertDoesNotThrow(guard::validateStartupConfiguration);
        assertDoesNotThrow(guard::assertPaperOrderConfiguration);
    }
}
