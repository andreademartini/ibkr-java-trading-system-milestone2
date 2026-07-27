package it.andreademartini.trading.marketdata;

import com.ib.client.Contract;
import it.andreademartini.trading.config.TradingSystemProperties;
import org.springframework.stereotype.Component;

@Component
public class IbkrContractFactory {

    public Contract create(TradingSystemProperties.InstrumentProperties instrument) {
        Contract contract = new Contract();
        contract.symbol(instrument.getSymbol().trim().toUpperCase());
        contract.secType(instrument.getSecurityType().trim().toUpperCase());
        contract.exchange(instrument.getExchange().trim().toUpperCase());
        contract.currency(instrument.getCurrency().trim().toUpperCase());

        if (instrument.getPrimaryExchange() != null
                && !instrument.getPrimaryExchange().isBlank()) {
            contract.primaryExch(instrument.getPrimaryExchange().trim().toUpperCase());
        }

        return contract;
    }
}
