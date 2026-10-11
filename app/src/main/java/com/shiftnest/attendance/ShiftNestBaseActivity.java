package com.shiftnest.attendance;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.OutputStream;

/** Shared branded navigation, appearance and safe PDF document export. */
public class ShiftNestBaseActivity extends AppCompatActivity {
    protected LinearLayout screen;
    private String activeLanguage;
    private android.view.ViewTreeObserver.OnGlobalLayoutListener languageLayout;
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(ShiftNestLanguage.context(base));}
    @Override protected void onResume(){
        super.onResume();if(activeLanguage!=null&&!activeLanguage.equals(ShiftNestLanguage.selected(this))){recreate();return;}
        ShiftNestLanguage.views(this,findViewById(android.R.id.content));
    }

    private File pendingPdf;
    private boolean creatingPdf;
    private TextView actionFeedback;
    private android.app.DatePickerDialog dateDialog;
    protected final ActivityResultLauncher<String> savePdf = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
            if(uri==null){toast("PDF save cancelled.");return;}
            if(pendingPdf==null||!pendingPdf.exists()){problem("The prepared PDF is unavailable. Generate the report again.");return;}
            try (java.io.InputStream in = new java.io.FileInputStream(pendingPdf);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Cannot open selected destination");
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer,0,count);
                success("PDF downloaded/saved successfully to the location you selected.");
            } catch (Exception e) { problem("PDF could not be saved. Choose a destination and try again."); }
        });
    private final ActivityResultLauncher<String> notificationPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(!granted)toast("Phone notifications are off. You can still read alerts in the Notifications page.");});
    protected void setupNotifications(){
        if(BuildConfig.DEMO_BACKEND)return;
        ShiftNestNotifications.register(this);
        if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("ShiftNestNotificationPermission",MODE_PRIVATE).getBoolean("asked",false)){
            getSharedPreferences("ShiftNestNotificationPermission",MODE_PRIVATE).edit().putBoolean("asked",true).apply();notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }
    protected void notificationSettings(){
        Intent intent;
        if(android.os.Build.VERSION.SDK_INT>=26){intent=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());}
        else{intent=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));}
        startActivity(intent);
    }
    @Override protected void onCreate(Bundle state) {
        int mode=getSharedPreferences("ShiftNestAppearance",MODE_PRIVATE).getInt("mode",AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(mode);
        super.onCreate(state);
        if(BuildConfig.DEMO_BACKEND&&!ShiftNestApplication.demoReady()){
            startActivity(new Intent(this,DemoConnectionActivity.class));finish();return;
        }
        activeLanguage=ShiftNestLanguage.selected(this);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (state!=null && state.getString("pendingPdf")!=null) pendingPdf=new File(state.getString("pendingPdf"));
        if (isAdminScreen()) {
            if (FirebaseAuth.getInstance().getCurrentUser()==null) {startActivity(new Intent(this,AdminLoginActivity.class));finish();return;}
            String uid=FirebaseAuth.getInstance().getCurrentUser().getUid();
            ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("admins").document(uid).get(com.google.firebase.firestore.Source.SERVER)).addOnSuccessListener(d->{
                if (!Boolean.TRUE.equals(d.get("active"))) {problem("Admin access required");logout();}
            }).addOnFailureListener(e->problem(backendError("Verify admin access",e)));
        }
    }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        configureInputs(findViewById(android.R.id.content));
        android.view.View content=findViewById(android.R.id.content);
        languageLayout=()->ShiftNestLanguage.views(this,content);
        content.getViewTreeObserver().addOnGlobalLayoutListener(languageLayout);
        ShiftNestLanguage.views(this,content);
    }
    private void configureInputs(View view) {
        if (view instanceof EditText) styleEditable((EditText) view);
        else if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)configureInputs(group.getChildAt(i));
        }
    }
    protected void styleEditable(EditText field) {
        // Preserve input types, filters and intentionally disabled leave-quota fields.
        field.setFocusable(true);
        field.setFocusableInTouchMode(true);
        field.setCursorVisible(true);
        field.setShowSoftInputOnFocus(true);
        field.setTextColor(color(R.color.shiftnest_text));
        field.setHintTextColor(color(R.color.shiftnest_hint));
        field.setMinimumHeight(dp(56));
        if(field.getHint()!=null)field.setHint(ShiftNestLanguage.text(this,field.getHint()));
        field.setOnTouchListener((view,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_UP && field.isEnabled()) {
                field.post(()->{
                    if(!field.isEnabled() || !field.isShown())return;
                    field.requestFocus();
                    android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(keyboard!=null)keyboard.showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }
            return false; // Keep normal selection, paste and cursor movement.
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); if(pendingPdf!=null)state.putString("pendingPdf",pendingPdf.getAbsolutePath());
    }
    protected boolean isAdminScreen() {
        return this instanceof StorageHealthActivity || this instanceof AdminDashboardActivity || this instanceof AddEmployeeActivity || this instanceof EditEmployeeActivity || this instanceof AddLocationActivity || this instanceof EmployeeListActivity || this instanceof AttendanceReportActivity || this instanceof DailyAttendanceReportActivity || this instanceof AdminLocationTrackingActivity || this instanceof LeaveSheetActivity || this instanceof HolidayCalendarActivity || this instanceof DepartmentCalendarActivity || this instanceof AdminPersonalActivity || (this instanceof EmployeeDashboardActivity && "admin".equals(getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("sessionRole","")));
    }
    protected int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    protected int color(int id){return ContextCompat.getColor(this,id);}
    private final android.os.Handler messageHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Set<String> messages=new java.util.LinkedHashSet<>();
    private androidx.appcompat.app.AlertDialog problemDialog;
    private boolean messageScheduled;
    protected void problem(String message) {
        if(isFinishing()||isDestroyed())return;
        feedback(message, true);
        messages.add(message==null||message.trim().isEmpty()?"Request could not be completed. Check the entered details and try again.":message);
        if(messageScheduled)return;messageScheduled=true;
        messageHandler.post(()->{
            messageScheduled=false;if(isFinishing()||isDestroyed()){messages.clear();return;}
            StringBuilder body=new StringBuilder();for(String line:messages){if(body.length()>0)body.append("\n\n");body.append("• ").append(line);}
            if(problemDialog!=null&&problemDialog.isShowing()){problemDialog.setMessage(ShiftNestLanguage.text(this,body.toString()));return;}
            problemDialog=ShiftNestLanguage.dialog(this).setTitle("Please check").setMessage(body.toString()).setPositiveButton("OK",null).create();
            problemDialog.setOnDismissListener(d->{messages.clear();problemDialog=null;});problemDialog.show();
        });
    }
    protected void success(String message){success(message,null);}
    protected void success(String message,Runnable after){
        if(isFinishing()||isDestroyed())return;
        hideKeyboard();
        feedback(message, false);
        ShiftNestLanguage.dialog(this).setTitle("Success").setMessage(message)
            .setCancelable(after==null).setPositiveButton("OK",(dialog,which)->{if(after!=null)after.run();}).show();
    }
    /** A connectivity hint only; the request itself still decides whether Firebase is reachable. */
    protected boolean networkAvailable(){
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(manager==null)return true;
        android.net.Network network=manager.getActiveNetwork();
        android.net.NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        if(BuildConfig.DEMO_BACKEND)return caps!=null&&(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        return caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    protected boolean requireOnline(String operation){
        if(networkAvailable())return true;
        problem("Check your internet connection and try again.");return false;
    }
    protected boolean locationEnabled(){
        android.location.LocationManager manager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
        return manager!=null&&(manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));
    }
    protected void showLocationProblem(){
        if(isFinishing()||isDestroyed())return;
        ShiftNestLanguage.dialog(this).setTitle("Location unavailable").setMessage("Turn on phone Location and allow location access for ShiftNest Attendance. Use Precise location for a fixed work site. Move to an open area, wait for a GPS fix and retry. No check-in has been saved by this attempt.").setPositiveButton("Location settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Close",null).show();
    }
    protected void hideKeyboard(){
        android.view.View focus=getCurrentFocus();
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(focus!=null&&keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);
    }
    protected String backendError(String operation,Exception error){
        String code="";
        String detail=error==null?null:error.getMessage();
        if(error instanceof com.google.firebase.functions.FirebaseFunctionsException)
            code=((com.google.firebase.functions.FirebaseFunctionsException)error).getCode().name();
        else if(error instanceof com.google.firebase.firestore.FirebaseFirestoreException)
            code=((com.google.firebase.firestore.FirebaseFirestoreException)error).getCode().name();
        else if(error instanceof com.google.firebase.auth.FirebaseAuthException)
            code=((com.google.firebase.auth.FirebaseAuthException)error).getErrorCode();
        else if(error instanceof java.util.concurrent.TimeoutException)code="DEADLINE_EXCEEDED";
        else if(error instanceof com.google.firebase.FirebaseNetworkException)code="NETWORK";
        else if(error instanceof com.google.firebase.FirebaseTooManyRequestsException)code="RESOURCE_EXHAUSTED";
        // Debug builds retain only operation and error code, never server messages or credentials.
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)
            android.util.Log.w("ShiftNestRequest",operation+": "+code);
        return ShiftNestErrorMessages.message(code,detail);
    }
    protected void validationError(EditText field,String message) {
        field.setError(ShiftNestLanguage.text(this,message));
        if(messages.isEmpty())field.requestFocus();
        String hint=field.getHint()==null?"":field.getHint().toString();
        problem(hint.isEmpty()?message:hint+": "+ShiftNestLanguage.text(this,message));
    }
    @Override protected void onDestroy(){
        android.view.View content=findViewById(android.R.id.content);
        if(content!=null&&languageLayout!=null&&content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnGlobalLayoutListener(languageLayout);
        messageHandler.removeCallbacksAndMessages(null);
        if(problemDialog!=null)problemDialog.dismiss();
        if(dateDialog!=null)dateDialog.dismiss();
        super.onDestroy();
    }
    protected void feedback(String message, boolean error) {
        if(actionFeedback==null||isFinishing()||isDestroyed())return;
        actionFeedback.setVisibility(View.VISIBLE);
        actionFeedback.setText(message);
        actionFeedback.setTextColor(error?0xFFF05454:color(R.color.shiftnest_text));
        actionFeedback.announceForAccessibility(message);
    }
    protected void toast(String text){Toast.makeText(this,ShiftNestLanguage.text(this,text),Toast.LENGTH_LONG).show();}
    protected TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color(R.color.shiftnest_text));t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}
    protected Button button(String label,Runnable action){Button b=new androidx.appcompat.widget.AppCompatButton(this);b.setText(label);b.setTextColor(color(R.color.shiftnest_on_primary));b.setBackgroundResource(R.drawable.shiftnest_primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));b.setLayoutParams(p);b.setMinHeight(dp(52));b.setOnClickListener(v->action.run());return b;}
    protected void open(Class<?> type){Intent i=new Intent(this,type);i.putExtra("homeRole",role());startActivity(i);}
    protected String role(){if(AdminSession.personal(this))return "employee";return getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).getString("sessionRole","employee");}
    protected void home(){
        Class<?> target;
        if(this instanceof AdminLoginActivity || this instanceof EmployeeLoginActivity || this instanceof VerifyOtpActivity || this instanceof MainActivity)target=EmployeeLoginActivity.class;
        else target="admin".equals(role())?AdminDashboardActivity.class:EmployeeDashboardActivity.class;
        if(getClass()==target)return;
        Intent i=new Intent(this,target);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();
    }
    protected void logout(){
        stopService(new Intent(this,LocationTrackingService.class));
        ShiftNestNotifications.clear(this);
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE).edit().clear().apply();
        Intent i=new Intent(this,EmployeeLoginActivity.class);i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(i);finish();
    }
    private void wrap(View content,String title){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundResource(R.drawable.shiftnest_background);outer.setFitsSystemWindows(true);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button back=button("‹ Back",()->getOnBackPressedDispatcher().onBackPressed());back.setTextSize(12);back.setLayoutParams(new LinearLayout.LayoutParams(dp(72),dp(48)));bar.addView(back);
        TextView heading=text(title,16);heading.setGravity(Gravity.CENTER);heading.setTextColor(color(R.color.shiftnest_heading));bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button home=button("Home",this::home);home.setTextSize(12);home.setLayoutParams(new LinearLayout.LayoutParams(dp(64),dp(48)));bar.addView(home);
        if(!(this instanceof EmployeeLoginActivity))outer.addView(bar);
        if(BuildConfig.DEMO_BACKEND){TextView demo=text("DEMO: local test data - "+ShiftNestApplication.demoHost(),12);demo.setPadding(dp(18),dp(4),dp(18),dp(4));outer.addView(demo);}
        actionFeedback=text("",15);actionFeedback.setMaxLines(5);actionFeedback.setVisibility(View.GONE);actionFeedback.setPadding(dp(18),dp(6),dp(18),dp(6));outer.addView(actionFeedback);
        FrameLayout frame=new FrameLayout(this);ImageView watermark=new ImageView(this);watermark.setImageResource(R.drawable.shiftnest_logo);watermark.setAlpha(0.055f);watermark.setClickable(false);watermark.setFocusable(false);watermark.setScaleType(ImageView.ScaleType.FIT_CENTER);watermark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams mark=new FrameLayout.LayoutParams(-1,dp(190),Gravity.CENTER);mark.setMargins(dp(18),0,dp(18),0);frame.addView(watermark,mark);frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
        outer.addView(frame,new LinearLayout.LayoutParams(-1,0,1));super.setContentView(outer);
    }
    @Override public void setContentView(int layout){
        View content=getLayoutInflater().inflate(layout,null);content.setBackgroundColor(Color.TRANSPARENT);content.setFitsSystemWindows(false);
        View oldNav=content.findViewById(R.id.btnTopBack);if(oldNav!=null && oldNav.getParent() instanceof View)((View)oldNav.getParent()).setVisibility(View.GONE);
        wrap(content,title());
    }
    private String title(){if(getClass()==EmployeeDashboardActivity.class && AdminSession.personal(this))return "My Attendance";String s=getClass().getSimpleName().replace("Activity","");return s.replaceAll("([a-z])([A-Z])","$1 $2");}
    protected void page(String title){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setPadding(dp(18),dp(12),dp(18),dp(24));scroll.addView(screen);wrap(scroll,title);
    }
    protected void styleSpinner(Spinner spinner) {
        spinner.setMinimumHeight(dp(64));
        spinner.setBackgroundTintList(null);
        spinner.setBackgroundResource(R.drawable.shiftnest_input);
        spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color(R.color.shiftnest_surface)));
        spinner.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
    }
    protected Spinner departments(Runnable changed){
        Spinner spinner=new ShiftNestChoiceSpinner(this);
        String[] values={"All Employees","Sales","Office","Hotel","Other"};
        String[] labels={"All Employees","Sales Officers","Office Staff","Hotel Staff","Other departments"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,R.layout.shiftnest_spinner_selected,values);
        adapter.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);spinner.setAdapter(adapter);
        spinner.setVisibility(View.GONE);screen.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
        screen.addView(text("Filter by department",16));
        Button[] choices=new Button[labels.length];
        for(int first=0;first<labels.length;first+=2){
            LinearLayout row=new LinearLayout(this);
            for(int i=first;i<Math.min(first+2,labels.length);i++){
                final int index=i;
                Button option=button(labels[i],()->{
                    spinner.setSelection(index);
                    for(int j=0;j<choices.length;j++)if(choices[j]!=null)choices[j].setAlpha(j==index?1f:0.6f);
                    changed.run();
                });
                option.setTextSize(13);option.setAlpha(i==0?1f:0.6f);choices[i]=option;
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(option,params);
            }
            screen.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,android.view.View view,int index,long id){
                for(int i=0;i<choices.length;i++)if(choices[i]!=null)choices[i].setAlpha(i==index?1f:0.6f);
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
        return spinner;
    }
    protected void chooseDate(String key,String title,java.time.LocalDate selected,java.util.function.Consumer<java.time.LocalDate> chosen){
        if(isFinishing()||isDestroyed())return;
        if(dateDialog!=null&&dateDialog.isShowing())return;
        hideKeyboard();
        dateDialog=new android.app.DatePickerDialog(this,(view,year,month,day)->{
            java.time.LocalDate value=java.time.LocalDate.of(year,month+1,day);
            feedback("Selected date: "+value,false);
            chosen.accept(value);
        },selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth());
        dateDialog.setTitle(ShiftNestLanguage.text(this,title));
        dateDialog.setOnDismissListener(dialog->dateDialog=null);
        dateDialog.show();
    }
    @SuppressWarnings("unchecked")
    protected void bindDateChoice(String key,java.util.function.Consumer<java.time.LocalDate> chosen){
        androidx.fragment.app.Fragment fragment=getSupportFragmentManager().findFragmentByTag("shiftnest_date_"+key);
        if(fragment instanceof com.google.android.material.datepicker.MaterialDatePicker){
            com.google.android.material.datepicker.MaterialDatePicker<Long> picker=(com.google.android.material.datepicker.MaterialDatePicker<Long>)fragment;
            picker.clearOnPositiveButtonClickListeners();
            picker.addOnPositiveButtonClickListener(value->{if(!isFinishing()&&!isDestroyed())chosen.accept(ShiftNestDates.date(value));});
        }
    }
    protected void exportExistingPdf(File file, String name) {
        pendingPdf=file;savePdf.launch(name.replaceAll("[^A-Za-z0-9_-]", "_")+".pdf");
    }
    protected void exportPdf(String title,String[] headers,java.util.List<String[]> rows){
        if(creatingPdf){toast("PDF is being prepared. Please wait.");return;}
        creatingPdf=true;
        java.util.List<String[]> snapshot=new java.util.ArrayList<>();
        for(String[] row:rows)snapshot.add(row.clone());
        String[] columns=headers.clone();toast("Preparing PDF...");
        new Thread(()->{
            try {
                File output=ShiftNestPdf.create(this,title,columns,snapshot);
                runOnUiThread(()->{creatingPdf=false;if(isFinishing()||isDestroyed())return;pendingPdf=output;savePdf.launch(title.replaceAll("[^A-Za-z0-9_-]","_")+".pdf");});
            } catch(Exception e){runOnUiThread(()->{creatingPdf=false;if(!isFinishing()&&!isDestroyed())problem("PDF could not be created. Please try again.");});}
        },"ShiftNestPdfExport").start();
    }
}
