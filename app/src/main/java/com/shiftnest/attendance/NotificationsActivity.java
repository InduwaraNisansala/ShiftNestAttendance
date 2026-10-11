package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

/** Durable alert inbox: still available if push permission/delivery is unavailable. */
public class NotificationsActivity extends ShiftNestBaseActivity {
    private TextView summary;
    private LinearLayout list;
    private ShiftNestInbox.Subscription changes;
    private String uid;
    private int limit=50;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;
        if(FirebaseAuth.getInstance().getCurrentUser()==null){logout();return;}
        uid=FirebaseAuth.getInstance().getCurrentUser().getUid();page("Notifications");
        summary=text("Loading notifications...",17);screen.addView(summary);
        screen.addView(button("Refresh notifications",this::watch));
        screen.addView(button("Phone notification settings",this::notificationSettings));
        screen.addView(button("Load older notifications",()->{limit+=50;watch();}));
        list=new LinearLayout(this);list.setOrientation(1);screen.addView(list);
    }
    @Override protected void onStart(){super.onStart();if(uid!=null)watch();}
    private void watch(){
        if(changes!=null)changes.remove();summary.setText("Loading notifications...");
        changes=ShiftNestInbox.watch(this,limit,docs->{
            if(isFinishing()||isDestroyed())return;
            list.removeAllViews();int unread=0;
            for(DocumentSnapshot d:docs){boolean read=Boolean.TRUE.equals(d.get("read"));if(!read)unread++;Date time=ShiftNestData.timestamp(d,"createdAt");
                Button alert=button((read?"":ShiftNestLanguage.text(this,"NEW • "))+ShiftNestLanguage.text(this,ShiftNestData.string(d,"title"))+"\n"+ShiftNestLanguage.notification(this,ShiftNestData.string(d,"type"),ShiftNestData.string(d,"body"))+"\n"+(time==null?"":ShiftNestData.format(time,"yyyy-MM-dd HH:mm")),()->openAlert(d));
                String tint=ShiftNestData.string(d,"color");if("red".equals(tint)||"green".equals(tint)){alert.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("red".equals(tint)?"#9B1C1C":"#176B39")));alert.setTextColor(android.graphics.Color.WHITE);}
                alert.setTag("shiftnest_data");alert.setAllCaps(false);alert.setTextSize(15);list.addView(alert);
            }
            summary.setText("Recent notifications: "+docs.size()+" • Unread in this list: "+unread);
            if(docs.isEmpty())list.addView(text("No notifications yet.",16));
        },e->{if(!isFinishing()&&!isDestroyed())summary.setText(backendError("Load notifications",e));});
    }
    private void openAlert(DocumentSnapshot d){
        if(networkAvailable())ShiftNestRequests.write(d.getReference().update("read",true)).addOnFailureListener(e->problem(backendError("Mark notification read",e)));
        if(ShiftNestData.string(d,"type").startsWith("work_")||"storage_budget".equals(ShiftNestData.string(d,"type"))||"admin_schedule".equals(ShiftNestData.string(d,"type"))){ShiftNestLanguage.dialog(this).setTitle(ShiftNestData.string(d,"title")).setMessage(ShiftNestLanguage.notification(this,"admin_schedule",ShiftNestData.string(d,"body"))).setPositiveButton("OK",null).show();return;}
        if("self".equals(ShiftNestData.string(d,"scope"))&&"admin".equals(getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("sessionRole",""))){startActivity(new android.content.Intent(this,AdminPersonalActivity.class).putExtra("personalTarget","leave"));return;}
        open(ShiftNestNotifications.target(getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("sessionRole","employee"),ShiftNestData.string(d,"type")));
    }
    @Override protected void onStop(){if(changes!=null){changes.remove();changes=null;}super.onStop();}
}
