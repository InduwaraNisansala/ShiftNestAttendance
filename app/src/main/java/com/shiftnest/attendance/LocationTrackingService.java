package com.shiftnest.attendance;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LocationTrackingService extends Service {

    private static final String CHANNEL_ID =
            "ShiftNestAttendanceLocationChannel";

    private static final int NOTIFICATION_ID = 1001;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;

    private LocationCallback locationCallback;
    private long nextMockReportAt;
    private boolean mockReportPending, mockNoticeShown,savePending;

    private com.google.firebase.firestore.ListenerRegistration employeeChanges;
    private String employeeId = "";
    private String locationId = "";

    @Override
    public void onCreate() {
        super.onCreate();
        if(BuildConfig.DEMO_BACKEND&&!ShiftNestApplication.demoReady()){stopSelf();return;}

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        db =
                FirebaseFirestore.getInstance();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {
        if(BuildConfig.DEMO_BACKEND&&!ShiftNestApplication.demoReady()){stopSelf();return START_NOT_STICKY;}


        if (intent != null) {

            String receivedEmployeeId =
                    intent.getStringExtra("employeeId");

            String receivedLocationId =
                    intent.getStringExtra("locationId");

            if (receivedEmployeeId != null) {
                employeeId = receivedEmployeeId;
            }

            if (receivedLocationId != null) {
                locationId = receivedLocationId;
            }
        }

        if(employeeChanges!=null)employeeChanges.remove();
        if(!employeeId.isEmpty())employeeChanges=db.collection("employees").document(employeeId).addSnapshotListener((d,e)->{
            if(e!=null){if(e instanceof com.google.firebase.firestore.FirebaseFirestoreException && ((com.google.firebase.firestore.FirebaseFirestoreException)e).getCode()==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)stopSelf();return;}
            if(d==null || !d.exists() || !Boolean.TRUE.equals(d.get("active"))){stopSelf();return;}
            locationId="fixed".equals(d.getString("workLocationType"))?ShiftNestData.string(d,"locationId"):"";
        });

        try {
            startForeground(NOTIFICATION_ID,createNotification());
            startLocationTracking();
        } catch(RuntimeException error){
            android.util.Log.e("ShiftNestTracking","Tracking could not start",error);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private Notification createNotification() {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "ShiftNest Attendance"
                )
                .setContentText(
                        ShiftNestLanguage.text(this,"Work location tracking is active")
                )
                .setSmallIcon(
                        android.R.drawable.ic_menu_mylocation
                )
                .setOngoing(true)
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Location Tracking",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Shows when employee location tracking is active."
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    private void startLocationTracking() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

                &&

                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            stopSelf();
            return;
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        5 * 60 * 1000
                )
                        .setMinUpdateIntervalMillis(
                                2 * 60 * 1000
                        )
                        .setWaitForAccurateLocation(false)
                        .build();

        if(locationCallback!=null)fusedLocationClient.removeLocationUpdates(locationCallback);
        locationCallback =
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            LocationResult locationResult) {

                        if (locationResult == null) {
                            return;
                        }

                        Location location =
                                locationResult.getLastLocation();

                        if (location == null) {
                            return;
                        }

                        if (ShiftNestLocationGuard.isMock(location)) {
                            if (!mockNoticeShown) {
                                android.widget.Toast.makeText(LocationTrackingService.this,
                                        "Fake location detected. GPS tracking readings blocked. Turn off mock location.", android.widget.Toast.LENGTH_LONG).show();
                                mockNoticeShown = true;
                            }
                            long now = android.os.SystemClock.elapsedRealtime();
                            if (!mockReportPending && now >= nextMockReportAt) {
                                mockReportPending = true;
                                ShiftNestLocationGuard.report("tracking")
                                        .addOnSuccessListener(result -> {
                                            mockReportPending = false;
                                            nextMockReportAt = android.os.SystemClock.elapsedRealtime() + 300000;
                                        })
                                        .addOnFailureListener(error -> {
                                            mockReportPending = false;
                                            nextMockReportAt = android.os.SystemClock.elapsedRealtime() + 60000;
                                            android.util.Log.w("ShiftNestMockLocation", "Admin alert could not be confirmed", error);
                                        });
                            }
                            return;
                        }
                        mockNoticeShown = false;
                        saveLocation(
                                location.getLatitude(),
                                location.getLongitude(), location.getTime(), location.hasAccuracy()?location.getAccuracy():Float.MAX_VALUE
                        );
                    }
                };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
    }

    private void saveLocation(double latitude,double longitude,long fixTime,float accuracy){
        if(savePending||employeeId.isEmpty())return;
        Map<String,Object> v=new HashMap<>();v.put("latitude",latitude);v.put("longitude",longitude);v.put("fixTime",fixTime);v.put("accuracyMeters",accuracy);v.put("mock",false);
        v.put("action","tracking");v.put("expectedDate",ShiftNestData.date());v.put("requestId",java.util.UUID.randomUUID().toString().replace("-",""));
        savePending=true;ShiftNestSecureRequests.call("recordAttendance",v).addOnSuccessListener(r->savePending=false).addOnFailureListener(e->{
            savePending=false;android.util.Log.w("ShiftNestTracking","Verified location save failed",e);
            if(e instanceof com.google.firebase.functions.FirebaseFunctionsException){com.google.firebase.functions.FirebaseFunctionsException.Code code=((com.google.firebase.functions.FirebaseFunctionsException)e).getCode();if(code==com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION||code==com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED||code==com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED)stopSelf();}
        });
    }

    @Override
    public void onDestroy() {
        if(employeeChanges!=null)employeeChanges.remove();

        if (fusedLocationClient != null
                && locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}