package com.shiftnest.attendance;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
public class ShiftNestMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){ShiftNestNotifications.register(this,token);}
    @Override public void onMessageReceived(RemoteMessage message){ShiftNestNotifications.show(this,message.getData());}
}
