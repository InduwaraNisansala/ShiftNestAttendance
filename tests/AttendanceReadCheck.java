import java.util.*;
import java.util.function.Consumer;
public class AttendanceReadCheck {
    String employeeId="demo-sales", attendanceDay="";
    int attendanceGeneration, resumes, stops;
    boolean attendanceLoaded, checkedInToday, checkedOutToday, isCheckingIn, isCheckingOut;
    Text attendanceStatus=new Text(),tvCheckIn=new Text(),tvCheckOut=new Text();
    Button outingNotice,lateNotice;
 Button btnCheckIn=new Button(),btnCheckOut=new Button();
    Store db=new Store();
    boolean isFinishing(){return false;} boolean isDestroyed(){return false;}
    void loadEmployeeLocationAndResumeTracking(){resumes++;} void stopLocationTracking(){stops++;}
    String backendError(String operation,Exception error){return operation+": "+error.getMessage();}
    static class Text {String value;void setText(String value){this.value=value;}}
    static class Button {boolean enabled;void setEnabled(boolean value){enabled=value;}}
    static class Document {String in,out,status;Document(String in,String out,String status){this.in=in;this.out=out;this.status=status;}}
    static class ShiftNestData {
        static String day="2026-10-09";static String date(){return day;}
        static String string(Document d,String key){return key.equals("checkIn")?d.in:key.equals("checkOut")?d.out:d.status;}
    }
    enum Source {SERVER}
    static class Records {List<Document> documents=new ArrayList<>();boolean isEmpty(){return documents.isEmpty();}List<Document> getDocuments(){return documents;}}
    static class Task<T> {T data;Exception error;Task(T data,Exception error){this.data=data;this.error=error;}Task<T> addOnSuccessListener(Consumer<T> listener){if(error==null)listener.accept(data);return this;}Task<T> addOnFailureListener(Consumer<Exception> listener){if(error!=null)listener.accept(error);return this;}}
    static class ShiftNestRequests {static <T> Task<T> read(Task<T> task){return task;}}
    static class Store {
        Records records=new Records();Exception error;Map<String,Object> filters;int queries;
        Store collection(String name){if(!name.equals("attendance"))throw new AssertionError(name);filters=new HashMap<>();return this;}
        Store whereEqualTo(String key,Object value){filters.put(key,value);return this;}
        Store limit(int limit){if(limit!=1)throw new AssertionError("limit");return this;}
        Task<Records> get(Source source){if(source!=Source.SERVER||!filters.equals(Map.of("employeeId","demo-sales","date",ShiftNestData.date())))throw new AssertionError("employee/date ownership query");queries++;return new Task<>(records,error);}
    }
    private void loadTodayAttendance() {
        if(employeeId==null||employeeId.isEmpty())return;
        final int request=++attendanceGeneration;
        final String day=ShiftNestData.date();
        attendanceLoaded=false;
        updateAttendanceButtons();
        if(attendanceStatus!=null)attendanceStatus.setText("Loading attendance for "+ShiftNestData.date()+"...");
        // A missing document cannot satisfy resource.data ownership rules.
        // Query by this employee/date so an empty result is a valid pre-check-in state.
        ShiftNestRequests.read(db.collection("attendance").whereEqualTo("employeeId",employeeId)
                .whereEqualTo("date",day).limit(1).get(Source.SERVER))
            .addOnSuccessListener(records->{
                if(request!=attendanceGeneration||isCheckingIn||isCheckingOut||isFinishing()||isDestroyed())return;
                if(!day.equals(ShiftNestData.date())){loadTodayAttendance();return;}
                Document document=records.isEmpty()?null:records.getDocuments().get(0);
                String in=document==null?"":ShiftNestData.string(document,"checkIn"),out=document==null?"":ShiftNestData.string(document,"checkOut");
                applyAttendanceState(day,in,out);
                if(attendanceStatus!=null)attendanceStatus.setText(ShiftNestData.date()+" • "+(in.isEmpty()?"Not checked in":out.isEmpty()?"Checked in / working":"Checked out"));
                if(!in.isEmpty()&&out.isEmpty()&&"Working".equals(ShiftNestData.string(document,"workStatus")))loadEmployeeLocationAndResumeTracking();
                if(!out.isEmpty())stopLocationTracking();
            }).addOnFailureListener(e->{
                if(request!=attendanceGeneration||isFinishing()||isDestroyed())return;
                if(attendanceStatus!=null)attendanceStatus.setText(backendError("Load today’s attendance",e)+"\nTap Refresh attendance / leave balance.");
            });
    }
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
    }
    void expect(boolean in,boolean out,String stage){if(btnCheckIn.enabled!=in||btnCheckOut.enabled!=out)throw new AssertionError(stage);}
    public static void main(String[] args){
        AttendanceReadCheck a=new AttendanceReadCheck();
        a.updateAttendanceButtons();a.expect(false,false,"loading");
        a.loadTodayAttendance();a.expect(true,false,"empty server query must enable first check-in");
        a.db.records.documents.add(new Document("08:00:00","","Working"));
        a.loadTodayAttendance();a.expect(false,true,"reopen working attendance");
        if(a.resumes!=1)throw new AssertionError("tracking resume");
        a.isCheckingOut=true;a.updateAttendanceButtons();a.expect(false,false,"saving checkout");
        a.isCheckingOut=false;a.applyAttendanceState(ShiftNestData.day,"08:00:00","17:00:00");a.expect(false,false,"checkout confirmed");
        a.db.records.documents.set(0,new Document("08:00:00","17:00:00","Completed"));
        a.loadTodayAttendance();a.expect(false,false,"reopen completed attendance");
        if(a.stops!=1)throw new AssertionError("tracking stop");
        a.db.error=new Exception("permission denied");a.loadTodayAttendance();a.expect(false,false,"failed read must not fabricate empty state");
        if(!a.attendanceStatus.value.contains("permission denied"))throw new AssertionError("visible error");
        a.db.error=null;a.db.records.documents.clear();ShiftNestData.day="2026-10-10";
        a.loadTodayAttendance();a.expect(true,false,"next day first check-in");
        a.isCheckingIn=true;a.updateAttendanceButtons();a.expect(false,false,"saving check-in");
        a.isCheckingIn=false;a.applyAttendanceState(ShiftNestData.day,"08:00:00","");a.expect(false,true,"check-in confirmed");
        System.out.println("PASS: extracted attendance code: empty server query, working/completed reopen, success transitions, failed reads and next day.");
    }
}
