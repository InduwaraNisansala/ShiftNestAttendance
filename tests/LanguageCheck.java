import com.shiftnest.attendance.ShiftNestTranslations;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class LanguageCheck {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Check "+checks);}
    public static void main(String[] args)throws Exception{
        ShiftNestTranslations t=new ShiftNestTranslations(new InputStreamReader(new FileInputStream(args[0]),StandardCharsets.UTF_8));
        for(String label:new String[]{"Employee Login","Admin Dashboard","Save Employee","Save Location","pending","Approve","Reject","Download Leave Sheet PDF","Incorrect OTP. Check the code and try again.","From date must be on or before To date.","Language","Leave approved","No notifications yet."})check(!label.equals(t.text(label)));
        check(t.text("From: 2026-10-07").contains("2026-10-07"));
        check(t.text("Completed work days: 20\nEarned: 2 • Approved: 1\nPending: 0 • Available: 1").contains("2 • අනුමත: 1"));
        check(t.text("Reason: Approve").endsWith("Approve"));
        check(t.text("Welcome, Office").endsWith("Office"));
        check(t.text("Decision note: NOT_FOUND").endsWith("NOT_FOUND"));
        check(t.text("Holiday: Poya. Leave is not available.").contains("Poya"));
        check(t.text("Complete 9 more work day(s) to earn your next leave.").contains("9"));
        check(t.text("Resend OTP in 35s").contains("35"));
        check(t.text("Showing 1–50 of 70 • Swipe sideways for all columns").contains("70"));
        check(t.text("3 of 60 matches • Tap to select").contains("60"));
        check(t.text("+94770000001").equals("+94770000001"));
        String approved=t.notification("leave_approved","Your leave on 2026-10-08 has been approved by Chairman Sir.");
        check(approved.contains("2026-10-08")&&approved.contains("අනුමත"));
        String rejected=t.notification("leave_rejected","Your leave on 2026-10-08 has been rejected by Chairman Sir. The reserved leave has been released.");
        check(rejected.contains("2026-10-08")&&rejected.contains("ප්‍රතික්ෂේප")&&rejected.contains("ශේෂයට"));
        String pending=t.notification("leave_pending","Office (Sales) requested leave on 2026-10-08. Waiting for Chairman Sir's approval.");
        check(pending.startsWith("Office (Sales)")&&pending.contains("2026-10-08"));
        check(t.text(t.text("Save Employee")).equals(t.text("Save Employee")));
        check(t.text("Requests: 0").contains("0"));
        System.out.println(checks+" translation/data-preservation checks passed.");
    }
}
