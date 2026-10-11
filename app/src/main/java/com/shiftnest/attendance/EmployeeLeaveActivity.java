package com.shiftnest.attendance;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.*;
import java.util.*;
import java.time.*;

public class EmployeeLeaveActivity extends ShiftNestBaseActivity {
    private final FirebaseFunctions functions=FirebaseFunctions.getInstance("asia-south1");
    private TextView balance,status;
    private EditText reason;
    private Button apply,date,refreshButton;
    private LinearLayout history;
    private String selected=ShiftNestData.date();
    private Map<?,?> overview;
    private boolean busy,loading,started;
    private ListenerRegistration changes;
    private int generation;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);page("My Leave");if(AdminSession.personal(this))screen.addView(button("Back to Admin Panel",()->AdminSession.panel(this)));
        if(state!=null)selected=state.getString("selected",selected);
        balance=text("Loading leave balance...",18);screen.addView(balance);
        date=button("Leave date: "+selected,()->chooseDate("employee_leave","Select leave date",LocalDate.parse(selected),this::dateChanged));screen.addView(date);
        status=text("Checking leave eligibility...",16);screen.addView(status);
        screen.addView(text("Leave reason (required, up to 500 characters)",16));
        reason=new EditText(this);reason.setHint("Leave reason");
        reason.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(2);reason.setMaxLines(5);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        if(state!=null)reason.setText(state.getString("reason",""));screen.addView(reason);
        apply=button("Apply Leave",this::submit);screen.addView(apply);
        refreshButton=button("Refresh balance / approval status",this::refresh);screen.addView(refreshButton);
        screen.addView(text("My leave requests",22));history=new LinearLayout(this);history.setOrientation(1);screen.addView(history);
        bindDateChoice("employee_leave",this::dateChanged);buttons();
    }
    private void dateChanged(LocalDate day){selected=day.toString();date.setText("Leave date: "+selected);status.setText("Checking "+selected+"...");overview=null;refresh();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("selected",selected);state.putString("reason",reason.getText().toString());}
    @Override protected void onStart(){
        super.onStart();started=true;
        String id=getSharedPreferences("ShiftNestAttendancePrefs",0).getString("employeeId","");
        if(id.isEmpty()){logout();return;}
        refresh();
        final boolean[] initial={true};
        changes=FirebaseFirestore.getInstance().collection("leaveRequests").whereEqualTo("employeeId",id).addSnapshotListener((q,e)->{
            if(!started||isFinishing()||isDestroyed())return;
            if(e!=null){status.setText(backendError("Update leave status",e)+"\nTap Refresh to retry.");return;}
            if(initial[0]){initial[0]=false;return;}
            if(!busy&&!loading)refresh();
        });
    }
    @Override protected void onStop(){started=false;generation++;loading=false;if(changes!=null){changes.remove();changes=null;}super.onStop();}
    private long number(String key){Object n=overview==null?null:overview.get(key);return n instanceof Number?((Number)n).longValue():0;}
    private void buttons(){
        if(apply==null)return;
        apply.setEnabled(!busy&&!loading&&overview!=null&&blockedReason().isEmpty());
        apply.setText(busy?"Submitting leave...":loading?"Checking leave eligibility...":"Apply Leave");
        date.setEnabled(!busy);reason.setEnabled(!busy);refreshButton.setEnabled(!busy&&!loading);
        refreshButton.setText(loading?"Loading balance / approval status...":"Refresh balance / approval status");
    }
    private void refresh(){
        if(busy)return;
        final int request=++generation;loading=true;overview=null;
        balance.setText("Loading leave balance...");status.setText("Checking eligibility for "+selected+"...");buttons();
        if(!networkAvailable()){loading=false;balance.setText("Leave balance unavailable while offline.");status.setText("Connect to mobile data/Wi-Fi, then tap Refresh. Your reason and selected date are kept.");buttons();return;}
        functions.getHttpsCallable("leaveOverview").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(Collections.singletonMap("date",selected))
            .addOnSuccessListener(r->{
                if(request!=generation||!started||isFinishing()||isDestroyed())return;loading=false;
                if(!(r.getData() instanceof Map)){balance.setText("Leave balance unavailable");status.setText("The server returned an invalid leave response. Tap Refresh to retry.");buttons();return;}
                overview=(Map<?,?>)r.getData();boolean sales=Boolean.TRUE.equals(overview.get("sales"));
                balance.setText(sales?"Sales leave: 1 day per 10 completed work days\nCompleted work days: "+number("worked")+"\nEarned: "+number("earned")+" • Approved: "+number("used")+"\nPending: "+number("pending")+" • Available: "+number("remaining"):
                    Boolean.TRUE.equals(overview.get("configured"))?"Annual leave ("+overview.get("year")+"): "+overview.get("quota")+"\nTaken: "+number("used")+" • Pending: "+number("pending")+"\nAvailable: "+number("remaining")+"\nCasual / Sick leave: #":
                    "Annual quota ("+overview.get("year")+"): #\nTaken: # • Remaining: #\nCasual / Sick leave: #\nAsk admin to configure this year’s allowance.");
                renderHistory();String why=blockedReason();status.setText(selected+"\n"+(why.isEmpty()?"Available. Enter a reason and submit for approval.":why));buttons();
            }).addOnFailureListener(e->{
                if(request!=generation||!started||isFinishing()||isDestroyed())return;
                loading=false;overview=null;balance.setText("Leave balance unavailable");
                status.setText(backendError("Load leave balance",e)+"\n\nTap Refresh balance / approval status to retry.");
                history.removeAllViews();history.addView(text("Leave history could not be refreshed. Existing requests have not been deleted.",16));buttons();
            });
    }
    private void renderHistory(){
        history.removeAllViews();Object raw=overview.get("requests");List<Map<?,?>> list=new ArrayList<>();
        if(raw instanceof List)for(Object item:(List<?>)raw)if(item instanceof Map)list.add((Map<?,?>)item);
        list.sort((a,b)->String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
        for(Map<?,?> m:list){String st=String.valueOf(m.get("status"));
            String message="approved".equals(st)?"Your leave has been approved by Chairman Sir.":"rejected".equals(st)?"Your leave has been rejected by Chairman Sir.":"pending".equals(st)?"Waiting for Chairman Sir's approval.":"Status: "+st;
            TextView entry=text(m.get("date")+"\n"+ShiftNestLanguage.text(this,message)+"\n"+ShiftNestLanguage.text(this,"Reason: ")+m.get("reason")+(m.get("decisionNote")==null?"":"\n"+ShiftNestLanguage.text(this,"Decision note: ")+m.get("decisionNote")),16);
            entry.setTag("shiftnest_data");history.addView(entry);
        }
        if(list.isEmpty())history.addView(text("No leave requests yet.",16));
    }
    private String blockedReason(){
        if(overview==null)return "Refresh leave balance before applying.";
        String today=String.valueOf(overview.get("currentDate"));Map<?,?> policy=overview.get("policy") instanceof Map?(Map<?,?>)overview.get("policy"):Collections.emptyMap();Object booked=overview.get("bookedDates");
        if(!Boolean.TRUE.equals(overview.get("configured")))return "Office / Hotel leave quota is awaiting company confirmation. Ask admin to enter the allowance for the selected year.";
        if(selected.compareTo(today)<0)return "Past dates cannot be booked.";
        if(booked instanceof List&&((List<?>)booked).contains(selected))return "This day is already booked. Select another date.";
        boolean sales=Boolean.TRUE.equals(overview.get("sales"));
        if(sales&&(policy.get("holidayCoverageStart")==null||policy.get("holidayCoverageEnd")==null||selected.compareTo(String.valueOf(policy.get("holidayCoverageStart")))<0||selected.compareTo(String.valueOf(policy.get("holidayCoverageEnd")))>0))return "Company holiday calendar is not confirmed for this date. Ask admin to confirm calendar coverage.";
        int weekday=LocalDate.parse(selected).getDayOfWeek().getValue()%7;Object blocked=policy.get("blockedWeekdays");
        if(sales&&blocked instanceof List)for(Object day:(List<?>)blocked)if(day instanceof Number&&((Number)day).intValue()==weekday)return "Leave cannot be taken on a company holiday. Select another date.";
        Object holidays=overview.get("holidays");if(sales&&holidays instanceof List)for(Object h:(List<?>)holidays)if(h instanceof Map){Map<?,?> m=(Map<?,?>)h;if(selected.equals(m.get("date"))&&!Boolean.FALSE.equals(m.get("blocked")))return "Holiday: "+m.get("name")+". Leave is not available.";}
        if(number("remaining")<1)return Boolean.TRUE.equals(overview.get("sales"))?"Complete "+number("nextWorkDays")+" more work day(s) to earn your next leave.":"No annual leave balance available.";
        return "";
    }
    private void submit(){
        if(busy||loading)return;
        String why=blockedReason();if(!why.isEmpty()){problem(why);return;}
        String note=reason.getText().toString().trim();if(note.isEmpty()){validationError(reason,"Enter a leave reason");return;}
        if(!requireOnline("Apply leave"))return;
        busy=true;hideKeyboard();buttons();Map<String,Object> data=new HashMap<>();data.put("date",selected);data.put("reason",note);
        functions.getHttpsCallable("applyLeave").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data)
            .addOnSuccessListener(r->{if(isFinishing()||isDestroyed())return;busy=false;reason.setText("");success("Leave request submitted successfully. Waiting for Chairman Sir's approval.");if(started)refresh();else buttons();})
            .addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;busy=false;buttons();problem(backendError("Apply leave",e)+"\nRefresh approval status before submitting again.");if(started)refresh();});
    }
}
