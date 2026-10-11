package com.shiftnest.attendance;
import java.time.LocalDate;
public final class ShiftNestLeaveMath {
    private ShiftNestLeaveMath() {}
    public static boolean validDate(String date) {
        try { return date != null && date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && LocalDate.parse(date).toString().equals(date); }
        catch (RuntimeException e) { return false; }
    }
    public static String rangeError(LocalDate from, LocalDate to, LocalDate today, boolean attendance) {
        if (from == null || to == null) return "Select both dates.";
        if (from.isAfter(to)) return "From date must be on or before To date.";
        if (attendance && (from.isAfter(today) || to.isAfter(today))) return "Attendance reports cannot include future dates.";
        return "";
    }
    public static boolean completedDay(String date, String checkIn, String checkOut, String status, String today) {
        return validDate(date) && validDate(today) && date.compareTo(today)<=0 && checkIn!=null && !checkIn.isEmpty()
            && checkOut!=null && !checkOut.isEmpty() && "Completed".equals(status);
    }
    public static boolean validQuota(Long year, Long quota, Long opening, String date) {
        return validDate(date) && year!=null && quota!=null && opening!=null && year>=2000 && year<=2100
            && quota>=0 && quota<=366 && opening>=0 && opening<=quota && year==Long.parseLong(date.substring(0,4));
    }
    public static long available(long earned, long approved, long pending) {
        return Math.max(0,earned-approved-pending);
    }
}
