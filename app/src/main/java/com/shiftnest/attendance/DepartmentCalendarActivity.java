package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;

public class DepartmentCalendarActivity extends ShiftNestBaseActivity {
    private String department="Sales",day=ShiftNestData.date(),start="09:00",end="17:00",draftKey="",requestId="";
    private boolean saving;
    private int generation;
    private EditText reason;
    private RadioButton holiday,working;
    private Button date,in,out,send;
    private TextView hours,history;
    private final List<Button> controls=new ArrayList<>();
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;page("Department Calendar");
        if(state!=null){department=state.getString("department",department);day=state.getString("day",day);start=state.getString("start",start);end=state.getString("end",end);draftKey=state.getString("draftKey","");requestId=state.getString("requestId","");}
        screen.addView(text("Choose the department receiving this calendar update.",16));
        String[] departments={"Sales","Office","Hotel"},labels={"Sales Officers","Office Staff","Hotel Staff"};
        for(int i=0;i<3;i++){final String dep=departments[i];Button b=button(labels[i],()->{department=dep;highlight();load();});controls.add(b);screen.addView(b);}
        date=button("Date: "+day,()->chooseDate("department_day","Select schedule date",LocalDate.parse(day),d->{day=d.toString();date.setText("Date: "+day);}));controls.add(date);screen.addView(date);
        RadioGroup kinds=new RadioGroup(this);holiday=new RadioButton(this);working=new RadioButton(this);holiday.setId(android.view.View.generateViewId());working.setId(android.view.View.generateViewId());holiday.setText("Holiday");working.setText("Working day");holiday.setTextColor(color(R.color.shiftnest_text));working.setTextColor(color(R.color.shiftnest_text));kinds.addView(holiday);kinds.addView(working);screen.addView(kinds);
        reason=new EditText(this);reason.setHint("Reason / employee message");reason.setMinLines(2);reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});screen.addView(reason);
        if(state!=null)reason.setText(state.getString("reason",""));
        in=button("Check In: "+start,()->time(true));out=button("Check Out: "+end,()->time(false));controls.add(in);controls.add(out);screen.addView(in);screen.addView(out);hours=text("",16);screen.addView(hours);
        send=button("Save calendar & notify department",this::publish);screen.addView(send);
        screen.addView(text("Notices announce schedules; they do not automatically check employees in or out. Holiday leave restrictions apply to Sales Officers only.",15));
        screen.addView(button("Sales holiday calendar",()->open(HolidayCalendarActivity.class)));
        history=text("Loading department calendar...",15);screen.addView(history);Button reload=button("Refresh department calendar",this::load);controls.add(reload);screen.addView(reload);
        kinds.setOnCheckedChangeListener((group,id)->highlight());if(state!=null&&state.getBoolean("working",false))working.setChecked(true);else holiday.setChecked(true);highlight();load();
    }
    private void highlight(){
        for(int i=0;i<3;i++)controls.get(i).setAlpha(new String[]{"Sales","Office","Hotel"}[i].equals(department)?1f:0.6f);
        for(Button b:controls)b.setEnabled(!saving);holiday.setEnabled(!saving);working.setEnabled(!saving);reason.setEnabled(!saving);send.setEnabled(!saving);
        in.setEnabled(!saving&&working.isChecked());out.setEnabled(!saving&&working.isChecked());
        int minutes=minutes(end)-minutes(start);hours.setText(holiday.isChecked()?"Holiday — no work scheduled":minutes>0?"Working hours: "+minutes/60+" h "+minutes%60+" min":"Check-out must be later than check-in on the same day.");send.setText(saving?"Saving...":"Save calendar & notify department");
    }
    private int minutes(String time){return Integer.parseInt(time.substring(0,2))*60+Integer.parseInt(time.substring(3));}
    private void time(boolean first){hideKeyboard();String value=first?start:end;new android.app.TimePickerDialog(this,(view,h,m)->{String t=String.format(Locale.ROOT,"%02d:%02d",h,m);if(first){start=t;in.setText("Check In: "+t);}else{end=t;out.setText("Check Out: "+t);}highlight();},minutes(value)/60,minutes(value)%60,true).show();}
    private void load(){if(saving)return;final int run=++generation;history.setText("Loading department calendar...");
        ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("departmentCalendars").document(department).collection("days").whereGreaterThanOrEqualTo("date",LocalDate.parse(ShiftNestData.date()).minusMonths(2).toString()).get(Source.SERVER)).addOnSuccessListener(q->{if(run!=generation||isFinishing()||isDestroyed())return;List<DocumentSnapshot> docs=new ArrayList<>(q.getDocuments());docs.sort(Comparator.comparing(DocumentSnapshot::getId));StringBuilder body=new StringBuilder(ShiftNestLanguage.text(this,"Saved department calendar:")+"\n");for(DocumentSnapshot d:docs){body.append(d.getId()).append(" • ").append(ShiftNestLanguage.text(this,"holiday".equals(d.getString("kind"))?"Holiday":"Working day")).append('\n').append(ShiftNestData.string(d,"reason")).append('\n');if("working".equals(d.getString("kind")))body.append(ShiftNestData.string(d,"checkIn")).append(" – ").append(ShiftNestData.string(d,"checkOut")).append('\n');}if(docs.isEmpty())body.append(ShiftNestLanguage.text(this,"No matching records."));history.setTag("shiftnest_data");history.setText(body.toString());}).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;history.setText(backendError("Load department calendar",e));});
    }
    private void publish(){if(saving)return;String message=reason.getText().toString().trim();if(LocalDate.parse(day).isBefore(LocalDate.parse(ShiftNestData.date()))){problem("Select today or a future date.");return;}if(message.isEmpty()){validationError(reason,"Enter a reason (up to 500 characters).");return;}if(working.isChecked()&&minutes(end)<=minutes(start)){problem("Check-out must be later than check-in on the same day.");return;}if(!requireOnline("Send department notice"))return;
        String key=department+"|"+day+"|"+holiday.isChecked()+"|"+start+"|"+end+"|"+message;if(!key.equals(draftKey)||requestId.isEmpty()){draftKey=key;requestId=UUID.randomUUID().toString();}
        Map<String,Object> data=new HashMap<>();data.put("id",requestId);data.put("department",department);data.put("date",day);data.put("kind",holiday.isChecked()?"holiday":"working");data.put("reason",message);data.put("checkIn",start);data.put("checkOut",end);saving=true;hideKeyboard();highlight();
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("publishDepartmentNotice").withTimeout(45,java.util.concurrent.TimeUnit.SECONDS).call(data).addOnSuccessListener(r->{if(isFinishing()||isDestroyed())return;saving=false;highlight();success("Calendar saved. Department notification queued.");load();}).addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;saving=false;highlight();problem(backendError("Send department notice",e));});
    }
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("department",department);s.putString("day",day);s.putString("start",start);s.putString("end",end);s.putString("draftKey",draftKey);s.putString("requestId",requestId);s.putString("reason",reason.getText().toString());s.putBoolean("working",working.isChecked());}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
