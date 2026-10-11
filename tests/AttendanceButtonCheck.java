public class AttendanceButtonCheck {
 boolean attendanceLoaded,checkedInToday,checkedOutToday,isCheckingIn,isCheckingOut;
 String attendanceDay=""; int reloads;
 Button outingNotice=new Button(),lateNotice=new Button();
 Button btnCheckIn=new Button(),btnCheckOut=new Button();
 Text tvCheckIn=new Text(),tvCheckOut=new Text();
 static class Button {boolean enabled;void setEnabled(boolean v){enabled=v;}}
 static class Text {String value;void setText(String v){value=v;}}
 static class ShiftNestData {static String day="2026-10-08";static String date(){return day;}}
 void loadTodayAttendance(){reloads++;attendanceLoaded=false;updateAttendanceButtons();}
    private void applyAttendanceState(String day, String checkIn, String checkOut) {
        if (!day.equals(ShiftNestData.date())) { loadTodayAttendance(); return; }
        attendanceDay=day;
        attendanceLoaded=true;
        checkedInToday=!checkIn.isEmpty();
        checkedOutToday=!checkOut.isEmpty();
        tvCheckIn.setText("Check In: "+(checkIn.isEmpty()?"--":checkIn));
        tvCheckOut.setText("Check Out: "+(checkOut.isEmpty()?"--":checkOut));
        updateAttendanceButtons();
    }
    private void updateAttendanceButtons() {
        boolean ready=attendanceLoaded && !isCheckingIn && !isCheckingOut
                && attendanceDay.equals(ShiftNestData.date());
        btnCheckIn.setEnabled(ready && !checkedInToday && !checkedOutToday);
        btnCheckOut.setEnabled(ready && checkedInToday && !checkedOutToday);
        if(outingNotice!=null)outingNotice.setEnabled(ready && checkedInToday && !checkedOutToday);
        if(lateNotice!=null)lateNotice.setEnabled(!isCheckingIn && !isCheckingOut && (!attendanceLoaded || !checkedOutToday));
    }
 void expect(boolean in,boolean out,String stage){if(btnCheckIn.enabled!=in||btnCheckOut.enabled!=out)throw new AssertionError(stage);}
 public static void main(String[] args){
 AttendanceButtonCheck a=new AttendanceButtonCheck();
 a.updateAttendanceButtons();a.expect(false,false,"initial loading");
 a.applyAttendanceState(ShiftNestData.day,"","");a.expect(true,false,"before check in");
 a.isCheckingIn=true;a.updateAttendanceButtons();a.expect(false,false,"check in pending");
 a.isCheckingIn=false;a.applyAttendanceState(ShiftNestData.day,"08:30:00","");a.expect(false,true,"check in confirmed");
 if(!a.tvCheckIn.value.contains("08:30:00"))throw new AssertionError("check in display");
 a.isCheckingOut=true;a.updateAttendanceButtons();a.expect(false,false,"check out pending");
 a.isCheckingOut=false;a.applyAttendanceState(ShiftNestData.day,"08:30:00","17:30:00");a.expect(false,false,"check out confirmed");
 AttendanceButtonCheck reopened=new AttendanceButtonCheck();reopened.applyAttendanceState(ShiftNestData.day,"08:30:00","17:30:00");reopened.expect(false,false,"reopen after check out");
 a.loadTodayAttendance();a.expect(false,false,"uncertain write or refresh");
 a.applyAttendanceState(ShiftNestData.day,"08:30:00","");a.expect(false,true,"server recovery");
 a.applyAttendanceState(ShiftNestData.day,"","17:30:00");a.expect(false,false,"inconsistent completed record");
 ShiftNestData.day="2026-10-09";a.updateAttendanceButtons();a.expect(false,false,"date changed");
 a.applyAttendanceState("2026-10-08","08:30:00","");a.expect(false,false,"stale response");
 if(a.reloads!=2)throw new AssertionError("stale response must reload");
 a.applyAttendanceState(ShiftNestData.day,"","");a.expect(true,false,"next day confirmed");
 a.expect(true,false,"next day confirmed");if(!a.lateNotice.enabled||a.outingNotice.enabled)throw new AssertionError("new day notice buttons");a.applyAttendanceState(ShiftNestData.day,"08:00:00","");if(!a.outingNotice.enabled)throw new AssertionError("outing after check-in");a.applyAttendanceState(ShiftNestData.day,"08:00:00","17:00:00");if(a.outingNotice.enabled||a.lateNotice.enabled)throw new AssertionError("notices after checkout");
 System.out.println("PASS: attendance button transitions, pending requests, reopening, recovery and date rollover.");
 }
}