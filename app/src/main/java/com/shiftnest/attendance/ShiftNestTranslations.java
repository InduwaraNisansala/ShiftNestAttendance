package com.shiftnest.attendance;
import java.util.*;
import java.util.regex.*;

/** Translate interface phrases only. Captured names, reasons, numbers and dates are preserved. */
public final class ShiftNestTranslations {
    private final Map<String,String> exact=new HashMap<>();
    private final List<String> prefixes=new ArrayList<>();
    public ShiftNestTranslations(java.io.Reader source) throws java.io.IOException {
        java.io.BufferedReader reader=new java.io.BufferedReader(source);String line;
        while((line=reader.readLine())!=null){int tab=line.indexOf('\t');if(tab<1)continue;
            String key=line.substring(0,tab);exact.put(key.toLowerCase(Locale.ROOT),line.substring(tab+1));
            if(key.endsWith(": ")||key.endsWith(" - ")||key.equals("Welcome, "))prefixes.add(key);
        }
        prefixes.sort((a,b)->Integer.compare(b.length(),a.length()));
    }
    public String text(String value) {
        if(value==null)return "";
        String all=exact.get(value.toLowerCase(Locale.ROOT));if(all!=null)return all;
        String[] lines=value.split("\\n",-1);for(int i=0;i<lines.length;i++)lines[i]=line(lines[i]);return String.join("\n",lines);
    }
    private String line(String value){
        String found=exact.get(value.toLowerCase(Locale.ROOT));if(found!=null)return found;
        if(value.startsWith("• "))return "• "+line(value.substring(2));
        if(value.startsWith("NEW • "))return "නව • "+line(value.substring(6));
        Matcher days=Pattern.compile("Complete ([0-9]{1,3}) more work day\\(s\\) to earn your next leave\\.").matcher(value);
        if(days.matches())return "ඊළඟ නිවාඩුව සඳහා තවත් සම්පූර්ණ වැඩ දින "+days.group(1)+"ක් අවශ්‍යයි.";
        Matcher resend=Pattern.compile("Resend OTP in ([0-9]+)s").matcher(value);
        if(resend.matches())return "තත්පර "+resend.group(1)+"කින් OTP නැවත යවන්න";
        Matcher showing=Pattern.compile("Showing ([0-9]+)–([0-9]+) of ([0-9]+) • Swipe sideways for all columns").matcher(value);
        if(showing.matches())return "මුළු "+showing.group(3)+"න් "+showing.group(1)+"–"+showing.group(2)+" පෙන්වයි • සියලු තීරු බැලීමට පැත්තට අදින්න";
        Matcher matches=Pattern.compile("([0-9]+) of ([0-9]+) matches • Tap to select").matcher(value);
        if(matches.matches())return "මුළු "+matches.group(2)+"න් ගැළපෙන "+matches.group(1)+"ක් • තෝරාගැනීමට තට්ටු කරන්න";
        if(value.startsWith("Annual leave (")||value.startsWith("Annual quota ("))return value.replace("Annual leave (","වාර්ෂික නිවාඩු (").replace("Annual quota (","වාර්ෂික නිවාඩු ප්‍රමාණය (");
        if(value.startsWith("Checking eligibility for "))return "නිවාඩු ලබාගත හැකිදැයි පරීක්ෂා කරමින්: "+value.substring("Checking eligibility for ".length());
        if(value.startsWith("Checking ")&&value.matches("Checking [0-9-]+\\.\\.\\."))return "පරීක්ෂා කරමින්: "+value.substring(9);
        if(value.startsWith("SMS code sent to "))return "SMS කේතය "+phone(value)+" අංකයට යවා ඇත. කේතය ඇතුළත් කරන්න.";
        if(value.startsWith("Sending SMS OTP to "))return "SMS OTP යවමින්: "+phone(value);
        if(value.startsWith("Holiday: ")&&value.endsWith(". Leave is not available."))return "නිවාඩු දිනය: "+value.substring(9,value.length()-". Leave is not available.".length())+". මෙම නිවාඩුව ලබාගත නොහැක.";
        for(String prefix:prefixes)if(value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))){
            String tail=value.substring(prefix.length());
            // Free text remains untouched, including names that resemble interface labels.
            if(prefix.equals("Reason: ")||prefix.equals("Decision note: ")||prefix.equals("Welcome, ")||prefix.equals("Work location: ")||prefix.equals("Selected site: "))return exact.get(prefix.toLowerCase(Locale.ROOT))+tail;
            if(tail.contains(" • ")){String[] pieces=tail.split(" • ",-1);for(int i=1;i<pieces.length;i++)pieces[i]=line(pieces[i]);tail=String.join(" • ",pieces);}
            else if(prefix.equals("Role: ")||prefix.startsWith("Coverage "))tail=line(tail);
            return exact.get(prefix.toLowerCase(Locale.ROOT))+tail;
        }
        return value;
    }
    private String phone(String value){Matcher m=Pattern.compile("\\+?[0-9]{9,12}").matcher(value);return m.find()?m.group():"";}
    public String notification(String type,String body){
        if(body==null)return "";Matcher date=Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}").matcher(body);
        if("leave_approved".equals(type)||"leave_rejected".equals(type)){
            String day=date.find()?date.group():"";
            return day+"\n"+text("leave_approved".equals(type)?"Your leave has been approved by Chairman Sir.":"Your leave has been rejected by Chairman Sir.")+("leave_rejected".equals(type)?" වෙන් කළ නිවාඩුව නැවත ශේෂයට එක් කර ඇත.":"");
        }
        if("leave_pending".equals(type)){
            int marker=body.indexOf(" requested leave on ");
            if(marker>=0&&date.find())return body.substring(0,marker)+" • "+date.group()+" සඳහා නිවාඩුවක් ඉල්ලා ඇත. සභාපතිතුමාගේ අනුමැතිය බලාපොරොත්තුවෙන්.";
        }
        return text(body);
    }
}
