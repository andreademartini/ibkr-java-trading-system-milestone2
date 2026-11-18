package it.andreademartini.trading.paper.patternv1;
import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.orders.IbkrOrderCommand;
import java.time.*;
import java.util.*;

/** Broker boundary. Tests supply a simulated broker; production requires verified session identity. */
public interface PatternPaperBroker {
    Connection connection();
    void connectReadOnly();
    BrokerSnapshot reconcile();
    PatternPaperCalendar calendar(String symbol);
    List<HistoricalBar> dailyBars(String symbol);
    Quote quote(String symbol);
    List<IbkrOrderCommand> bracket(SignalRecord signal, long quantity, String account);
    IbkrOrderCommand timeout(Trade trade, long quantity, String account, String ocaGroup);
    void place(IbkrOrderCommand command);
    void cancelVerifiedUnfilledEntry(BrokerOrder order);
    List<IbkrOrderCommand> restoreProtection(Trade trade, List<BrokerOrder> existing, String account);
    List<ExecutionRecord> drainExecutions();
    List<CommissionRecord> drainCommissions();
    List<BrokerOrder> drainOrders();
    List<String> drainErrors();
    List<String> limitations();
    /** Explicit operator approval, bound to the current broker connection; never inferred from a port. */
    default void authorizePaperSession(String account,long epoch,String confirmation){throw new IllegalStateException("Session approval unsupported");}
    default void connectPaperSession(){connectReadOnly();}
    default boolean realtime(String symbol){return connection().realtimeData();}
}
