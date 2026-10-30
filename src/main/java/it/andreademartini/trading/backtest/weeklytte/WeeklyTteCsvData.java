package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.marketdata.stooq.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.math.*;
import java.util.*;

/** Isolated adapter for fractional adjusted STOOQ volumes. OHLC text is preserved byte-for-byte per field. */
public final class WeeklyTteCsvData {
    private WeeklyTteCsvData(){}
    public record Loaded(List<HistoricalBar> bars,int fractionalVolumes,String sourceSha256){}
    public static Loaded load(Path path,String symbol)throws IOException{
        var lines=Files.readAllLines(path,StandardCharsets.UTF_8);if(lines.isEmpty())throw new IllegalArgumentException("Empty CSV: "+symbol);
        var columns=lines.getFirst().split(",",-1);int volume=-1;
        for(int i=0;i<columns.length;i++)if(columns[i].trim().equalsIgnoreCase("Volume"))volume=i;
        int normalized=0;
        if(volume>=0)for(int j=1;j<lines.size();j++){
            if(lines.get(j).isBlank())continue;var cells=lines.get(j).split(",",-1);
            if(cells.length!=columns.length)throw new IllegalArgumentException("Unsupported CSV columns: "+symbol+" row "+j);
            var n=new BigDecimal(cells[volume]);if(n.signum()<0)throw new IllegalArgumentException("Negative volume: "+symbol);
            var rounded=n.setScale(0,RoundingMode.DOWN);rounded.longValueExact();
            if(n.compareTo(rounded)!=0){normalized++;cells[volume]=rounded.toPlainString();lines.set(j,String.join(",",cells));}
        }
        Path temporary=Files.createTempFile("weekly-tte-csv-",".csv");
        try{
            Files.write(temporary,lines,StandardCharsets.UTF_8);var parsed=new StooqCsvParser().parse(temporary,symbol,BarInterval.DAILY,PriceAdjustment.ADJUSTED,true);
            if(parsed.rowsRejected()>0)throw new IllegalArgumentException("Rejected CSV rows: "+symbol+" count="+parsed.rowsRejected());
            String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
            return new Loaded(parsed.bars(),normalized,hash);
        }finally{Files.deleteIfExists(temporary);}
    }
}
