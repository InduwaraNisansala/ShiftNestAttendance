import java.util.*;
public class SecureClientCheck {
 static class BuildConfig {static boolean DEMO_BACKEND;}
 static class Location {double getLatitude(){return 6.9;}double getLongitude(){return 79.8;}boolean hasAccuracy(){return true;}float getAccuracy(){return 5;}long getTime(){return 123;}boolean mock;}
 static class LocationGuard {static boolean isMock(Location l){return l.mock;}}
 static class Task<T>{} static class HttpsCallableResult{} static class HttpsCallableOptions {boolean limited;static class Builder {boolean limited;Builder setLimitedUseAppCheckTokens(boolean b){limited=b;return this;}HttpsCallableOptions build(){HttpsCallableOptions o=new HttpsCallableOptions();o.limited=limited;return o;}}}
 static class HttpsCallableReference {HttpsCallableReference withTimeout(int n,java.util.concurrent.TimeUnit u){if(n!=30)throw new AssertionError();return this;}Task<HttpsCallableResult> call(Map<String,Object>d){return new Task<>();}}
 static class FirebaseFunctions {static boolean limited;static String region;static FirebaseFunctions getInstance(String r){region=r;return new FirebaseFunctions();}HttpsCallableReference getHttpsCallable(String n){limited=false;return new HttpsCallableReference();}HttpsCallableReference getHttpsCallable(String n,HttpsCallableOptions o){limited=o.limited;return new HttpsCallableReference();}}
    public static Map<String,Object> gps(Location fix){
        Map<String,Object> v=new HashMap<>();v.put("latitude",fix.getLatitude());v.put("longitude",fix.getLongitude());
        v.put("accuracyMeters",fix.hasAccuracy()?fix.getAccuracy():Float.MAX_VALUE);v.put("fixTime",fix.getTime());v.put("mock",LocationGuard.isMock(fix));return v;
    }
    public static Task<HttpsCallableResult> call(String name,Map<String,Object> data){
        FirebaseFunctions f=FirebaseFunctions.getInstance("asia-south1");
        HttpsCallableReference call=BuildConfig.DEMO_BACKEND?f.getHttpsCallable(name):f.getHttpsCallable(name,new HttpsCallableOptions.Builder().setLimitedUseAppCheckTokens(true).build());
        return call.withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data);
    }
 public static void main(String[]a){
 BuildConfig.DEMO_BACKEND=false;call("recordAttendance",new HashMap<>());if(!FirebaseFunctions.limited||!"asia-south1".equals(FirebaseFunctions.region))throw new AssertionError("production protection missing");
 BuildConfig.DEMO_BACKEND=true;call("recordAttendance",new HashMap<>());if(FirebaseFunctions.limited)throw new AssertionError("demo integrity token requested");
 Location fix=new Location();Map<String,Object>g=gps(fix);if(!g.keySet().equals(new HashSet<>(Arrays.asList("latitude","longitude","accuracyMeters","fixTime","mock")))||!Boolean.FALSE.equals(g.get("mock")))throw new AssertionError("GPS payload");fix.mock=true;if(!Boolean.TRUE.equals(gps(fix).get("mock")))throw new AssertionError("mock flag lost");
 System.out.println("PASS: actual client methods request limited-use tokens in production, isolate demo and retain GPS quality/mock fields.");
 }
}
