import com.shiftnest.attendance.ShiftNestDemoHost;
public class DemoHostCheck {
 public static void main(String[] args){int n=0;
  for(String x:new String[]{"192.168.1.5","10.0.2.2","127.0.0.1","172.16.1.5","172.31.2.3"}){if(!ShiftNestDemoHost.valid(x))throw new AssertionError(x);n++;}
  for(String x:new String[]{"","google.com","shiftnest-attendance","8.8.8.8","http://192.168.1.5","192.168.1.5:8180","192.168.1.256","192.168.1.0","192.168.1.255","192.168.01.5","172.32.1.5","0.0.0.0","127.0.0.0"}){if(ShiftNestDemoHost.valid(x))throw new AssertionError(x);n++;}
  System.out.println(n+" private-host safety checks passed.");
 }
}
