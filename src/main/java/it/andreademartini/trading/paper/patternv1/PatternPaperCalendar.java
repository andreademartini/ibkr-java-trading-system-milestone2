package it.andreademartini.trading.paper.patternv1;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** IBKR liquidHours is the authority: absent coverage never means a trading day. */
public record PatternPaperCalendar(String symbol, String timezone, Instant retrievedAt,
                                   Set<LocalDate> coveredDates, List<Session> sessions) {
    public record Session(LocalDate date, Instant open, Instant close) {}
    private static final DateTimeFormatter DATE=DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HHmm");
    public static PatternPaperCalendar parse(String symbol,String timezone,String hours,Instant now){
        String zone=switch(timezone){case "US/Eastern","EST","EST5EDT"->"America/New_York";case "US/Central","CST"->"America/Chicago";default->timezone;};
        var id=ZoneId.of(zone);var covered=new TreeSet<LocalDate>();var sessions=new ArrayList<Session>();
        if(hours==null||hours.isBlank())throw new IllegalArgumentException("Calendario IBKR assente: "+symbol);
        for(String day:hours.split(";")){
            String[] dateAndHours=day.split(":",2);if(dateAndHours.length!=2)throw new IllegalArgumentException("Formato liquidHours non valido");
            var date=LocalDate.parse(dateAndHours[0],DATE);covered.add(date);
            if(dateAndHours[1].equals("CLOSED"))continue;
            // Multiple RTH segments are intentionally unsupported until an instrument-specific policy exists.
            if(dateAndHours[1].contains(","))throw new IllegalArgumentException("Sessione RTH segmentata non supportata: "+symbol);
            String[] bounds=dateAndHours[1].split("-");if(bounds.length!=2)throw new IllegalArgumentException("Sessione IBKR non valida");
            var open=boundary(date,bounds[0],id);var close=boundary(date,bounds[1],id);
            if(!close.isAfter(open))throw new IllegalArgumentException("Chiusura sessione non valida");
            sessions.add(new Session(date,open,close));
        }
        sessions.sort(Comparator.comparing(Session::open));
        return new PatternPaperCalendar(symbol,zone,now,Set.copyOf(covered),List.copyOf(sessions));
    }
    private static Instant boundary(LocalDate day,String text,ZoneId zone){
        String[] parts=text.split(":");var date=parts.length==2?LocalDate.parse(parts[0],DATE):day;
        var time=LocalTime.parse(parts[parts.length-1],TIME);var local=LocalDateTime.of(date,time);
        if(zone.getRules().getValidOffsets(local).size()!=1)throw new IllegalArgumentException("Orario sessione ambiguo per DST");
        return local.atZone(zone).toInstant();
    }
    public boolean fresh(Instant now){return !retrievedAt.isAfter(now)&&Duration.between(retrievedAt,now).compareTo(Duration.ofHours(24))<0;}
    public LocalDate localDate(Instant now){return now.atZone(ZoneId.of(timezone)).toLocalDate();}
    public Optional<Session> session(LocalDate date){return sessions.stream().filter(s->s.date().equals(date)).findFirst();}
    public Optional<Session> next(Instant now){return sessions.stream().filter(s->s.open().isAfter(now)).findFirst();}
    public Optional<Session> latestClosed(Instant now){return sessions.stream().filter(s->!s.close().plusSeconds(60).isAfter(now)).max(Comparator.comparing(Session::close));}
    public int holdingBars(LocalDate entered,LocalDate through){
        for(LocalDate day=entered;!day.isAfter(through);day=day.plusDays(1))if(!coveredDates.contains(day))throw new IllegalStateException("Copertura calendario mancante: "+day);
        return (int)sessions.stream().filter(s->!s.date().isBefore(entered)&&!s.date().isAfter(through)).count();
    }
}
