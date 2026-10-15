package it.andreademartini.trading.backtest.patternv2;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** Missing legacy fields default; explicit nulls and type coercions are validation errors. */
public final class MarketRegimeFilterJson {
    private MarketRegimeFilterJson() {}
    /** Preserve the legacy JSON consumed by the frozen stable-V1 adapter when all settings are defaults. */
    public static final class DefaultValue {
        @Override public boolean equals(Object value){return Boolean.FALSE.equals(value)||Integer.valueOf(14).equals(value)||Double.valueOf(25).equals(value)||value==PatternV2Configuration.MarketRegimeFilterMode.EXCLUDE_WITH_TREND;}
        @Override public int hashCode(){return 0;}
    }
    private abstract static class Strict<T> extends JsonDeserializer<T> {
        @Override public T getNullValue(DeserializationContext c) throws JsonMappingException {throw JsonMappingException.from(c,"Market Regime Filter parameters cannot be null");}
    }
    public static final class Enabled extends Strict<Boolean> {
        @Override public Boolean getAbsentValue(DeserializationContext c){return false;}
        @Override public Boolean deserialize(JsonParser p,DeserializationContext c)throws IOException {if(!p.hasToken(JsonToken.VALUE_TRUE)&&!p.hasToken(JsonToken.VALUE_FALSE))throw JsonMappingException.from(p,"Market Regime Filter enabled must be boolean");return p.getBooleanValue();}
    }
    public static final class Period extends Strict<Integer> {
        @Override public Integer getAbsentValue(DeserializationContext c){return 14;}
        @Override public Integer deserialize(JsonParser p,DeserializationContext c)throws IOException {if(!p.hasToken(JsonToken.VALUE_NUMBER_INT))throw JsonMappingException.from(p,"ADX Period must be an integer");return p.getIntValue();}
    }
    public static final class Threshold extends Strict<Double> {
        @Override public Double getAbsentValue(DeserializationContext c){return 25.0;}
        @Override public Double deserialize(JsonParser p,DeserializationContext c)throws IOException {if(!p.currentToken().isNumeric())throw JsonMappingException.from(p,"ADX Threshold must be numeric");return p.getDoubleValue();}
    }
    public static final class Mode extends Strict<PatternV2Configuration.MarketRegimeFilterMode> {
        @Override public PatternV2Configuration.MarketRegimeFilterMode getAbsentValue(DeserializationContext c){return PatternV2Configuration.MarketRegimeFilterMode.EXCLUDE_WITH_TREND;}
        @Override public PatternV2Configuration.MarketRegimeFilterMode deserialize(JsonParser p,DeserializationContext c)throws IOException {if(!p.hasToken(JsonToken.VALUE_STRING)||!"EXCLUDE_WITH_TREND".equals(p.getText()))throw JsonMappingException.from(p,"Unsupported Market Regime Filter mode");return PatternV2Configuration.MarketRegimeFilterMode.EXCLUDE_WITH_TREND;}
    }
}
