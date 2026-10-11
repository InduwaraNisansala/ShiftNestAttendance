package com.shiftnest.attendance;
import com.google.firebase.firestore.DocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.*;
public final class ShiftNestData {
    private ShiftNestData(){}
    public static String string(DocumentSnapshot d,String key){Object v=d.get(key);return v==null?"":String.valueOf(v);}
    public static String department(String role){String s=role.toLowerCase(Locale.US);return s.contains("sales")?"Sales":s.contains("hotel")?"Hotel":s.contains("office")||s.contains("staff")?"Office":"Other";}
    public static String normalizePhone(String raw){String p=raw.replaceAll("[\\s()-]", "");if(p.matches("0[0-9]{9}"))p="+94"+p.substring(1);else if(p.matches("94[0-9]{9}"))p="+"+p;else if(p.matches("[0-9]{9}"))p="+94"+p;return p.matches("\\+94[0-9]{9}")?p:"";}
    public static String date(){return format(new Date(),"yyyy-MM-dd");}
    public static String format(Date date,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Colombo"));return f.format(date);}
    public static Date timestamp(DocumentSnapshot d,String key){Object t=d.get(key);return t instanceof com.google.firebase.Timestamp?((com.google.firebase.Timestamp)t).toDate():t instanceof Date?(Date)t:null;}
    public static String locationStatus(DocumentSnapshot d){if(d==null)return "Location unavailable";Date t=timestamp(d,"timestamp");if(t==null || System.currentTimeMillis()-t.getTime()>300000 || t.getTime()>System.currentTimeMillis()+60000)return "Location stale";Date fix=timestamp(d,"fixTimestamp");if(fix!=null&&(System.currentTimeMillis()-fix.getTime()>300000||fix.getTime()>System.currentTimeMillis()+60000))return "Location stale";if(!"Working".equalsIgnoreCase(string(d,"trackingStatus")))return "Not tracking";if("unavailable".equals(string(d,"siteCheckStatus")))return "Location check unavailable";Object inside=d.get("insideSite");if(Boolean.FALSE.equals(inside)||"outside".equalsIgnoreCase(String.valueOf(inside)))return "OUTSIDE LOCATION";if(Boolean.TRUE.equals(inside)||"inside".equalsIgnoreCase(String.valueOf(inside)))return "Inside location";return "No fixed location";}
}
