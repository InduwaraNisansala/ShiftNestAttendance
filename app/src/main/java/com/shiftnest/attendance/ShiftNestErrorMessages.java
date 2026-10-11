package com.shiftnest.attendance;

/** User messages are selected locally; arbitrary server exception text is never displayed. */
public final class ShiftNestErrorMessages {
    private ShiftNestErrorMessages() {}
    private static final java.util.Set<String> BUSINESS = new java.util.HashSet<>(java.util.Arrays.asList(
        "Accurate GPS is required (50 metres or better). Enable precise location and retry.",
        "An open check-in is required. Refresh attendance.",
        "App verification failed. Use the official app on a verified phone.",
        "Ask admin to configure a valid assigned work site.",
        "Attendance is blocked after a fake GPS report. Ask admin for company approval.",
        "Date changed. Refresh attendance and retry.",
        "Enter ",
        "Enter location (1-120 characters).",
        "Enter site name (1-120 characters).",
        "GPS does not confirm you are inside the assigned site. Move inside, get a precise fix, or contact admin.",
        "GPS reading is stale. Get a new location and retry.",
        "Invalid attendance request. Reopen attendance and retry.",
        "Real GPS is required. Mock or unverified readings are blocked.",
        "Request ID belongs to another attendance request.",
        "This app is not approved for attendance. Contact the project owner.",
        "Unexpected attendance fields. Use the official app.",
        "You already checked in today. Refresh attendance.",
        "You have approved leave today.",
        "Your employee account is unavailable.",
        "Active employee not found.",
        "Admin access required.",
        "Admin login required.",
        "An active, unique employee record is required.",
        "Annual leave quota is not configured for this year.",
        "Assigned site does not exist.",
        "Attendance is already recorded for this day.",
        "Choose a decision and enter the Chairman decision note.",
        "Choose a valid assigned site.",
        "Choose a work location type.",
        "Choose an assigned site.",
        "Company holiday calendar is not confirmed for this date.",
        "Employee access changed.",
        "Employee department changed. Reject this request and submit a new one.",
        "Employee is inactive.",
        "Employee is no longer eligible.",
        "Employee not found.",
        "Enter a valid Sri Lankan phone number.",
        "Enter a valid annual allowance, opening used days and year.",
        "Enter a valid annual allowance, used days and year.",
        "Enter valid employee details.",
        "Invalid admin login.",
        "Invalid employee ID.",
        "Invalid location report source.",
        "Invalid notification token.",
        "Invalid username.",
        "Leave cannot be taken on a company holiday.",
        "Login required.",
        "No annual leave balance available.",
        "Office/Hotel leave quota is awaiting company confirmation for this year.",
        "Past dates cannot be booked.",
        "Reject or decide pending leave requests before changing department.",
        "Select a valid date and enter a reason (up to 500 characters).",
        "Select a valid leave date.",
        "Select an employee and enter an approval reason (3–500 characters).",
        "Sign in with your employee phone number.",
        "The date reservation does not match this request.",
        "The employee no longer has sufficient earned leave.",
        "This day is already booked.",
        "Link your admin account to your employee record first.",
        "Your linked employee record is unavailable.",
        "Add one active employee record with your phone number first.",
        "Your admin account is already linked. Contact the company administrator to change it.",
        "This employee record is linked to another administrator.",
        "Another administrator must record the Chairman decision for your own leave.",
        "Decide pending leave requests before removing this employee.",
        "Select a department.",
        "Select today or a future date.",
        "Enter a reason (up to 500 characters).",
        "Select valid check-in and check-out times.",
        "Check-out must be later than check-in on the same day.",
        "Refresh before sending a changed notice.",
        "This phone number is already registered to another employee.",
        "This phone number is already registered.",
        "This phone number is not registered as an active employee.",
        "This request has already been decided."
    ));
    public static String message(String code, String detail) {
        if (detail != null && BUSINESS.contains(detail)) return detail;
        if (detail != null && detail.matches("Complete [0-9]{1,3} more work day\\(s\\) to earn your next leave\\.")) return detail;
        if (detail != null && detail.startsWith("Leave cannot be taken on a holiday: ")) return "Leave cannot be taken on a holiday.";
        switch (code == null ? "" : code) {
            case "NETWORK": case "UNAVAILABLE": return "Connection unavailable. Check internet and try again.";
            case "DEADLINE_EXCEEDED": return "Request timed out. Refresh before trying again.";
            case "UNAUTHENTICATED": return "Please sign in again.";
            case "PERMISSION_DENIED": return "Access unavailable. Contact your administrator.";
            case "NOT_FOUND": return "Service or record unavailable. Contact your administrator.";
            case "ALREADY_EXISTS": return "This record already exists. Check before retrying.";
            case "INVALID_ARGUMENT": return "Check the entered details and try again.";
            case "FAILED_PRECONDITION": return "This action is unavailable. Refresh or contact your administrator.";
            case "RESOURCE_EXHAUSTED": return "Too many requests. Please try again later.";
            case "ERROR_INVALID_VERIFICATION_CODE": return "Incorrect OTP. Check the code and try again.";
            case "ERROR_SESSION_EXPIRED": return "OTP expired. Request a new code.";
            case "ERROR_INVALID_CREDENTIAL": case "ERROR_WRONG_PASSWORD": case "ERROR_USER_NOT_FOUND": return "Login failed. Check your details and try again.";
            case "ERROR_INVALID_EMAIL": return "Enter a valid email address.";
            case "ERROR_USER_DISABLED": return "Account disabled. Contact your administrator.";
            case "ERROR_TOO_MANY_REQUESTS": return "Too many attempts. Please try again later.";
            case "ERROR_INVALID_PHONE_NUMBER": return "Enter a valid phone number.";
            default: return "Could not complete this action. Try again or contact your administrator.";
        }
    }
}
