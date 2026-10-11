package com.shiftnest.attendance;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** Explicit LAN connection screen for the separate emulator-only demo APK. */
public class DemoConnectionActivity extends AppCompatActivity {
    private EditText host;
    private TextView status;
    private Button connectButton;
    private boolean connecting;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(!BuildConfig.DEMO_BACKEND){finish();return;}
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("ShiftNest Attendance Demo");title.setTextSize(26);body.addView(title);
        TextView help=new TextView(this);help.setText("TEST DATA ONLY - no real SMS or cloud push.\nRun START-DEMO.bat on your PC and wait for READY. Connect this phone and PC to the same trusted Wi-Fi. Enter the PC IPv4 address shown by ipconfig.\nAndroid virtual phone: use 10.0.2.2.\nRead demo/DEMO-SETUP.md first.");body.addView(help);
        host=new EditText(this);host.setHint("PC IPv4 address, e.g. 192.168.1.5");host.setInputType(InputType.TYPE_CLASS_TEXT);host.setSingleLine(true);host.setText(getSharedPreferences("ShiftNestDemoConnection",0).getString("host",""));body.addView(host);
        status=new TextView(this);status.setText("OTP codes appear in the PC terminal or local emulator UI. Test admin: admin@shiftnest.test / ShiftNestDemo123!");body.addView(status);
        Button virtual=new Button(this);virtual.setText("Use virtual phone address: 10.0.2.2");body.addView(virtual);virtual.setOnClickListener(v->{if(connecting)return;host.setText("10.0.2.2");connect();});
        connectButton=new Button(this);connectButton.setText("Connect & open demo login");body.addView(connectButton);connectButton.setOnClickListener(v->connect());
        Button change=new Button(this);change.setText("Save new PC address (restart required)");body.addView(change);change.setOnClickListener(v->{if(connecting)return;String value=value();if(value==null)return;getSharedPreferences("ShiftNestDemoConnection",0).edit().putString("host",value).apply();status.setText("Address saved. Phone Settings > Apps > ShiftNest Attendance Demo > Force stop, then reopen. Log out before changing PC/server; clear demo app data if switching to a different test dataset.");});
    }
    private String value(){String value=host.getText().toString().trim();if(!ShiftNestDemoHost.valid(value)){host.setError("Enter a private LAN IPv4 address. Do not enter http:// or a port.");return null;}return value;}
    private void connect(){if(connecting)return;String value=value();if(value==null)return;
        if(ShiftNestApplication.demoReady()&&!value.equals(ShiftNestApplication.demoHost())){status.setText("Use Save new PC address, then Force stop and reopen to apply it.");return;}
        connecting=true;connectButton.setEnabled(false);status.setText("Checking PC backend connection...");
        new Thread(()->{
            String problem="";
            for(int port:new int[]{9099,8180,5001}){
                try(java.net.Socket socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress(value,port),3000);}
                catch(Exception error){problem="Cannot reach PC demo backend (port "+port+"). Run START-DEMO.bat and wait for READY. Keep the backend window open; check the PC address and private-network firewall.";break;}
            }
            final String message=problem;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;connecting=false;connectButton.setEnabled(true);
                if(!message.isEmpty()){status.setText(message);return;}
                try{ShiftNestApplication.connectDemo(this,value);getSharedPreferences("ShiftNestDemoConnection",0).edit().putString("host",value).apply();startActivity(new Intent(this,EmployeeLoginActivity.class));finish();}
                catch(RuntimeException error){status.setText("Demo connection setup failed. Verify the demoDebug build and PC address; reopen the app.");}
            });
        }).start();
    }
}
