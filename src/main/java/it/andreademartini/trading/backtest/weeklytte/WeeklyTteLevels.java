package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.WeeklyLevel;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;

/** Calendar-week reference validation; current-week aggregates are descriptive only. */
public final class WeeklyTteLevels {
    private WeeklyTteLevels() {}
    public static LocalDate monday(LocalDate d){return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    public static LocalDate date(HistoricalBar b){return b.dateTime().toLocalDate();}
    public static boolean complete(LocalDate week,List<HistoricalBar> bars) {
        if(week.getYear()<2010)return false;
        return bars.stream().map(WeeklyTteLevels::date).toList().equals(ResearchSessionCalendar.weekSessions(week));
    }
    public static List<WeeklyLevel> calculate(String symbol,List<HistoricalBar> bars,LocalDate from,LocalDate end) {
        var groups=new TreeMap<LocalDate,List<HistoricalBar>>();
        for(var b:bars)groups.computeIfAbsent(monday(date(b)),k->new ArrayList<>()).add(b);
        var result=new ArrayList<WeeklyLevel>();
        for(var item:groups.entrySet()) {
            var w=item.getKey();if(w.plusDays(6).isBefore(from)||w.isAfter(end))continue;
            var current=item.getValue();var previous=groups.getOrDefault(w.minusWeeks(1),List.of());
            boolean valid=complete(w.minusWeeks(1),previous), done=complete(w,current)&&!ResearchSessionCalendar.weekSessions(w).getLast().isAfter(end);
            Double level=valid?new WeeklyLevelService(null).previousWeekLevels(symbol,w,previous).orElseThrow().previousWeekHigh().doubleValue():null;
            double open=current.getFirst().open(),high=current.stream().mapToDouble(HistoricalBar::high).max().orElseThrow(),
                    low=current.stream().mapToDouble(HistoricalBar::low).min().orElseThrow(),close=current.getLast().close();
            LocalDate touch=null;Integer index=null;
            if(valid)for(var b:current)if(b.high()>=level){touch=date(b);index=ResearchSessionCalendar.weekSessions(w).indexOf(touch)+1;break;}
            String trend=valid?(previous.getLast().close()>previous.getFirst().open()?"PREVIOUS_WEEK_UP":"PREVIOUS_WEEK_DOWN_OR_FLAT"):"UNAVAILABLE";
            result.add(new WeeklyLevel(symbol,w,w.plusDays(6),w.minusDays(1),level,open,high,low,close,
                    valid?touch!=null:null,touch,index,valid?(level-open)/level*100:null,(high-low)/open*100,
                    trend,"UNAVAILABLE",!valid?"INCOMPLETE_REFERENCE_WEEK":done?"VALID":"INCOMPLETE_CURRENT_WEEK",done));
        }
        return List.copyOf(result);
    }
}
