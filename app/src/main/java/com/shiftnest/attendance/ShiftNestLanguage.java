package com.shiftnest.attendance;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

public final class ShiftNestLanguage {
    private ShiftNestLanguage() {}
    private static ShiftNestTranslations translations;
    public static String selected(Context c){return c.getSharedPreferences("ShiftNestLanguage",0).getString("language","en");}
    public static Context context(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(new Locale(selected(c)));return c.createConfigurationContext(config);}
    public static void save(Context c,String value){if(!"si".equals(value)&&!"en".equals(value))return;c.getSharedPreferences("ShiftNestLanguage",0).edit().putString("language",value).putBoolean("showSaved",true).apply();}
    private static synchronized ShiftNestTranslations dictionary(Context c){
        if(translations==null)try(java.io.Reader r=new java.io.InputStreamReader(c.getAssets().open("shiftnest-si.tsv"),java.nio.charset.StandardCharsets.UTF_8)){translations=new ShiftNestTranslations(r);}catch(java.io.IOException e){return null;}
        return translations;
    }
    public static String text(Context c,CharSequence value){String s=value==null?"":value.toString();if(!"si".equals(selected(c)))return s;ShiftNestTranslations d=dictionary(c);return d==null?s:d.text(s);}
    public static String notification(Context c,String type,String value){if(!"si".equals(selected(c)))return value;ShiftNestTranslations d=dictionary(c);return d==null?value:d.notification(type,value);}
    public static void views(Context c,View root){
        if(!"si".equals(selected(c)))return;
        if(root==null||"shiftnest_data".equals(root.getTag()))return;
        if(root instanceof EditText){EditText field=(EditText)root;String hint=text(c,field.getHint());if(!hint.contentEquals(field.getHint()==null?"":field.getHint()))field.setHint(hint);return;}
        if(root instanceof TextView){TextView field=(TextView)root;String translated=text(c,field.getText());if(!translated.contentEquals(field.getText()))field.setText(translated);if(root instanceof Button)field.setAllCaps(false);}
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)views(c,group.getChildAt(i));}
    }
    public static androidx.appcompat.app.AlertDialog.Builder dialog(Context c){return new androidx.appcompat.app.AlertDialog.Builder(c){
        @Override public androidx.appcompat.app.AlertDialog.Builder setTitle(CharSequence title){return super.setTitle(text(c,title));}
        @Override public androidx.appcompat.app.AlertDialog.Builder setMessage(CharSequence message){return super.setMessage(text(c,message));}
        @Override public androidx.appcompat.app.AlertDialog.Builder setPositiveButton(CharSequence label,android.content.DialogInterface.OnClickListener action){return super.setPositiveButton(text(c,label),action);}
        @Override public androidx.appcompat.app.AlertDialog.Builder setNegativeButton(CharSequence label,android.content.DialogInterface.OnClickListener action){return super.setNegativeButton(text(c,label),action);}
        @Override public androidx.appcompat.app.AlertDialog.Builder setNeutralButton(CharSequence label,android.content.DialogInterface.OnClickListener action){return super.setNeutralButton(text(c,label),action);}
    };}
}
