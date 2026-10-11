package com.shiftnest.attendance;
import java.time.LocalDate;
/** Material calendars encode days as UTC milliseconds, independently of device timezone. */
public final class ShiftNestDates {
    private ShiftNestDates(){}
    public static long millis(LocalDate day){return Math.multiplyExact(day.toEpochDay(),86400000L);}
    public static LocalDate date(long millis){return LocalDate.ofEpochDay(Math.floorDiv(millis,86400000L));}
}
