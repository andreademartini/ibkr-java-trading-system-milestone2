package it.andreademartini.trading.backtest.patternv3;

/** Prevent JSON decimal-to-integer coercion of the RVOL lookback. Missing legacy values stay null. */
public final class ResearchFilterJson {
    private ResearchFilterJson(){}
    public static final class Period extends com.fasterxml.jackson.databind.JsonDeserializer<Integer>{
        @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser p,com.fasterxml.jackson.databind.DeserializationContext c)throws java.io.IOException{
            if(!p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT))throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"RVOL period must be an integer");return p.getIntValue();
        }
    }
    public static final class HttpPeriod extends tools.jackson.databind.ValueDeserializer<Integer>{
        @Override public Integer deserialize(tools.jackson.core.JsonParser p,tools.jackson.databind.DeserializationContext c){
            if(!p.hasToken(tools.jackson.core.JsonToken.VALUE_NUMBER_INT))throw tools.jackson.databind.DatabindException.from(p,"RVOL period must be an integer");return p.getIntValue();
        }
    }
}
