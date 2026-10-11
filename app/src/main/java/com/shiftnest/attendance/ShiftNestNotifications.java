package com.shiftnest.attendance;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.*;

/** Verified-device registration and account-filtered data-message notifications. */
public final class ShiftNestNotifications {
    private ShiftNestNotifications(){}
    private static final String CHANNEL="shiftnest_alerts";
    public static void register(Context context){register(context,(ok,message)->{});}
    public static void register(Context context,java.util.function.BiConsumer<Boolean,String> completed){
        if(BuildConfig.DEMO_BACKEND){completed.accept(true,"Demo uses in-app notifications. Real phone push is disabled.");return;}
        if(FirebaseAuth.getInstance().getCurrentUser()==null){completed.accept(false,"Sign in before registering phone notifications.");return;}
        ShiftNestRequests.read(FirebaseMessaging.getInstance().getToken()).addOnSuccessListener(token->register(context,token,completed))
            .addOnFailureListener(e->completed.accept(false,"Notifications unavailable. Please try again."));
    }
    public static void register(Context context,String token){register(context,token,(ok,message)->{if(!ok)android.util.Log.w("ShiftNestAlerts",message);});}
    private static void register(Context context,String token,java.util.function.BiConsumer<Boolean,String> completed){
        if(BuildConfig.DEMO_BACKEND){completed.accept(true,"Demo uses in-app notifications. Real phone push is disabled.");return;}
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){completed.accept(false,"Sign in before registering phone notifications.");return;}
        final String uid=user.getUid();final Context app=context.getApplicationContext();
        final String role=app.getSharedPreferences("ShiftNestAttendancePrefs",0).getString("sessionRole","");
        if(role.isEmpty()){completed.accept(false,"Login session is not ready. Open your dashboard and retry.");return;}
        final String registration=uid+"|"+role+"|"+token;
        if(registration.equals(app.getSharedPreferences("ShiftNestNotificationState",0).getString("registered",""))){completed.accept(true,"This device is already registered for notifications.");return;}
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("registerDevice").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(Collections.singletonMap("token",token))
            .addOnSuccessListener(r->{FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();if(current!=null&&uid.equals(current.getUid())){app.getSharedPreferences("ShiftNestNotificationState",0).edit().putString("registered",registration).apply();completed.accept(true,"Notification device registered successfully. Enable phone notification permission to receive alerts in the notification tray.");}})
            .addOnFailureListener(e->{String message=context instanceof ShiftNestBaseActivity?((ShiftNestBaseActivity)context).backendError("Register notifications",e):"Notifications unavailable. Please try again.";completed.accept(false,message);});
    }
    public static Class<?> target(String role,String type){
        if(type!=null&&(type.startsWith("work_")||"storage_budget".equals(type)||"admin_schedule".equals(type)))return NotificationsActivity.class;
        if("admin".equals(role))return ("location_outside".equals(type)||"location_mock".equals(type))?AdminLocationTrackingActivity.class:LeaveSheetActivity.class;
        return "admin_schedule".equals(type)?NotificationsActivity.class:EmployeeLeaveActivity.class;
    }
    public static void show(Context context,Map<String,String> data){
        if(BuildConfig.DEMO_BACKEND)return;
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        String role=context.getSharedPreferences("ShiftNestAttendancePrefs",0).getString("sessionRole","");
        if(user==null||!user.getUid().equals(data.get("recipientUid"))||!role.equals(data.get("role")))return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"ShiftNest attendance and leave alerts",NotificationManager.IMPORTANCE_HIGH));
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        String id=data.get("notificationId");if(id==null)return;
        Intent intent;
        if("admin".equals(role)&&"self".equals(data.get("scope")))intent=new Intent(context,AdminPersonalActivity.class).putExtra("personalTarget","leave");
        else intent=new Intent(context,target(role,data.get("type")));intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title=ShiftNestLanguage.text(context,data.get("title")),body=ShiftNestLanguage.notification(context,data.get("type"),data.get("body"));
        NotificationCompat.Builder notification=new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.shiftnest_ic_bell).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true);
        String tint=data.get("color");if("red".equals(tint)||"green".equals(tint))notification.setColor(android.graphics.Color.parseColor("red".equals(tint)?"#9B1C1C":"#176B39"));
        manager.notify(id.hashCode(),notification.build());
    }
    public static void clear(Context context){
        context.getSharedPreferences("ShiftNestNotificationState",0).edit().clear().apply();
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancelAll();
        // Data-only pushes check the current Firebase account before displaying, including after logout.
    }
}
