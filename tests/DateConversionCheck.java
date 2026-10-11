import com.shiftnest.attendance.ShiftNestDates;
import java.time.LocalDate;
import java.util.TimeZone;
public class DateConversionCheck {
    public static void main(String[] args){
        String[] zones={"Asia/Colombo","UTC","America/Los_Angeles","Pacific/Kiritimati"};
        String[] days={"2024-02-29","2026-10-06","2026-12-31","2027-01-01","1969-12-31"};
        int checks=0;
        for(String zone:zones){TimeZone.setDefault(TimeZone.getTimeZone(zone));for(String value:days){
            LocalDate day=LocalDate.parse(value);long encoded=ShiftNestDates.millis(day);
            if(!ShiftNestDates.date(encoded).equals(day))throw new AssertionError(zone+" changed "+value);
            if(!ShiftNestDates.date(encoded+86399999L).equals(day))throw new AssertionError("Day boundary: "+zone+" "+value);
            checks+=2;
        }}
        if(ShiftNestDates.millis(LocalDate.of(1970,1,1))!=0)throw new AssertionError("UTC epoch");
        System.out.println("PASS: "+checks+" date/timezone boundary checks and UTC epoch");
    }
}
