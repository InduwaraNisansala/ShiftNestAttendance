package com.shiftnest.attendance;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import com.google.firebase.auth.FirebaseAuth;

public class EmployeeLoginActivity extends ShiftNestBaseActivity {
    private boolean checking;
    private int loginGeneration;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(AdminSession.restore(this))return;
        android.content.SharedPreferences prefs=getSharedPreferences("ShiftNestAttendancePrefs",MODE_PRIVATE);
        com.google.firebase.auth.FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(prefs.getBoolean("loggedIn",false)&&user!=null&&user.getPhoneNumber()!=null&&user.getPhoneNumber().equals(prefs.getString("phone",""))){startActivity(new Intent(this,EmployeeDashboardActivity.class));finish();return;}
        setContentView(R.layout.activity_employee_login);
        EditText phone=findViewById(R.id.etPhone);Button send=findViewById(R.id.btnSendOtp),admin=findViewById(R.id.btnAdminLogin);
        send.setOnClickListener(v->{
            if(checking){feedback("Checking employee registration. Please wait...",false);return;}
            String value=ShiftNestData.normalizePhone(phone.getText().toString());
            if(value.isEmpty()){validationError(phone,"Enter a valid number, e.g. 0771234567 or +94771234567");return;}
            if(!requireOnline("Employee login"))return;
            hideKeyboard();checking=true;final int attempt=++loginGeneration;
            send.setEnabled(false);send.setText("Checking registration...");phone.setEnabled(false);admin.setEnabled(false);
            feedback("Checking whether admin registered this phone number...",false);
            com.google.firebase.functions.FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("lookupEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS)
                .call(java.util.Collections.singletonMap("phone",value))
                .addOnSuccessListener(result->{
                    if(attempt!=loginGeneration||isFinishing()||isDestroyed())return;
                    checking=false;send.setEnabled(true);send.setText("Send OTP");phone.setEnabled(true);admin.setEnabled(true);
                    Object data=result.getData();
                    if(!(data instanceof java.util.Map)||!Boolean.TRUE.equals(((java.util.Map<?,?>)data).get("registered"))){problem("Number not registered. Please contact your administrator.");return;}
                    feedback("Registered employee found. Opening SMS verification...",false);
                    Intent intent=new Intent(this,VerifyOtpActivity.class);intent.putExtra("phone",value);startActivity(intent);
                })
                .addOnFailureListener(error->{
                    if(attempt!=loginGeneration||isFinishing()||isDestroyed())return;
                    checking=false;send.setEnabled(true);send.setText("Send OTP");phone.setEnabled(true);admin.setEnabled(true);
                    String message;
                    if(error instanceof com.google.firebase.functions.FirebaseFunctionsException&&((com.google.firebase.functions.FirebaseFunctionsException)error).getCode()==com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND){
                        String detail=error.getMessage()==null?"":error.getMessage();
                        message=detail.contains("not registered")?"Number not registered. Please contact your administrator.":backendError("Check employee registration",error);
                    }else message=backendError("Check employee registration",error);
                    problem(message);
                });
        });
        admin.setOnClickListener(v->startActivity(new Intent(this,AdminLoginActivity.class)));
    }
    @Override protected void onDestroy(){loginGeneration++;super.onDestroy();}
}
