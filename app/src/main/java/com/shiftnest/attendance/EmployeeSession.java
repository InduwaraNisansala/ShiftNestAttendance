package com.shiftnest.attendance;
import android.content.Intent;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Map;
public final class EmployeeSession {
    private EmployeeSession(){}
    public static void complete(ShiftNestBaseActivity activity){complete(activity,()->{});}
    public static void complete(ShiftNestBaseActivity activity,Runnable failed){
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("employeeProfile").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call().addOnSuccessListener(r->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            if(!(r.getData() instanceof Map)){FirebaseAuth.getInstance().signOut();activity.toast("Employee profile unavailable");failed.run();return;}
            Map<?,?>m=(Map<?,?>)r.getData();save(activity,m);
            Intent i=new Intent(activity,EmployeeDashboardActivity.class);i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity.startActivity(i);activity.finish();
        }).addOnFailureListener(e->{if(activity.isFinishing()||activity.isDestroyed())return;FirebaseAuth.getInstance().signOut();activity.problem(activity.backendError("Load employee profile",e));failed.run();});
    }
    public static void save(ShiftNestBaseActivity activity,Map<?,?>m){activity.getSharedPreferences("ShiftNestAttendancePrefs",0).edit().clear().putBoolean("loggedIn",true).putString("sessionRole","employee").putString("employeeId",String.valueOf(m.get("employeeId"))).putString("employeeName",String.valueOf(m.get("employeeName"))).putString("employeeRole",String.valueOf(m.get("employeeRole"))).putString("phone",String.valueOf(m.get("phone"))).apply();}
}
