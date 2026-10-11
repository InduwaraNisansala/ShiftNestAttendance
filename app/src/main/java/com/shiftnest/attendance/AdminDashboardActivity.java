package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

public class AdminDashboardActivity extends ShiftNestBaseActivity {
    private TextView pending,outside,inbox;
    private String employeeError="",locationError="";
    private final List<ListenerRegistration> listeners=new ArrayList<>();
    private final Map<String,DocumentSnapshot> employees=new HashMap<>(),locations=new HashMap<>();
    private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable stale=new Runnable(){public void run(){renderOutside();timer.postDelayed(this,60000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;page("Admin Dashboard");screen.addView(text("ShiftNest • Administration",24));
        inbox=text("Loading recent notifications...",16);pending=text("Loading pending leave requests...",16);outside=text("Loading location exceptions...",16);screen.addView(inbox);screen.addView(pending);screen.addView(outside);
        screen.addView(button("Notifications / Alerts",()->open(NotificationsActivity.class)));
        screen.addView(button("Refresh dashboard alerts",this::watch));
        screen.addView(button("My Attendance",()->open(AdminPersonalActivity.class)));screen.addView(button("Add Employee",()->open(AddEmployeeActivity.class)));screen.addView(button("Add Location",()->open(AddLocationActivity.class)));screen.addView(button("Employee List",()->open(EmployeeListActivity.class)));screen.addView(button("Attendance Report",()->open(AttendanceReportActivity.class)));screen.addView(button("Location Tracking",()->open(AdminLocationTrackingActivity.class)));screen.addView(button("Leave Sheet / Approvals",()->open(LeaveSheetActivity.class)));screen.addView(button("Holiday calendar / Department notices",()->open(DepartmentCalendarActivity.class)));screen.addView(button("Settings",()->open(SettingsActivity.class)));screen.addView(button("Logout",this::logout));
        screen.addView(button("Data retention / Storage budget",()->open(StorageHealthActivity.class)));
        setupNotifications();
    }
    @Override protected void onResume(){super.onResume();getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).edit().putBoolean("adminPersonalMode",false).apply();}
    @Override protected void onStart(){super.onStart();if(inbox==null)return;watch();timer.post(stale);}
    private void watch(){
        for(ListenerRegistration l:listeners)l.remove();listeners.clear();
        if(FirebaseAuth.getInstance().getCurrentUser()==null)return;
        FirebaseFirestore db=FirebaseFirestore.getInstance();String uid=FirebaseAuth.getInstance().getCurrentUser().getUid();
        listeners.add(db.collection("notifications").document(uid).collection("items").orderBy("createdAt",Query.Direction.DESCENDING).limit(50).addSnapshotListener((q,e)->{
            if(e!=null){inbox.setText(backendError("Load notifications",e)+"\nOpen Notifications or Refresh to retry.");return;}int count=0;if(q!=null)for(DocumentSnapshot d:q)if(!Boolean.TRUE.equals(d.get("read")))count++;inbox.setText("Unread among latest 50 alerts: "+count+(q!=null&&q.getMetadata().isFromCache()?" (saved offline data)":""));
        }));
        listeners.add(db.collection("leaveRequests").whereEqualTo("status","pending").addSnapshotListener((q,e)->{if(e!=null){pending.setText(backendError("Load pending leave",e));return;}pending.setText("Waiting for Chairman Sir's approval: "+(q==null?0:q.size())+(q!=null&&q.getMetadata().isFromCache()?" (saved offline data)":""));}));
        listeners.add(db.collection("employees").whereEqualTo("active",true).addSnapshotListener((q,e)->{if(e!=null){employeeError=backendError("Load employees for location alerts",e);renderOutside();return;}employeeError="";employees.clear();if(q!=null)for(DocumentSnapshot d:q)employees.put(d.getId(),d);renderOutside();}));
        listeners.add(db.collection("latestLocations").addSnapshotListener((q,e)->{if(e!=null){locationError=backendError("Load location exceptions",e);renderOutside();return;}locationError="";locations.clear();if(q!=null)for(DocumentSnapshot d:q)locations.put(d.getId(),d);renderOutside();}));
    }
    private void renderOutside(){if(outside==null)return;if(!employeeError.isEmpty()||!locationError.isEmpty()){outside.setText(employeeError+"\n"+locationError+"\nTap Refresh dashboard alerts.");return;}int count=0;for(DocumentSnapshot e:employees.values())if("fixed".equals(ShiftNestData.string(e,"workLocationType"))&&locations.containsKey(e.getId())&&ShiftNestData.string(e,"locationId").equals(ShiftNestData.string(locations.get(e.getId()),"locationId"))&&"OUTSIDE LOCATION".equals(ShiftNestData.locationStatus(locations.get(e.getId()))))count++;int blocked=0;for(DocumentSnapshot e:employees.values())if(Boolean.TRUE.equals(e.getBoolean("attendanceBlocked")))blocked++;outside.setText("Attendance blocked after fake GPS: "+blocked+"\nFresh outside-location reports: "+count+"\nOpen Location Tracking for details. All departments with fixed sites are included.");}
    @Override protected void onStop(){for(ListenerRegistration l:listeners)l.remove();listeners.clear();timer.removeCallbacks(stale);super.onStop();}
}
