package com.shiftnest.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.HashMap;
import java.util.Map;

public class AdminLoginActivity extends ShiftNestBaseActivity {
    private EditText username, password;
    private Button login;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempt = 0;
    private boolean busy = false;
    private Runnable timeout;
    private String stage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (AdminSession.restore(this)) return;
        setContentView(R.layout.activity_admin_login);
        username = findViewById(R.id.etAdminUsername);
        password = findViewById(R.id.etAdminPassword);
        login = findViewById(R.id.btnAdminLogin);
        login.setOnClickListener(v -> signIn());
    }

    private boolean current(int id) {
        return busy && id == attempt && !isFinishing() && !isDestroyed();
    }

    private void signIn() {
        if (busy) return;
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        if (u.isEmpty()) { validationError(username,"Enter email or username"); return; }
        if (p.isEmpty()) { validationError(password,"Enter password"); return; }
        if (!requireOnline("Admin login")) return;
        hideKeyboard();
        busy = true;
        int id = ++attempt;
        login.setEnabled(false);
        stage = "Checking login";
        login.setText("CHECKING LOGIN...");
        timeout = () -> {
            if (current(id)) fail(id, stage + " timed out",
                    BuildConfig.DEMO_BACKEND ? "Cannot reach the PC demo backend. Keep START-DEMO.bat backend running and check the PC address." : "Login timed out. Check internet and try again.");
        };
        handler.postDelayed(timeout, 30000);
        if (u.contains("@")) {
            authenticate(u, p, id);
        } else {
            stage = "Finding admin username";
            Map<String, Object> data = new HashMap<>();
            data.put("username", u);
            FirebaseFunctions.getInstance("asia-south1")
                    .getHttpsCallable("resolveAdminUsername").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data)
                    .addOnSuccessListener(r -> {
                        if (!current(id)) return;
                        Object raw = r.getData();
                        Object email = raw instanceof Map ? ((Map<?, ?>) raw).get("email") : null;
                        if (!(email instanceof String) || ((String) email).isEmpty()) {
                            fail(id, "Username lookup failed", "No email was returned for this username.");
                            return;
                        }
                        authenticate((String) email, p, id);
                    })
                    .addOnFailureListener(e -> error(id, "Username lookup failed", e));
        }
    }

    private void authenticate(String email, String p, int id) {
        stage = "Checking email and password";
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email, p)
                .addOnSuccessListener(r -> {
                    if (!current(id)) return;
                    if (r.getUser() == null) {
                        fail(id, "Login failed", "Please try signing in again.");
                        return;
                    }
                    stage = "Checking admin access";
                    login.setText("CHECKING ADMIN ACCESS...");
                    String uid = r.getUser().getUid();
                    ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("admins").document(uid)
                            .get(Source.SERVER))
                            .addOnSuccessListener(d -> {
                                if (!current(id)) return;
                                if (!d.exists()) {
                                    auth.signOut();
                                    fail(id, "Admin record missing", "Admin access is not enabled. Contact your administrator.");
                                    return;
                                }
                                if (!Boolean.TRUE.equals(d.get("active"))) {
                                    auth.signOut();
                                    fail(id, "Admin access disabled", "Admin access is disabled. Contact your administrator.");
                                    return;
                                }
                                reset();
                                getSharedPreferences("ShiftNestAttendancePrefs", MODE_PRIVATE).edit()
                                        .clear().putString("sessionRole", "admin").putString("adminUid", uid)
                                        .putBoolean("adminLoggedIn", true).apply();
                                Intent intent = new Intent(this, AdminDashboardActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                if (!current(id)) return;
                                auth.signOut();
                                error(id, "Admin access check failed", e);
                            });
                })
                .addOnFailureListener(e -> error(id, "Email/password login failed", e));
    }

    private void error(int id, String title, Exception e) {
        if (!current(id)) return;
        
        fail(id, title, backendError(title, e));
    }

    private void fail(int id, String title, String message) {
        if (!current(id)) return;
        reset();
        ShiftNestLanguage.dialog(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void reset() {
        busy = false;
        attempt++;
        if (timeout != null) handler.removeCallbacks(timeout);
        login.setEnabled(true);
        login.setText("LOGIN");
    }

    @Override
    protected void onDestroy() {
        busy = false;
        attempt++;
        if (timeout != null) handler.removeCallbacks(timeout);
        super.onDestroy();
    }
}
