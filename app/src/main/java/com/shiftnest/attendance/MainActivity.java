package com.shiftnest.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends ShiftNestBaseActivity {

    Button btnAdminLogin;
    Button btnEmployeeLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnAdminLogin = findViewById(R.id.btnAdminLogin);
        btnEmployeeLogin = findViewById(R.id.btnEmployeeLogin);

        btnAdminLogin.setOnClickListener(v -> {

            Intent intent = new Intent(
                    MainActivity.this,
                    AdminLoginActivity.class
            );

            startActivity(intent);
        });

        btnEmployeeLogin.setOnClickListener(v -> {

            Intent intent = new Intent(
                    MainActivity.this,
                    EmployeeLoginActivity.class
            );

            startActivity(intent);
        });
    }
}