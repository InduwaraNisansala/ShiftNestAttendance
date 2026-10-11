import com.shiftnest.attendance.ShiftNestLeaveMath;
import java.time.LocalDate;
public class LeaveMathCheck {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        LocalDate today=LocalDate.of(2026,10,7),future=today.plusDays(1),past=today.minusDays(1);
        check(ShiftNestLeaveMath.rangeError(past,today,today,true).isEmpty());
        check(!ShiftNestLeaveMath.rangeError(past,future,today,true).isEmpty());
        check(!ShiftNestLeaveMath.rangeError(future,future,today,true).isEmpty());
        check(ShiftNestLeaveMath.rangeError(today,future,today,false).isEmpty());
        check(!ShiftNestLeaveMath.rangeError(future,today,today,false).isEmpty());
        for(String value:new String[]{"2026-02-30","2026-99-99","2026-1-01","",null})check(!ShiftNestLeaveMath.validDate(value));
        check(ShiftNestLeaveMath.validDate("2024-02-29"));
        check(!ShiftNestLeaveMath.validDate("2026-02-29"));
        check(ShiftNestLeaveMath.completedDay("2026-10-07","08:00","17:00","Completed","2026-10-07"));
        check(!ShiftNestLeaveMath.completedDay("2026-10-08","08:00","17:00","Completed","2026-10-07"));
        check(!ShiftNestLeaveMath.completedDay("2026-02-30","08:00","17:00","Completed","2026-10-07"));
        check(!ShiftNestLeaveMath.completedDay("2026-10-07","08:00","","Completed","2026-10-07"));
        check(!ShiftNestLeaveMath.completedDay("2026-10-07","08:00","17:00","Working","2026-10-07"));
        check(ShiftNestLeaveMath.validQuota(2026L,20L,3L,"2026-10-07"));
        for(long[] q:new long[][]{{2025,20,3},{2026,-1,0},{2026,20,-1},{2026,20,21},{2026,367,0}})check(!ShiftNestLeaveMath.validQuota(q[0],q[1],q[2],"2026-10-07"));
        check(!ShiftNestLeaveMath.validQuota(null,20L,0L,"2026-10-07"));
        check(ShiftNestLeaveMath.available(2,0,1)==1); // Pending reserves one.
        check(ShiftNestLeaveMath.available(2,1,0)==1); // Approval does not deduct twice.
        check(ShiftNestLeaveMath.available(2,0,0)==2); // Rejection releases the reservation.
        check(ShiftNestLeaveMath.available(1,2,0)==0);
        System.out.println(checks+" date/quota/balance checks passed.");
    }
}
