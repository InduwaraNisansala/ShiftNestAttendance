package com.shiftnest.attendance;

import android.location.Location;
import androidx.core.location.LocationCompat;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import java.util.HashMap;
import java.util.Map;

/** Reject Android-marked test provider readings; not a device integrity guarantee. */
public final class ShiftNestLocationGuard {
    private ShiftNestLocationGuard() {}
    public static boolean isMock(Location location) {
        return location != null && LocationCompat.isMock(location);
    }
    public static Task<HttpsCallableResult> report(String source) {
        Map<String,Object> data = new HashMap<>();
        data.put("source", source);
        return ShiftNestRequests.read(FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("reportMockLocation").call(data));
    }
}
