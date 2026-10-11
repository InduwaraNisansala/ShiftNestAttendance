package com.shiftnest.attendance;
import android.location.Location;
import java.util.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.*;
/** Production sensitive calls acquire limited-use App Check tokens. Demo never reaches live services. */
public final class ShiftNestSecureRequests {
    private ShiftNestSecureRequests(){}
    public static Map<String,Object> gps(Location fix){
        Map<String,Object> v=new HashMap<>();v.put("latitude",fix.getLatitude());v.put("longitude",fix.getLongitude());
        v.put("accuracyMeters",fix.hasAccuracy()?fix.getAccuracy():Float.MAX_VALUE);v.put("fixTime",fix.getTime());v.put("mock",ShiftNestLocationGuard.isMock(fix));return v;
    }
    public static Task<HttpsCallableResult> call(String name,Map<String,Object> data){
        FirebaseFunctions f=FirebaseFunctions.getInstance("asia-south1");
        HttpsCallableReference call=BuildConfig.DEMO_BACKEND?f.getHttpsCallable(name):f.getHttpsCallable(name,new HttpsCallableOptions.Builder().setLimitedUseAppCheckTokens(true).build());
        return call.withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data);
    }
}
