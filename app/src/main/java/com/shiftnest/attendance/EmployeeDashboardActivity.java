package com.shiftnest.attendance;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmployeeDashboardActivity extends ShiftNestBaseActivity {

    private TextView tvEmployeeName;
    private TextView tvEmployeeRole;
    private TextView tvCheckIn;
    private TextView tvCheckOut;

    private Button btnCheckIn;
    private Button btnCheckOut;
    private Button btnAttendanceHistory;
    private Button btnLogout;
    private com.google.firebase.firestore.ListenerRegistration employeeChanges,leaveChanges;
    private int leaveGeneration,attendanceGeneration;
    private TextView attendanceStatus;
    private Button inboxHighlight;
    private ShiftNestInbox.Subscription inboxChanges;
    private TextView leaveBalance;
    private Button leaveApply,outingNotice,lateNotice;
    private String attendanceSite="",attendancePlace="";
    private boolean placeDialogOpen;
    private android.location.Location attendanceFix;

    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    private String employeeId;
    private String employeeName;
    private String employeeRole;
    private String phone;

    private boolean isCheckingIn = false;
    private boolean isCheckingOut = false;
    private boolean attendanceLoaded;
    private boolean checkedInToday;
    private boolean checkedOutToday;
    private String attendanceDay = "";

    private static final String PREF_NAME =
            "ShiftNestAttendancePrefs";

    private final ActivityResultLauncher<String[]>
            locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {

                        Boolean fine =
                                result.get(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                );

                        Boolean coarse =
                                result.get(
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                );

                        if (Boolean.TRUE.equals(fine)
                                || Boolean.TRUE.equals(coarse)) {

                            getCurrentLocationAndCheckIn();

                        } else {

                            problem("Location permission is required.");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null){attendanceSite=savedInstanceState.getString("attendanceSite","");attendancePlace=savedInstanceState.getString("attendancePlace","");}

        setContentView(
                R.layout.activity_employee_dashboard
        );

        if (AdminSession.personal(this)) ((android.widget.TextView)findViewById(R.id.tvDashboardTitle)).setText("My Attendance");

        tvEmployeeName =
                findViewById(R.id.tvEmployeeName);

        tvEmployeeRole =
                findViewById(R.id.tvEmployeeRole);

        tvCheckIn =
                findViewById(R.id.tvCheckIn);

        tvCheckOut =
                findViewById(R.id.tvCheckOut);

        btnCheckIn =
                findViewById(R.id.btnCheckIn);

        btnCheckOut =
                findViewById(R.id.btnCheckOut);

        btnAttendanceHistory =
                findViewById(R.id.btnAttendanceHistory);

        btnLogout =
                findViewById(R.id.btnLogout);

        db =
                FirebaseFirestore.getInstance();

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        SharedPreferences prefs =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        employeeId =
                prefs.getString(
                        "employeeId",
                        ""
                );

        employeeName =
                prefs.getString(
                        "employeeName",
                        ""
                );

        employeeRole =
                prefs.getString(
                        "employeeRole",
                        ""
                );

        phone =
                prefs.getString(
                        "phone",
                        ""
                );

        if (employeeName != null
                && !employeeName.isEmpty()) {

            tvEmployeeName.setText(
                    "Welcome, " + employeeName
            );
        }

        if (employeeRole != null
                && !employeeRole.isEmpty()) {

            tvEmployeeRole.setText(
                    "Role: " + employeeRole
            );
        }

        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null || employeeId == null || employeeId.isEmpty()) { logout(); return; }
        android.view.ViewGroup parent = (android.view.ViewGroup) btnLogout.getParent();
        inboxHighlight=button("Loading notifications...",()->open(NotificationsActivity.class));
        inboxHighlight.setAllCaps(false);inboxHighlight.setTextSize(16);inboxHighlight.setMaxLines(8);inboxHighlight.setEllipsize(android.text.TextUtils.TruncateAt.END);inboxHighlight.setTag("shiftnest_data");
        parent.addView(inboxHighlight,0);
        android.widget.Button settings = button("Settings", () -> open(SettingsActivity.class));
        leaveApply = button("Apply Leave", () -> open(EmployeeLeaveActivity.class));
        leaveApply.setEnabled(false);
        android.widget.Button leave = button("My leave details / approval status", () -> open(EmployeeLeaveActivity.class));
        leaveBalance = text("Leave balance loading…", 16);
        attendanceStatus=text("Loading today’s attendance...",16);
        parent.addView(attendanceStatus,parent.indexOfChild(btnLogout));
        parent.addView(button("Refresh attendance / leave balance",()->{if(isCheckingIn||isCheckingOut){problem("Wait for the attendance request to finish before refreshing.");return;}loadTodayAttendance();refreshLeaveBalance();}),parent.indexOfChild(btnLogout));
        parent.addView(leaveBalance, parent.indexOfChild(btnLogout));
        parent.addView(leaveApply, parent.indexOfChild(btnLogout));
        parent.addView(leave, parent.indexOfChild(btnLogout));
        parent.addView(settings, parent.indexOfChild(btnLogout));
        parent.addView(button("Notifications",()->open(NotificationsActivity.class)),parent.indexOfChild(btnLogout));setupNotifications();
        lateNotice=button("Running late? Message admin",()->startActivity(new Intent(this,WorkNoticeActivity.class).putExtra("type","late")));
        outingNotice=button("Add Location",()->startActivity(new Intent(this,WorkNoticeActivity.class).putExtra("type","outing")));
        parent.addView(lateNotice,parent.indexOfChild(btnLogout));
        outingNotice.setEnabled(false);
        parent.addView(outingNotice,parent.indexOfChild(btnCheckOut));
        loadTodayAttendance();

        btnCheckIn.setOnClickListener(v -> {

            if (isCheckingIn || isCheckingOut || !attendanceLoaded || checkedInToday || checkedOutToday) {
                return;
            }

            promptAttendancePlace(false);
        });

        btnCheckOut.setOnClickListener(v -> {

            if (isCheckingIn || isCheckingOut || !attendanceLoaded || !checkedInToday || checkedOutToday) {
                return;
            }

            checkOut();
        });

        btnAttendanceHistory.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            EmployeeDashboardActivity.this,
                            AttendanceHistoryActivity.class
                    );

            startActivity(intent);
        });

        if(AdminSession.personal(this)){btnLogout.setText("Back to Admin Panel");btnLogout.setOnClickListener(v->AdminSession.panel(this));}
        else btnLogout.setOnClickListener(v -> logout());
    }

    @Override protected void onStart() {
        super.onStart();
        if(employeeId==null || employeeId.isEmpty() || db==null)return;
        employeeChanges=db.collection("employees").document(employeeId).addSnapshotListener((d,e)->{
            if(isFinishing() || isDestroyed())return;
            if(e!=null){
                if(e instanceof com.google.firebase.firestore.FirebaseFirestoreException && ((com.google.firebase.firestore.FirebaseFirestoreException)e).getCode()==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED){toast("Employee details changed. Log in with your current registered phone number.");logout();}
                return;
            }
            if(d==null || !d.exists() || !Boolean.TRUE.equals(d.get("active"))){toast("Employee account is inactive.");if(AdminSession.personal(this))AdminSession.panel(this);else logout();return;}
            com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if(user==null){logout();return;}
            if(AdminSession.personal(this)){if(!user.getUid().equals(ShiftNestData.string(d,"adminUid"))){problem("Your linked employee record is unavailable.");AdminSession.panel(this);return;}}
            else if(!ShiftNestData.string(d,"phone").equals(user.getPhoneNumber())){logout();return;}
            employeeName=ShiftNestData.string(d,"name");employeeRole=ShiftNestData.string(d,"role");phone=ShiftNestData.string(d,"phone");
            getSharedPreferences(PREF_NAME,MODE_PRIVATE).edit().putString("employeeName",employeeName).putString("employeeRole",employeeRole).putString("phone",phone).apply();
            tvEmployeeName.setText("Welcome, "+employeeName);tvEmployeeRole.setText("Role: "+employeeRole);
        });
        if(inboxChanges!=null)inboxChanges.remove();
        inboxChanges=ShiftNestInbox.watch(this,50,docs->{
            if(isFinishing()||isDestroyed())return;int unread=0;StringBuilder previews=new StringBuilder();
            for(com.google.firebase.firestore.DocumentSnapshot d:docs)if(!Boolean.TRUE.equals(d.get("read"))){unread++;if(unread<=3)previews.append("\n• ").append(ShiftNestLanguage.text(this,ShiftNestData.string(d,"title"))).append("\n").append(ShiftNestLanguage.notification(this,ShiftNestData.string(d,"type"),ShiftNestData.string(d,"body")));}
            String heading=unread>0?ShiftNestLanguage.text(this,"New admin notifications: ")+unread:ShiftNestLanguage.text(this,"Notifications");
            inboxHighlight.setText(heading+previews+"\n"+ShiftNestLanguage.text(this,"Tap to view all notifications."));
            inboxHighlight.setBackgroundResource(unread>0?R.drawable.shiftnest_primary:R.drawable.shiftnest_secondary);
            inboxHighlight.setTextColor(color(unread>0?R.color.shiftnest_on_primary:R.color.shiftnest_secondary));
        },e->{if(!isFinishing()&&!isDestroyed())inboxHighlight.setText(ShiftNestLanguage.text(this,"Notifications unavailable. Please try again.")+"\n"+ShiftNestLanguage.text(this,"Tap to view all notifications."));});
        final boolean[] firstLeaveSnapshot={true};
        leaveChanges=db.collection("leaveRequests").whereEqualTo("employeeId",employeeId).addSnapshotListener((q,e)->{
            if(isFinishing()||isDestroyed())return;
            if(e!=null){leaveBalance.setText(backendError("Update leave balance",e)+"\nTap Refresh attendance / leave balance.");return;}
            if(firstLeaveSnapshot[0]){firstLeaveSnapshot[0]=false;return;}refreshLeaveBalance();
        });
    }
    @Override protected void onStop(){if(inboxChanges!=null){inboxChanges.remove();inboxChanges=null;}if(employeeChanges!=null){employeeChanges.remove();employeeChanges=null;}if(leaveChanges!=null){leaveChanges.remove();leaveChanges=null;}super.onStop();}

    @Override protected void onResume() {
        super.onResume();
        refreshLeaveBalance();
        if (db != null && !isCheckingIn && !isCheckingOut) loadTodayAttendance();
    }
    private void refreshLeaveBalance(){
        if (leaveBalance == null) return;
        final int request=++leaveGeneration;
        leaveBalance.setText("Loading leave balance...");
        if(!networkAvailable()){leaveApply.setEnabled(true);leaveBalance.setText("Leave balance is unavailable while offline. Open My leave details, connect to mobile data/Wi-Fi and tap Refresh.");return;}
        com.google.firebase.functions.FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("leaveOverview").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call()
                .addOnSuccessListener(r -> {
                    if(request!=leaveGeneration||isFinishing()||isDestroyed())return;
                    if (!(r.getData() instanceof java.util.Map)){leaveApply.setEnabled(true);leaveBalance.setText("Invalid leave response. Open My leave details and tap Refresh.");return;}
                    java.util.Map<?, ?> m = (java.util.Map<?, ?>) r.getData();
                    boolean sales = Boolean.TRUE.equals(m.get("sales"));
                    long available = m.get("remaining") instanceof Number ? ((Number)m.get("remaining")).longValue() : 0;
                    leaveApply.setEnabled(Boolean.TRUE.equals(m.get("configured")) && available > 0);
                    leaveBalance.setText(sales ? "Sales leave: 1 day per 10 completed work days\nLeave earned: " + m.get("earned") + " • Approved: " + m.get("used")
                            + "\nPending: " + m.get("pending") + " • Available: " + available
                            + (available < 1 ? "\nComplete " + m.get("nextWorkDays") + " more work day(s) to earn your next leave." : "\nSelect an available, non-holiday date.")
                            : Boolean.TRUE.equals(m.get("configured")) ? "Annual leave (" + m.get("year") + "): " + m.get("quota") + " • Taken: " + m.get("used") + "\nPending: " + m.get("pending") + " • Available: " + available + "\nCasual / Sick leave: #" : "Annual quota: # • Taken: # • Remaining: #\nAsk admin to configure this year’s allowance.");
                }).addOnFailureListener(e -> {if(request!=leaveGeneration||isFinishing()||isDestroyed())return;leaveApply.setEnabled(true);leaveBalance.setText(backendError("Load leave balance",e)+"\nOpen My leave details and tap Refresh."); });
    }

    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

                ||

                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("attendanceSite",attendanceSite);out.putString("attendancePlace",attendancePlace);}
    private void getCurrentLocationAndCheckIn() {
        if(attendanceSite.trim().isEmpty()||attendancePlace.trim().isEmpty()){promptAttendancePlace(false);return;}
        if(isCheckingIn||isCheckingOut||!attendanceLoaded||checkedInToday||checkedOutToday)return;
        if(!ShiftNestData.date().equals(attendanceDay)){loadTodayAttendance();return;}
        if(!requireOnline("Check in"))return;
        if(!locationEnabled()){showLocationProblem();return;}

        if (!hasLocationPermission()) {
            return;
        }

        if (employeeId == null
                || employeeId.isEmpty()) {

            problem("Employee information not found.");

            return;
        }

        attendanceGeneration++;
        isCheckingIn = true;

        updateAttendanceButtons();

        Toast.makeText(
                this,
                "Getting your GPS location...",
                Toast.LENGTH_SHORT
        ).show();

        ShiftNestRequests.read(fusedLocationClient
                .getCurrentLocation(
                        new com.google.android.gms.location.CurrentLocationRequest.Builder().setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(25000).build(),
                        null
                ))
                .addOnSuccessListener(location -> {

                    if (location == null) {

                        resetCheckInButton();

                        showLocationProblem();

                        return;
                    }

                    if (ShiftNestLocationGuard.isMock(location)) {
                        resetCheckInButton();
                        problem("Fake location detected. Check-in blocked. Turn off the mock location app and use real GPS.");
                        ShiftNestLocationGuard.report("check_in")
                                .addOnSuccessListener(result -> toast("Fake location report saved for admin review."))
                                .addOnFailureListener(error -> problem("Check-in remains blocked. Admin alert could not be confirmed. " + backendError("Report fake GPS",error)));
                        return;
                    }

                    attendanceFix=new android.location.Location(location);
                    double latitude =
                            location.getLatitude();

                    double longitude =
                            location.getLongitude();

                    checkEmployeeLocation(
                            latitude,
                            longitude
                    );
                })
                .addOnFailureListener(e -> {

                    resetCheckInButton();

                    problem("Location unavailable. Enable phone Location and try again.");
                });
    }

    private void checkEmployeeLocation(
            double employeeLatitude,
            double employeeLongitude) {

        ShiftNestRequests.read(db.collection("employees")
                .document(employeeId)
                .get())
                .addOnSuccessListener(employeeDocument -> {

                    if (!employeeDocument.exists()) {

                        resetCheckInButton();

                        problem("Employee record not found.");

                        return;
                    }

                    if (Boolean.TRUE.equals(employeeDocument.getBoolean("attendanceBlocked"))) {
                        resetCheckInButton();
                        problem("Attendance is blocked after a fake GPS report. Ask an admin for company approval to unblock it, then use real GPS.");
                        return;
                    }

                    String workLocationType =
                            employeeDocument.getString(
                                    "workLocationType"
                            );

                    String locationId =
                            employeeDocument.getString(
                                    "locationId"
                            );

                    // NO FIXED LOCATION
                    if ("no_fixed".equals(workLocationType)) {

                        saveCheckIn(
                                employeeLatitude,
                                employeeLongitude,
                                0,
                                "No Fixed Location",
                                ""
                        );

                        return;
                    }

                    // FIXED LOCATION
                    if (locationId == null
                            || locationId.isEmpty()) {

                        resetCheckInButton();

                        problem("No work location assigned.");

                        return;
                    }

                    getWorkLocation(
                            locationId,
                            employeeLatitude,
                            employeeLongitude
                    );
                })
                .addOnFailureListener(e -> {

                    resetCheckInButton();

                    problem(backendError("Load employee assignment",e));
                });
    }

    private void getWorkLocation(
            String locationId,
            double employeeLatitude,
            double employeeLongitude) {

        ShiftNestRequests.read(db.collection("locations")
                .document(locationId)
                .get())
                .addOnSuccessListener(locationDocument -> {

                    if (!locationDocument.exists()) {

                        resetCheckInButton();

                        problem("Work location not found.");

                        return;
                    }

                    Double siteLatitude =
                            locationDocument.getDouble(
                                    "latitude"
                            );

                    Double siteLongitude =
                            locationDocument.getDouble(
                                    "longitude"
                            );

                    Double allowedRadius =
                            locationDocument.getDouble(
                                    "allowedRadius"
                            );

                    String locationName =
                            locationDocument.getString(
                                    "name"
                            );

                    if (siteLatitude == null
                            || siteLongitude == null
                            || allowedRadius == null || !Double.isFinite(siteLatitude) || !Double.isFinite(siteLongitude) || !Double.isFinite(allowedRadius) || Math.abs(siteLatitude)>90 || Math.abs(siteLongitude)>180 || allowedRadius<=0) {

                        resetCheckInButton();

                        problem("Work location GPS data is incomplete.");

                        return;
                    }

                    float[] distanceResult =
                            new float[1];

                    Location.distanceBetween(
                            employeeLatitude,
                            employeeLongitude,
                            siteLatitude,
                            siteLongitude,
                            distanceResult
                    );

                    float distance =
                            distanceResult[0];

                    if (distance <= allowedRadius) {

                        saveCheckIn(
                                employeeLatitude,
                                employeeLongitude,
                                distance,
                                locationName,
                                locationId
                        );

                    } else {

                        resetCheckInButton();

                        problem("You are outside the allowed work location.\nDistance: "+Math.round(distance)+" meters\nAllowed: "+Math.round(allowedRadius)+" meters.\nCheck your precise location setting, or contact admin if the assigned site is incorrect.");
                    }
                })
                .addOnFailureListener(e -> {

                    resetCheckInButton();

                    problem(backendError("Load work location",e));
                });
    }

    private void saveCheckIn(double latitude,double longitude,float distance,String locationName,String locationId) {
        if(attendanceFix==null){resetCheckInButton();problem("Get a fresh GPS reading and retry.");return;}
        sendSecureAttendance("in",attendanceFix);
    }
    private void sendSecureAttendance(String action,android.location.Location fix){
        Map<String,Object> data=ShiftNestSecureRequests.gps(fix);
        data.put("action",action);data.put("expectedDate",attendanceDay);data.put("requestId",java.util.UUID.randomUUID().toString().replace("-",""));
        data.put("siteName",attendanceSite);data.put("locationName",attendancePlace);
        ShiftNestSecureRequests.call("recordAttendance",data).addOnSuccessListener(r->{
            if(isFinishing()||isDestroyed())return;isCheckingIn=false;isCheckingOut=false;
            if(!(r.getData() instanceof Map)){loadTodayAttendance();problem("Attendance response unavailable. Refresh to check the saved result.");return;}
            Map<?,?> value=(Map<?,?>)r.getData();
            applyAttendanceState(String.valueOf(value.get("date")),String.valueOf(value.get("checkIn")),String.valueOf(value.get("checkOut")));
            refreshLeaveBalance();
            if("out".equals(action)){stopLocationTracking();success("CHECK OUT SUCCESSFUL!\nAttendance saved. Check Out and Add Location are disabled.");}
            else {startLocationTracking(String.valueOf(value.get("locationId")));success("CHECK IN SUCCESSFUL!\nAttendance saved. Check Out and Add Location are available.");}
        }).addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;isCheckingIn=false;isCheckingOut=false;loadTodayAttendance();problem(backendError("Save attendance",e));});
    }

    private void startLocationTracking(String locationId) {

        Intent serviceIntent =
                new Intent(
                        this,
                        LocationTrackingService.class
                );

        serviceIntent.putExtra(
                "employeeId",
                employeeId
        );

        serviceIntent.putExtra(
                "locationId",
                locationId == null
                        ? ""
                        : locationId
        );

        try {
            if(android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.O)ContextCompat.startForegroundService(this,serviceIntent);
            else startService(serviceIntent);
        } catch(RuntimeException error){problem("Attendance saved. Tracking could not start; check location permission.");}
    }

    private void stopLocationTracking() {

        Intent serviceIntent =
                new Intent(
                        this,
                        LocationTrackingService.class
                );

        stopService(serviceIntent);
    }

    private void checkOut(){promptAttendancePlace(true);}
    private void promptAttendancePlace(boolean out){
        if(placeDialogOpen||isCheckingIn||isCheckingOut||!attendanceLoaded||checkedOutToday||out!=checkedInToday)return;
        placeDialogOpen=true;
        android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(android.widget.LinearLayout.VERTICAL);fields.setPadding(32,16,32,16);
        android.widget.EditText site=new android.widget.EditText(this),place=new android.widget.EditText(this);
        site.setHint("Site name (e.g. Urban Lux)");place.setHint("Location (e.g. Kandana)");
        site.setSingleLine(true);place.setSingleLine(true);
        site.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});place.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        fields.addView(site);fields.addView(place);
        androidx.appcompat.app.AlertDialog dialog=ShiftNestLanguage.dialog(this).setTitle(out?"Check out — site & location":"Check in — site & location").setView(fields).setNegativeButton("Cancel",null).setPositiveButton(out?"Check out":"Check in",null).create();
        dialog.setOnDismissListener(d->placeDialogOpen=false);dialog.setOnShowListener(d->dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            String sn=site.getText().toString().trim(),ln=place.getText().toString().trim();
            if(sn.isEmpty()){site.setError("Enter the site name");return;}if(ln.isEmpty()){place.setError("Enter the location");return;}
            attendanceSite=sn;attendancePlace=ln;dialog.dismiss();
            if(out)saveCheckOutWithDetails();else if(hasLocationPermission())getCurrentLocationAndCheckIn();else locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});
        }));dialog.show();
    }

    private void saveCheckOutWithDetails() {
        if(isCheckingIn||isCheckingOut||!attendanceLoaded||!checkedInToday||checkedOutToday)return;
        if(!ShiftNestData.date().equals(attendanceDay)){loadTodayAttendance();return;}
        if(!requireOnline("Check out"))return;
        if(!hasLocationPermission()||!locationEnabled()){showLocationProblem();return;}
        attendanceGeneration++;isCheckingOut=true;updateAttendanceButtons();
        ShiftNestRequests.read(fusedLocationClient.getCurrentLocation(new com.google.android.gms.location.CurrentLocationRequest.Builder().setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(25000).build(),null))
        .addOnSuccessListener(fix->{if(isFinishing()||isDestroyed())return;
            if(fix==null||ShiftNestLocationGuard.isMock(fix)){isCheckingOut=false;updateAttendanceButtons();problem("A fresh real GPS reading is required to check out.");if(fix!=null)ShiftNestLocationGuard.report("tracking");return;}
            sendSecureAttendance("out",fix);
        }).addOnFailureListener(e->{isCheckingOut=false;updateAttendanceButtons();problem("Current GPS unavailable. Enable precise location and retry.");});
    }

    private void loadTodayAttendance() {
        if(employeeId==null||employeeId.isEmpty())return;
        final int request=++attendanceGeneration;
        final String day=ShiftNestData.date();
        attendanceLoaded=false;
        updateAttendanceButtons();
        if(attendanceStatus!=null)attendanceStatus.setText("Loading attendance for "+ShiftNestData.date()+"...");
        // A missing document cannot satisfy resource.data ownership rules.
        // Query by this employee/date so an empty result is a valid pre-check-in state.
        ShiftNestRequests.read(db.collection("attendance").whereEqualTo("employeeId",employeeId)
                .whereEqualTo("date",day).limit(1).get(com.google.firebase.firestore.Source.SERVER))
            .addOnSuccessListener(records->{
                if(request!=attendanceGeneration||isCheckingIn||isCheckingOut||isFinishing()||isDestroyed())return;
                if(!day.equals(ShiftNestData.date())){loadTodayAttendance();return;}
                com.google.firebase.firestore.DocumentSnapshot document=records.isEmpty()?null:records.getDocuments().get(0);
                String in=document==null?"":ShiftNestData.string(document,"checkIn"),out=document==null?"":ShiftNestData.string(document,"checkOut");
                applyAttendanceState(day,in,out);
                if(attendanceStatus!=null)attendanceStatus.setText(ShiftNestData.date()+" • "+(in.isEmpty()?"Not checked in":out.isEmpty()?"Checked in / working":"Checked out"));
                if(!in.isEmpty()&&out.isEmpty()&&"Working".equals(ShiftNestData.string(document,"workStatus")))loadEmployeeLocationAndResumeTracking();
                if(!out.isEmpty())stopLocationTracking();
            }).addOnFailureListener(e->{
                if(request!=attendanceGeneration||isFinishing()||isDestroyed())return;
                if(attendanceStatus!=null)attendanceStatus.setText(backendError("Load today’s attendance",e)+"\nTap Refresh attendance / leave balance.");
            });
    }

    private void loadEmployeeLocationAndResumeTracking() {

        ShiftNestRequests.read(db.collection("employees")
                .document(employeeId)
                .get())
                .addOnSuccessListener(document -> {

                    if (!attendanceLoaded || !checkedInToday || checkedOutToday
                            || !attendanceDay.equals(ShiftNestData.date()) || isFinishing() || isDestroyed()) return;
                    if (!document.exists()) {
                        return;
                    }

                    String workLocationType =
                            document.getString(
                                    "workLocationType"
                            );

                    String locationId =
                            document.getString(
                                    "locationId"
                            );

                    if ("no_fixed".equals(workLocationType)) {

                        startLocationTracking("");

                    } else {

                        startLocationTracking(
                                locationId == null
                                        ? ""
                                        : locationId
                        );
                    }
                });
    }

    private void resetCheckInButton() {

        isCheckingIn = false;

        updateAttendanceButtons();
    }

    private void applyAttendanceState(String day, String checkIn, String checkOut) {
        if (!day.equals(ShiftNestData.date())) { loadTodayAttendance(); return; }
        attendanceDay=day;
        attendanceLoaded=true;
        checkedInToday=!checkIn.isEmpty();
        checkedOutToday=!checkOut.isEmpty();
        tvCheckIn.setText("Check In: "+(checkIn.isEmpty()?"--":checkIn));
        tvCheckOut.setText("Check Out: "+(checkOut.isEmpty()?"--":checkOut));
        updateAttendanceButtons();
    }

    private void updateAttendanceButtons() {
        boolean ready=attendanceLoaded && !isCheckingIn && !isCheckingOut
                && attendanceDay.equals(ShiftNestData.date());
        btnCheckIn.setEnabled(ready && !checkedInToday && !checkedOutToday);
        btnCheckOut.setEnabled(ready && checkedInToday && !checkedOutToday);
        if(outingNotice!=null)outingNotice.setEnabled(ready && checkedInToday && !checkedOutToday);
        if(lateNotice!=null)lateNotice.setEnabled(!isCheckingIn && !isCheckingOut && (!attendanceLoaded || !checkedOutToday));
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        // Do NOT stop tracking here.
        // Tracking must continue even when dashboard closes.
    }
}
