package com.shiftnest.attendance;
/** Only numeric loopback/emulator/private LAN IPv4 addresses are accepted for the demo. */
public final class ShiftNestDemoHost {
    private ShiftNestDemoHost(){}
    public static boolean valid(String host){
        if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        String[] parts=host.split("\\.");int[] n=new int[4];
        for(int i=0;i<4;i++){if(parts[i].length()>1&&parts[i].startsWith("0"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}
        if(n[3]==0||n[3]==255)return false;
        return n[0]==10 || n[0]==127 || (n[0]==192&&n[1]==168) || (n[0]==172&&n[1]>=16&&n[1]<=31);
    }
}
