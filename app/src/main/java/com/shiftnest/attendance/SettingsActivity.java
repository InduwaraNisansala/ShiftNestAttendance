package com.shiftnest.attendance;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDelegate;
public class SettingsActivity extends ShiftNestBaseActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);page("App Settings");
        if(BuildConfig.DEMO_BACKEND)screen.addView(button("Demo backend connection",()->open(DemoConnectionActivity.class)));
        screen.addView(text("Language",22));
        screen.addView(text("si".equals(ShiftNestLanguage.selected(this))?"Current language: Sinhala":"Current language: English",16));
        screen.addView(button("සිංහල",()->language("si")));
        screen.addView(button("English",()->language("en")));
        screen.addView(text("Appearance",22));
        screen.addView(button("Dark • Navy & Gold",()->mode(AppCompatDelegate.MODE_NIGHT_YES)));
        screen.addView(button("Light • White & Navy",()->mode(AppCompatDelegate.MODE_NIGHT_NO)));
        screen.addView(button("Follow phone setting",()->mode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)));
        screen.addView(button("Phone notification settings",this::notificationSettings));
        screen.addView(button("Retry notification registration",()->{getSharedPreferences("ShiftNestNotificationState",MODE_PRIVATE).edit().clear().apply();ShiftNestNotifications.register(this,(ok,message)->{if(isFinishing()||isDestroyed())return;if(ok)success(message);else problem(message);});}));
        screen.addView(text("ShiftNest Attendance 1.3.21\nOffice / Hotel annual leave: configured per employee by admin.\nUnconfirmed quota / Casual / Sick leave: #",16));
        if(getSharedPreferences("ShiftNestLanguage",MODE_PRIVATE).getBoolean("showSaved",false)){
            getSharedPreferences("ShiftNestLanguage",MODE_PRIVATE).edit().remove("showSaved").apply();success("Language preference saved.");
        }
        if(getSharedPreferences("ShiftNestAppearance",MODE_PRIVATE).getBoolean("showSaved",false)){
            getSharedPreferences("ShiftNestAppearance",MODE_PRIVATE).edit().remove("showSaved").apply();success("Appearance preference saved successfully.");
        }
    }
    private void language(String value){
        if(value.equals(ShiftNestLanguage.selected(this))){success("Language preference saved.");return;}
        ShiftNestLanguage.save(this,value);recreate();
    }
    private void mode(int value){
        int old=AppCompatDelegate.getDefaultNightMode();
        getSharedPreferences("ShiftNestAppearance",MODE_PRIVATE).edit().putInt("mode",value).putBoolean("showSaved",old!=value).apply();
        if(old==value){success("Appearance preference saved successfully.");return;}
        AppCompatDelegate.setDefaultNightMode(value);
    }
}
