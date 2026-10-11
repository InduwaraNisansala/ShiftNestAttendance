import com.shiftnest.attendance.ShiftNestErrorMessages;
public class ErrorMessageCheck {
    static int checks;
    static void expect(String code,String detail,String expected){
        String actual=ShiftNestErrorMessages.message(code,detail);
        if(!expected.equals(actual))throw new AssertionError(code+": "+actual);
        checks++;
    }
    public static void main(String[] args){
        String secret="Firebase admins/private-id asia-south1 Details: secret-token";
        for(String code:new String[]{"NOT_FOUND","PERMISSION_DENIED","UNAUTHENTICATED","INVALID_ARGUMENT","FAILED_PRECONDITION","INTERNAL","UNAVAILABLE","DEADLINE_EXCEEDED","ALREADY_EXISTS",""}){
            String text=ShiftNestErrorMessages.message(code,secret);
            if(text.contains("Firebase")||text.contains("private-id")||text.contains("secret-token")||text.contains("Details:"))throw new AssertionError(text);
            checks++;
        }
        expect("NOT_FOUND","NOT_FOUND","Service or record unavailable. Contact your administrator.");
        expect("NOT_FOUND","This phone number is not registered as an active employee.","This phone number is not registered as an active employee.");
        expect("ALREADY_EXISTS","This day is already booked.","This day is already booked.");
        expect("FAILED_PRECONDITION","Complete 9 more work day(s) to earn your next leave.","Complete 9 more work day(s) to earn your next leave.");
        expect("FAILED_PRECONDITION","No annual leave balance available.","No annual leave balance available.");
        expect("FAILED_PRECONDITION","Leave cannot be taken on a holiday: private-data","Leave cannot be taken on a holiday.");
        expect("ERROR_INVALID_VERIFICATION_CODE",secret,"Incorrect OTP. Check the code and try again.");
        expect("ERROR_SESSION_EXPIRED",secret,"OTP expired. Request a new code.");
        expect("ERROR_INVALID_CREDENTIAL",secret,"Login failed. Check your details and try again.");
        expect("ERROR_TOO_MANY_REQUESTS",secret,"Too many attempts. Please try again later.");
        expect("NETWORK",secret,"Connection unavailable. Check internet and try again.");
        expect("DEADLINE_EXCEEDED",secret,"Request timed out. Refresh before trying again.");
        expect(null,null,"Could not complete this action. Try again or contact your administrator.");
        System.out.println(checks+" message safety and business-feedback checks passed.");
    }
}
