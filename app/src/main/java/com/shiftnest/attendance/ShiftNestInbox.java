package com.shiftnest.attendance;
import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.function.Consumer;

/** Merge private account alerts with the durable employee-ID announcement inbox. */
public final class ShiftNestInbox {
    private ShiftNestInbox() {}
    public static final class Subscription {
        private boolean closed;
        private final List<ListenerRegistration> listeners=new ArrayList<>();
        public void remove(){closed=true;for(ListenerRegistration l:listeners)l.remove();listeners.clear();}
    }
    public static Subscription watch(Context c,int limit,Consumer<List<DocumentSnapshot>> result,Consumer<Exception> failure){
        Subscription sub=new Subscription();
        if(FirebaseAuth.getInstance().getCurrentUser()==null)return sub;
        String uid=FirebaseAuth.getInstance().getCurrentUser().getUid();FirebaseFirestore db=FirebaseFirestore.getInstance();
        List<CollectionReference> sources=new ArrayList<>();sources.add(db.collection("notifications").document(uid).collection("items"));
        String employeeId=c.getSharedPreferences("ShiftNestAttendancePrefs",0).getString("employeeId","");
        if(!employeeId.isEmpty())sources.add(db.collection("employeeNotices").document(employeeId).collection("items"));
        Map<Integer,List<DocumentSnapshot>> parts=new HashMap<>();
        for(int i=0;i<sources.size();i++){final int index=i;
            sub.listeners.add(sources.get(i).orderBy("createdAt",Query.Direction.DESCENDING).limit(limit).addSnapshotListener((q,e)->{
                if(sub.closed||FirebaseAuth.getInstance().getCurrentUser()==null||!uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid()))return;
                if(e!=null){failure.accept(e);return;}
                parts.put(index,q==null?Collections.emptyList():q.getDocuments());if(parts.size()<sources.size())return;
                List<DocumentSnapshot> combined=new ArrayList<>();for(List<DocumentSnapshot> list:parts.values())combined.addAll(list);
                combined.sort((a,b)->{Date x=ShiftNestData.timestamp(a,"createdAt"),y=ShiftNestData.timestamp(b,"createdAt");return Long.compare(y==null?0:y.getTime(),x==null?0:x.getTime());});result.accept(combined);
            }));
        }
        return sub;
    }
}
