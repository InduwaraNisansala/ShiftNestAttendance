package com.shiftnest.attendance;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
/** Restore Firebase's persisted admin session; never store a password. */
public final class AdminSession {
    private AdminSession() {}
    public static boolean personal(android.content.Context c){return "admin".equals(c.getSharedPreferences("ShiftNestAttendancePrefs",0).getString("sessionRole",""))&&c.getSharedPreferences("ShiftNestAttendancePrefs",0).getBoolean("adminPersonalMode",false);}
    public static void panel(Activity activity){activity.getSharedPreferences("ShiftNestAttendancePrefs",0).edit().putBoolean("adminPersonalMode",false).apply();Intent i=new Intent(activity,AdminDashboardActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);activity.startActivity(i);activity.finish();}
    public static boolean restore(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("ShiftNestAttendancePrefs", Activity.MODE_PRIVATE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !"admin".equals(prefs.getString("sessionRole", ""))
                || !prefs.getBoolean("adminLoggedIn", false)
                || !user.getUid().equals(prefs.getString("adminUid", user.getUid()))) return false;
        Intent intent = new Intent(activity, AdminDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
