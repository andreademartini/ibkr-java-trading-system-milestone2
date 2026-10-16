package it.andreademartini.trading.backtest.execution;

import it.andreademartini.trading.backtest.patternv2.ExitFillPolicy;

/** Run metadata, independent of strategy configuration and its hash. */
public final class ExecutionModel {
    public static final String OPEN_FIRST="OPEN_FIRST/1", LEVEL_ONLY="LEVEL_ONLY/1";
    private ExecutionModel(){}
    public static ExitFillPolicy policy(String version){
        if(version==null||LEVEL_ONLY.equals(version))return ExitFillPolicy.LEVEL_ONLY;
        if(OPEN_FIRST.equals(version))return ExitFillPolicy.OPEN_FIRST;
        throw new IllegalArgumentException("Unknown execution model: "+version);
    }
    public static String version(ExitFillPolicy policy){return policy==ExitFillPolicy.OPEN_FIRST?OPEN_FIRST:LEVEL_ONLY;}
}
