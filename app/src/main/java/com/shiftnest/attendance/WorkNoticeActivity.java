package com.shiftnest.attendance;

import android.Manifest;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.firebase.functions.*;
import com.google.android.gms.location.*;
import java.util.*;

/** The backend derives employee identity, date and alert color from authenticated records. */
public class WorkNoticeActivity extends ShiftNestBaseActivity {
    private String type,requestId;
    private EditText reason,destination,siteName;
    private Button send;
    private boolean busy;
    private Map<String,Object> pending;
    private final ActivityResultLauncher<String[]> permissions=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),r->{if(Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_FINE_LOCATION))||Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_COARSE_LOCATION)))locate();else{busy=false;buttons();problem("Location permission is required to record an outside destination.");}});
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;
        type="outing".equals(getIntent().getStringExtra("type"))?"outing":"late";
        requestId=state==null?newId():state.getString("requestId",newId());
        page(type.equals("late")?"Running late / Message admin":"Add Location");
        screen.addView(text(type.equals("late")?"Tell admin why check-in will be late. Your message appears in the attendance and daily reports, including before you check in.":"After check-in, add as many site/location records as you need. Fixed-site alerts are RED; no-fixed-site alerts are GREEN. This notice does not approve travel or change your assigned site. GPS tracking and site alerts continue.",16));
        destination=new EditText(this);destination.setHint("Location (e.g. Kandana)");destination.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        siteName=new EditText(this);siteName.setHint("Site name (e.g. Urban Lux)");siteName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        if(type.equals("outing")){screen.addView(siteName);screen.addView(destination);}
        reason=new EditText(this);reason.setHint("Reason / message to admin");reason.setMinLines(3);reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});screen.addView(reason);
        send=button(type.equals("late")?"Send late message":"Add location & notify admin",this::submit);screen.addView(send);
        if(state!=null){reason.setText(state.getString("reason",""));destination.setText(state.getString("destination",""));siteName.setText(state.getString("siteName",""));if(state.getBoolean("pending",false)){type=state.getString("type",type);pending=new HashMap<>();pending.put("type",type);pending.put("requestId",requestId);pending.put("reason",reason.getText().toString().trim());pending.put("destination",type.equals("outing")?destination.getText().toString().trim():"");pending.put("siteName",type.equals("outing")?siteName.getText().toString().trim():"");if(type.equals("outing")){pending.put("latitude",state.getDouble("latitude"));pending.put("longitude",state.getDouble("longitude"));pending.put("accuracyMeters",state.getDouble("accuracyMeters"));pending.put("fixTime",state.getLong("fixTime"));pending.put("mock",false);}}}
        buttons();
    }
    private String newId(){return UUID.randomUUID().toString().replace("-","");}
    private void buttons(){send.setEnabled(!busy);send.setText(busy?"Saving message...":pending!=null?"Retry the same message":type.equals("late")?"Send late message":"Add location & notify admin");reason.setEnabled(!busy&&pending==null);destination.setEnabled(!busy&&pending==null);siteName.setEnabled(!busy&&pending==null);}
    private void submit(){
        if(busy||!requireOnline("Send attendance message"))return;
        if(pending!=null){call();return;}
        if(reason.getText().toString().trim().isEmpty()){validationError(reason,"Enter a reason");return;}
        if(!type.equals("outing")){prepare(null);return;}
        if(siteName.getText().toString().trim().isEmpty()){validationError(siteName,"Enter a site name");return;}
        if(destination.getText().toString().trim().isEmpty()){validationError(destination,"Enter the location");return;}
        busy=true;buttons();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate();
        else permissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void locate(){
        if(!locationEnabled()){busy=false;buttons();showLocationProblem();return;}
        ShiftNestRequests.read(LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(new CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(25000).build(),null))
        .addOnSuccessListener(location->{if(isFinishing()||isDestroyed())return;if(location==null||ShiftNestLocationGuard.isMock(location)){busy=false;buttons();problem("A current, real GPS location is required. No destination was saved.");return;}prepare(location);})
        .addOnFailureListener(e->{busy=false;buttons();problem("Current location unavailable. Enable Location and retry.");});
    }
    private void prepare(android.location.Location fix){pending=new HashMap<>();pending.put("type",type);pending.put("requestId",requestId);pending.put("reason",reason.getText().toString().trim());pending.put("siteName",type.equals("outing")?siteName.getText().toString().trim():"");pending.put("destination",type.equals("outing")?destination.getText().toString().trim():"");if(fix!=null)pending.putAll(ShiftNestSecureRequests.gps(fix));call();}
    private void call(){busy=true;buttons();hideKeyboard();ShiftNestSecureRequests.call("submitWorkNotice",pending)
        .addOnSuccessListener(r->{if(isFinishing()||isDestroyed())return;busy=false;pending=null;requestId=newId();buttons();ShiftNestLanguage.dialog(this).setTitle("Saved successfully").setMessage("Your attendance message is saved. Admin notification is queued; push delivery depends on notification permissions.").setPositiveButton("OK",(d,w)->finish()).show();})
        .addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;busy=false;boolean uncertain=!(e instanceof FirebaseFunctionsException)||((FirebaseFunctionsException)e).getCode()==FirebaseFunctionsException.Code.DEADLINE_EXCEEDED||((FirebaseFunctionsException)e).getCode()==FirebaseFunctionsException.Code.UNAVAILABLE||((FirebaseFunctionsException)e).getCode()==FirebaseFunctionsException.Code.INTERNAL;if(!uncertain){pending=null;requestId=newId();}buttons();problem(backendError("Save attendance message",e)+(uncertain?"\nRetry the same message to check its saved result; do not send a duplicate.":""));});}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("requestId",requestId);out.putString("type",type);out.putString("reason",reason.getText().toString());out.putString("siteName",siteName.getText().toString());out.putString("destination",destination.getText().toString());out.putBoolean("pending",pending!=null);if(pending!=null&&pending.get("latitude") instanceof Number){out.putDouble("latitude",((Number)pending.get("latitude")).doubleValue());out.putDouble("longitude",((Number)pending.get("longitude")).doubleValue());out.putDouble("accuracyMeters",((Number)pending.get("accuracyMeters")).doubleValue());out.putLong("fixTime",((Number)pending.get("fixTime")).longValue());}}
}
