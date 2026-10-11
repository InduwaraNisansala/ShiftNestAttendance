package com.shiftnest.attendance;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class VerifyOtpActivity extends ShiftNestBaseActivity {
    private EditText otp;
    private TextView status;
    private Button verify,resend;
    private String phone,verificationId;
    private PhoneAuthProvider.ForceResendingToken token;
    private boolean checking,sending,verifying,smsInProgress;
    private int generation;
    private long nextResend,deadline;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){
        if(isFinishing()||isDestroyed())return;
        if((checking||sending)&&deadline>0&&System.currentTimeMillis()>deadline){generation++;checking=false;sending=false;smsInProgress=false;showError("OTP request timed out. Please try again.");}
        buttons();handler.postDelayed(this,1000);
    }};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_verify_otp);
        phone=getIntent().getStringExtra("phone");if(phone==null||ShiftNestData.normalizePhone(phone).isEmpty()){toast("Return to login and enter your phone number.");finish();return;}
        otp=findViewById(R.id.etOtp);verify=findViewById(R.id.btnVerifyOtp);
        android.view.ViewGroup parent=(android.view.ViewGroup)verify.getParent();
        status=text("Checking registered employee...",16);parent.addView(status,parent.indexOfChild(otp));
        resend=button("Resend SMS OTP",()->requestOtp(true));parent.addView(resend);
        parent.addView(text("By requesting an OTP you agree to phone verification through Firebase. Enter the 6-digit SMS code.",14));
        if(state!=null){verificationId=state.getString("verificationId");token=state.getParcelable("token");nextResend=state.getLong("nextResend");smsInProgress=state.getBoolean("smsInProgress");deadline=state.getLong("deadline");otp.setText(state.getString("otp",""));}
        verify.setOnClickListener(v->{String code=otp.getText().toString().trim();if(!code.matches("[0-9]{6}")){validationError(otp,"Enter the 6-digit OTP");return;}if(verificationId==null){showError("Request an OTP first.");return;}signIn(PhoneAuthProvider.getCredential(verificationId,code));});
        if(verificationId!=null)status.setText("SMS code sent to "+phone+". Enter the code below.");
        buttons();handler.post(tick);
    }
    @Override protected void onStart(){
        super.onStart();if(phone==null)return;
        if(smsInProgress){if(deadline==0)deadline=System.currentTimeMillis()+120000;attachSms(false,generation);}
        else if(verificationId==null&&!checking&&!sending)requestOtp(false);
    }
    private boolean current(int id){return id==generation&&!isFinishing()&&!isDestroyed();}
    private void buttons(){if(verify==null)return;boolean busy=checking||sending||verifying;verify.setEnabled(!busy&&verificationId!=null);long wait=Math.max(0,(nextResend-System.currentTimeMillis()+999)/1000);resend.setEnabled(!busy&&wait==0);resend.setText(wait>0?"Resend OTP in "+wait+"s":"Resend SMS OTP");}
    private void requestOtp(boolean force){
        if(checking||sending||verifying||(force&&System.currentTimeMillis()<nextResend))return;
        if(!requireOnline("Request SMS OTP"))return;
        hideKeyboard();
        int id=++generation;checking=true;deadline=System.currentTimeMillis()+120000;status.setText("Checking employee registration...");buttons();
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("lookupEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(Collections.singletonMap("phone",phone))
            .addOnSuccessListener(r->{if(!current(id))return;checking=false;sending=true;smsInProgress=true;nextResend=System.currentTimeMillis()+60000;status.setText("Sending SMS OTP to "+phone+". Complete app verification if prompted.");buttons();attachSms(force,id);})
            .addOnFailureListener(e->{if(!current(id))return;checking=false;showError(backendError("Employee registration check",e));});
    }
    private void attachSms(boolean force,int id){
        PhoneAuthOptions.Builder builder=PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance()).setPhoneNumber(phone).setTimeout(60L,TimeUnit.SECONDS).setActivity(this)
            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks(){
                @Override public void onVerificationCompleted(PhoneAuthCredential credential){if(!current(id))return;checking=false;sending=false;smsInProgress=false;signIn(credential);}
                @Override public void onVerificationFailed(FirebaseException e){if(!current(id))return;checking=false;sending=false;smsInProgress=false;showError(backendError("Request SMS OTP",e));}
                @Override public void onCodeSent(String value,PhoneAuthProvider.ForceResendingToken resendToken){if(!current(id))return;verificationId=value;token=resendToken;sending=false;checking=false;deadline=0;status.setText("SMS code sent to "+phone+". Enter the 6-digit code. Auto-verification may also complete login.");buttons();}
                @Override public void onCodeAutoRetrievalTimeOut(String value){if(!current(id))return;verificationId=value;smsInProgress=false;sending=false;checking=false;deadline=0;status.setText("Enter the SMS code manually. If it has not arrived, use Resend SMS OTP.");buttons();}
            });
        if(force&&token!=null)builder.setForceResendingToken(token);
        try{PhoneAuthProvider.verifyPhoneNumber(builder.build());}catch(Exception e){checking=false;sending=false;smsInProgress=false;showError(backendError("Request SMS OTP",e));}
    }
    private void signIn(PhoneAuthCredential credential){
        if(verifying||!requireOnline("Verify OTP"))return;hideKeyboard();verifying=true;status.setText("Verifying OTP...");buttons();
        ShiftNestRequests.read(FirebaseAuth.getInstance().signInWithCredential(credential)).addOnSuccessListener(r->{
            if(isFinishing()||isDestroyed())return;status.setText("OTP verified. Loading employee profile...");
            EmployeeSession.complete(this,()->{verifying=false;status.setText("Profile loading failed. Retry verification or request a new code.");buttons();});
        }).addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;verifying=false;showError(backendError("OTP verification",e));});
    }
    private void showError(String message){status.setText(message);buttons();ShiftNestLanguage.dialog(this).setTitle("Phone login").setMessage(message).setPositiveButton("OK",null).show();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(otp==null)return;state.putString("verificationId",verificationId);state.putParcelable("token",token);state.putLong("nextResend",nextResend);state.putBoolean("smsInProgress",smsInProgress);state.putLong("deadline",deadline);state.putString("otp",otp.getText().toString());}
    @Override protected void onDestroy(){generation++;handler.removeCallbacks(tick);super.onDestroy();}
}
