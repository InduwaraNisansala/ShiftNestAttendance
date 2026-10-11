package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class AttendanceHistoryActivity extends ShiftNestBaseActivity {
    private final List<DocumentSnapshot> records=new ArrayList<>();
    private LinearLayout rows;
    private TextView summary;
    private Button refresh;
    private String employeeId;
    private int generation;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);page("Attendance History");if(AdminSession.personal(this))screen.addView(button("Back to Admin Panel",()->AdminSession.panel(this)));
        summary=text("Loading attendance history...",18);screen.addView(summary);
        refresh=button("Refresh attendance history",this::load);screen.addView(refresh);
        rows=new LinearLayout(this);rows.setOrientation(1);screen.addView(rows);
        employeeId=getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("employeeId","");
        if(employeeId.isEmpty()){summary.setText("Employee information is unavailable. Sign in again.");refresh.setEnabled(false);return;}
        load();
    }
    private void load(){
        final int run=++generation;refresh.setEnabled(false);summary.setText("Loading attendance history...");rows.removeAllViews();
        ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("attendance").whereEqualTo("employeeId",employeeId).get())
            .addOnSuccessListener(q->{
                if(run!=generation||isFinishing()||isDestroyed())return;
                refresh.setEnabled(true);records.clear();records.addAll(q.getDocuments());records.sort(Comparator.comparing((DocumentSnapshot d)->ShiftNestData.string(d,"date")).reversed());
                summary.setText("Saved records: "+records.size()+(q.getMetadata().isFromCache()?"\nShowing saved offline data. Connect and tap Refresh for current records.":""));
                if(records.isEmpty()){rows.addView(text("No attendance records found.",16));return;}
                List<String[]> data=new ArrayList<>();
                for(DocumentSnapshot d:records)data.add(new String[]{ShiftNestData.string(d,"date"),ShiftNestData.string(d,"checkIn"),ShiftNestData.string(d,"checkOut"),ShiftNestData.string(d,"status")+" / "+ShiftNestData.string(d,"workStatus"),ShiftNestData.string(d,"locationName")});
                rows.addView(ShiftNestTable.create(this,new String[]{"Date","Check In","Check Out","Status / Work","Location"},data,null));
            }).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;refresh.setEnabled(true);summary.setText(backendError("Load attendance history",e)+"\nTap Refresh attendance history to retry.");});
    }
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
