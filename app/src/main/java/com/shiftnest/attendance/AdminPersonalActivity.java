package com.shiftnest.attendance;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.*;

/** Resolve the administrator's server-owned employee link before entering personal screens. */
public class AdminPersonalActivity extends ShiftNestBaseActivity {
    private TextView status;
    private EditText phone;
    private Button link,retry;
    private boolean busy;
    private int generation;
    private final FirebaseFunctions functions=FirebaseFunctions.getInstance("asia-south1");
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;page("My Attendance");
        status=text("Loading my employee profile...",18);screen.addView(status);
        screen.addView(text("Link your admin login to your own active employee record. Add your employee record first if it is missing.",16));
        phone=new EditText(this);phone.setHint("My employee phone number");phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);if(state!=null)phone.setText(state.getString("phone",""));screen.addView(phone);
        link=button("Link my employee record",this::link);screen.addView(link);
        retry=button("Open My Attendance",this::resolve);screen.addView(retry);
        screen.addView(button("Add Employee",()->open(AddEmployeeActivity.class)));
        screen.addView(button("Back to Admin Panel",()->AdminSession.panel(this)));
        resolve();
    }
    private void controls(){link.setEnabled(!busy);retry.setEnabled(!busy);phone.setEnabled(!busy);}
    private void resolve(){
        if(busy)return;if(!requireOnline("Load my employee profile")){status.setText("Connect to the internet and tap Open My Attendance.");return;}busy=true;controls();final int run=++generation;
        functions.getHttpsCallable("employeeProfile").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call().addOnSuccessListener(r->{
            if(run!=generation||isFinishing()||isDestroyed())return;busy=false;controls();
            if(!(r.getData() instanceof Map)){problem("Employee profile unavailable");return;}
            Map<?,?> m=(Map<?,?>)r.getData();Object id=m.get("employeeId");if(!(id instanceof String)||((String)id).isEmpty()){problem("Employee profile unavailable");return;}
            String previous=getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("employeeId","");if(!previous.isEmpty()&&!previous.equals(id))stopService(new Intent(this,LocationTrackingService.class));
            getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).edit().putString("employeeId",(String)id).putString("employeeName",String.valueOf(m.get("employeeName"))).putString("employeeRole",String.valueOf(m.get("employeeRole"))).putString("phone",String.valueOf(m.get("phone"))).putBoolean("adminPersonalMode",true).apply();
            Intent target=new Intent(this,"leave".equals(getIntent().getStringExtra("personalTarget"))?EmployeeLeaveActivity.class:EmployeeDashboardActivity.class);startActivity(target);finish();
        }).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;busy=false;controls();status.setText(backendError("Load my employee profile",e));});
    }
    private void link(){
        if(busy)return;String value=ShiftNestData.normalizePhone(phone.getText().toString());if(value.isEmpty()){validationError(phone,"Enter a valid Sri Lankan phone number");return;}if(!requireOnline("Link my employee record"))return;
        ShiftNestLanguage.dialog(this).setTitle("Link my employee record").setMessage("Confirm this phone number belongs to your own employee record: "+value).setPositiveButton("Confirm",(d,w)->send(value)).setNegativeButton("Cancel",null).show();
    }
    private void send(String value){if(busy)return;busy=true;controls();hideKeyboard();final int run=++generation;
        functions.getHttpsCallable("linkAdminEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(Collections.singletonMap("phone",value)).addOnSuccessListener(r->{if(run!=generation||isFinishing()||isDestroyed())return;busy=false;controls();resolve();}).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;busy=false;controls();problem(backendError("Link my employee record",e));});
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(phone!=null)state.putString("phone",phone.getText().toString());}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
