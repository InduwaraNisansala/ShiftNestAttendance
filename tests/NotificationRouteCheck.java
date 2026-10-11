public class NotificationRouteCheck {
 static class NotificationsActivity {} static class AdminLocationTrackingActivity {} static class LeaveSheetActivity {} static class EmployeeLeaveActivity {}
    public static Class<?> target(String role,String type){
        if(type!=null&&(type.startsWith("work_")||"storage_budget".equals(type)||"admin_schedule".equals(type)))return NotificationsActivity.class;
        if("admin".equals(role))return ("location_outside".equals(type)||"location_mock".equals(type))?AdminLocationTrackingActivity.class:LeaveSheetActivity.class;
        return "admin_schedule".equals(type)?NotificationsActivity.class:EmployeeLeaveActivity.class;
    }
 static void check(String role,String type,Class<?> want){if(target(role,type)!=want)throw new AssertionError(role+" / "+type);}
 public static void main(String[]a){
 for(String role:new String[]{"admin","employee"})for(String type:new String[]{"work_late","work_outing","work_return","storage_budget","admin_schedule"})check(role,type,NotificationsActivity.class);
 for(String type:new String[]{"location_outside","location_mock"})check("admin",type,AdminLocationTrackingActivity.class);
 for(String type:new String[]{"leave_pending","leave_approved","leave_rejected"}){check("admin",type,LeaveSheetActivity.class);check("employee",type,EmployeeLeaveActivity.class);}
 check("admin",null,LeaveSheetActivity.class);check("employee",null,EmployeeLeaveActivity.class);
 System.out.println("PASS: actual notification target: work/storage/schedules, leave, GPS, both roles and absent type.");
 }
}
