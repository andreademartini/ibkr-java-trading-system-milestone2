package it.andreademartini.trading.backtest.patternv3;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Offline US cash-equity session dates, version 1. No connection to PAPER calendars.
 * Full-day exceptions through 2026; scheduled holiday rules through 2028.
 * Shortened sessions remain sessions. Unknown future emergency closures need a version update. */
public final class ResearchSessionCalendar {
    public static final String VERSION="V3_US_EQUITY_SESSIONS/1";
    private static final Set<LocalDate> EXCEPTIONS=Set.of(LocalDate.of(2012,10,29),LocalDate.of(2012,10,30),LocalDate.of(2018,12,5),LocalDate.of(2025,1,9));
    private ResearchSessionCalendar() {}
    public static boolean isSession(LocalDate date) {
        int y=date.getYear();
        if(y<2010||y>2028)throw new IllegalArgumentException("WEEKLY calendar coverage is 2010..2028: "+date);
        var day=date.getDayOfWeek();
        if(day==DayOfWeek.SATURDAY||day==DayOfWeek.SUNDAY||EXCEPTIONS.contains(date))return false;
        var jan=LocalDate.of(y,1,1);
        // A Saturday New Year does not close the preceding year's accounting day.
        if(date.equals(jan)||jan.getDayOfWeek()==DayOfWeek.SUNDAY&&date.equals(jan.plusDays(1)))return false;
        return !Set.of(nth(y,1,DayOfWeek.MONDAY,3),nth(y,2,DayOfWeek.MONDAY,3),easter(y).minusDays(2),
                LocalDate.of(y,5,31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                observed(LocalDate.of(y,7,4)),nth(y,9,DayOfWeek.MONDAY,1),nth(y,11,DayOfWeek.THURSDAY,4),observed(LocalDate.of(y,12,25))).contains(date)
                &&(y<2022||!date.equals(observed(LocalDate.of(y,6,19))));
    }
    public static List<LocalDate> weekSessions(LocalDate date) {
        var monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));var result=new ArrayList<LocalDate>();
        for(int i=0;i<5;i++){var d=monday.plusDays(i);if(isSession(d))result.add(d);}
        return List.copyOf(result);
    }
    private static LocalDate observed(LocalDate date) {return switch(date.getDayOfWeek()){case SATURDAY->date.minusDays(1);case SUNDAY->date.plusDays(1);default->date;};}
    private static LocalDate nth(int y,int m,DayOfWeek day,int n){return LocalDate.of(y,m,1).with(TemporalAdjusters.dayOfWeekInMonth(n,day));}
    private static LocalDate easter(int y){int a=y%19,b=y/100,c=y%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3,h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451;int n=h+l-7*m+114;return LocalDate.of(y,n/31,n%31+1);}
}
