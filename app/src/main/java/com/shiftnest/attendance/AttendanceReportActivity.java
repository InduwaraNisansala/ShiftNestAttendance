package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.time.*;
public class AttendanceReportActivity extends ShiftNestBaseActivity {
    private LocalDate from=LocalDate.parse(ShiftNestData.date()).minusMonths(2),to=LocalDate.parse(ShiftNestData.date());private Button start,end,pdf,load;private Spinner filter;private LinearLayout table;private TextView summary;private final List<DocumentSnapshot> records=new ArrayList<>(),notices=new ArrayList<>();private final List<String[]>rows=new ArrayList<>();private final String[] headers={"Date","Employee","Department","Check In","Check Out","Status / Site","Late message"};private int generation;private boolean reportLoading;
    @Override protected void onCreate(Bundle s){super.onCreate(s);if(isFinishing())return;page("Attendance Report");if(s!=null){from=LocalDate.parse(s.getString("from",from.toString()));to=LocalDate.parse(s.getString("to",to.toString()));}start=button("From: "+from,()->pick(true));end=button("To: "+to,()->pick(false));screen.addView(start);screen.addView(end);filter=departments(this::render);load=button("Load attendance",this::load);screen.addView(load);screen.addView(button("Daily summary / charts (To date)",()->{if(!rangeError().isEmpty()){problem(rangeError());return;}android.content.Intent i=new android.content.Intent(this,DailyAttendanceReportActivity.class);i.putExtra("reportDate",to.toString());startActivity(i);}));pdf=button("Download PDF",()->exportPdf("Attendance "+from+" to "+to,headers,rows));pdf.setEnabled(false);screen.addView(pdf);summary=text("Loading saved attendance…",16);screen.addView(summary);table=new LinearLayout(this);table.setOrientation(1);screen.addView(table);load();bindDateChoice("attendance_from",d->dateChanged(true,d));bindDateChoice("attendance_to",d->dateChanged(false,d));}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("from",from.toString());s.putString("to",to.toString());}
    private void pick(boolean first){chooseDate(first?"attendance_from":"attendance_to",first?"Attendance: From date":"Attendance: To date",first?from:to,day->dateChanged(first,day));}
    private void dateChanged(boolean first,LocalDate day){
        if(first){from=day;start.setText("From: "+from);}else{to=day;end.setText("To: "+to);}
        generation++;reportLoading=false;records.clear();rows.clear();table.removeAllViews();pdf.setEnabled(false);load.setEnabled(true);
        String error=rangeError();if(!error.isEmpty()){summary.setText(error);problem(error);return;}
        load();
    }
    private String rangeError(){return ShiftNestLeaveMath.rangeError(from,to,LocalDate.parse(ShiftNestData.date()),true);}
    private void load(){
        String error=rangeError();if(!error.isEmpty()){summary.setText(error);problem(error);return;}
        final int run=++generation;reportLoading=true;load.setEnabled(false);pdf.setEnabled(false);records.clear();notices.clear();rows.clear();table.removeAllViews();summary.setText("Loading attendance and late messages...");
        com.google.android.gms.tasks.Task<QuerySnapshot> attendance=ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("attendance").whereGreaterThanOrEqualTo("date",from.toString()).whereLessThanOrEqualTo("date",to.toString()).get(Source.SERVER));
        com.google.android.gms.tasks.Task<QuerySnapshot> messages=ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("attendanceNotices").whereGreaterThanOrEqualTo("date",from.toString()).whereLessThanOrEqualTo("date",to.toString()).get(Source.SERVER));
        com.google.android.gms.tasks.Tasks.whenAllSuccess(attendance,messages).addOnSuccessListener(result->{if(run!=generation||isFinishing()||isDestroyed())return;records.addAll(attendance.getResult().getDocuments());notices.addAll(messages.getResult().getDocuments());reportLoading=false;load.setEnabled(true);render();})
            .addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;reportLoading=false;load.setEnabled(true);pdf.setEnabled(false);summary.setText(backendError("Load attendance / late messages",e)+"\nReload to retry. An incomplete report cannot be exported.");});
    }
    private void render(){
        if(table==null||filter==null||reportLoading)return;if(!rangeError().isEmpty()){summary.setText(rangeError());pdf.setEnabled(false);return;}
        rows.clear();String selected=String.valueOf(filter.getSelectedItem());int completed=0;
        Map<String,DocumentSnapshot> notes=new HashMap<>();for(DocumentSnapshot d:notices)notes.put(ShiftNestData.string(d,"employeeId")+"_"+ShiftNestData.string(d,"date"),d);
        Set<String> seen=new HashSet<>();
        for(DocumentSnapshot d:records){String dep=ShiftNestData.department(ShiftNestData.string(d,"employeeRole"));String key=ShiftNestData.string(d,"employeeId")+"_"+ShiftNestData.string(d,"date");seen.add(key);if(!"All Employees".equals(selected)&&!dep.equals(selected))continue;String out=ShiftNestData.string(d,"checkOut");if(ShiftNestLeaveMath.completedDay(ShiftNestData.string(d,"date"),ShiftNestData.string(d,"checkIn"),out,ShiftNestData.string(d,"workStatus"),ShiftNestData.date()))completed++;DocumentSnapshot note=notes.get(key);rows.add(new String[]{ShiftNestData.string(d,"date"),ShiftNestData.string(d,"employeeName"),dep,ShiftNestData.string(d,"checkIn"),out.isEmpty()?"--":out,ShiftNestData.string(d,"status")+" / "+ShiftNestData.string(d,"locationName")+" | In site: "+ShiftNestData.string(d,"checkInSiteName")+" / "+ShiftNestData.string(d,"checkInLocationName")+" | Out site: "+ShiftNestData.string(d,"checkOutSiteName")+" / "+ShiftNestData.string(d,"checkOutLocationName"),note==null?"":ShiftNestData.string(note,"lateMessage")});}
        // Keep an explanation visible even when the employee has not checked in yet.
        for(Map.Entry<String,DocumentSnapshot> entry:notes.entrySet()){if(seen.contains(entry.getKey()))continue;DocumentSnapshot d=entry.getValue();String dep=ShiftNestData.department(ShiftNestData.string(d,"employeeRole"));if(!"All Employees".equals(selected)&&!dep.equals(selected))continue;rows.add(new String[]{ShiftNestData.string(d,"date"),ShiftNestData.string(d,"employeeName"),dep,"--","--","No check-in recorded",ShiftNestData.string(d,"lateMessage")});}
        rows.sort(Comparator.comparing((String[] row)->row[0]).reversed().thenComparing(row->row[1]));summary.setText(from+" to "+to+"\nReport rows: "+rows.size()+" • Completed: "+completed+"\nLate messages are employee explanations; they do not mark attendance or grant approval. Date filters do not delete older records.");table.removeAllViews();table.addView(ShiftNestTable.create(this,headers,rows,null));pdf.setEnabled(!rows.isEmpty());
    }
}
