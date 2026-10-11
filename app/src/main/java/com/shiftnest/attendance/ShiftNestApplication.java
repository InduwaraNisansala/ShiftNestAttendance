package com.shiftnest.attendance;
import android.app.Application;
import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.functions.FirebaseFunctions;

/** Demo config is applied before any screen/service uses its Firebase SDKs. No live fallback. */
public class ShiftNestApplication extends Application {
    private static boolean ready;
    private static String connectedHost="";
    @Override public void onCreate(){
        super.onCreate();
        if(!BuildConfig.DEMO_BACKEND){
            FirebaseApp.initializeApp(this);
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance());
        }
        if(BuildConfig.DEMO_BACKEND){
            if(!BuildConfig.DEBUG)throw new IllegalStateException("Demo must be a debug build");
            String host=getSharedPreferences("ShiftNestDemoConnection",0).getString("host","");
            if(ShiftNestDemoHost.valid(host))connectDemo(this,host);
        }
    }
    public static boolean demoReady(){return !BuildConfig.DEMO_BACKEND||ready;}
    public static String demoHost(){return connectedHost;}
    public static synchronized void connectDemo(Context context,String host){
        if(!BuildConfig.DEMO_BACKEND||!BuildConfig.DEBUG||!ShiftNestDemoHost.valid(host))throw new IllegalArgumentException("Invalid demo connection");
        if(ready){if(!host.equals(connectedHost))throw new IllegalStateException("Force stop and reopen the demo to change PC address");return;}
        if(!"demo-shiftnest-attendance".equals(FirebaseApp.getInstance().getOptions().getProjectId()))throw new IllegalStateException("Demo project configuration is missing");
        FirebaseAuth.getInstance().useEmulator(host,9099);
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        db.useEmulator(host,8180);
        FirebaseFunctions.getInstance("asia-south1").useEmulator(host,5001);
        connectedHost=host;ready=true;
    }
}
