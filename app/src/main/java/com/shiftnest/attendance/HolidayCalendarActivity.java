package com.shiftnest.attendance;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.firestore.*;
import java.time.LocalDate;
import java.util.*;

public class HolidayCalendarActivity extends ShiftNestBaseActivity {
    private String day=ShiftNestData.date(),from="",to="";
    private EditText name;
    private TextView coverage,list;
    private Button start,end,saveRange,selectedDay,block,remove,refresh;
    private boolean saving,policyLoaded;
    private int generation;
    private Bundle draft;
    private final List<CheckBox> weekdays=new ArrayList<>();
    private final FirebaseFirestore db=FirebaseFirestore.getInstance();
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;page("Sales Holiday Calendar");
        coverage=text("Loading confirmed coverage...",16);screen.addView(coverage);
        start=button("Coverage start: not set",()->pick(true));end=button("Coverage end: not set",()->pick(false));screen.addView(start);screen.addView(end);
        screen.addView(text("Company weekly holidays (tick only confirmed off-days)",16));
        for(String label:new String[]{"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"}){CheckBox b=new CheckBox(this);b.setText(label);b.setTextColor(color(R.color.shiftnest_text));weekdays.add(b);screen.addView(b);}
        saveRange=button("Confirm holiday calendar coverage",this::confirm);screen.addView(saveRange);
        screen.addView(text("Holiday leave restrictions apply to Sales Officers only.",16));screen.addView(text("Add every public, Poya and company holiday before confirming coverage.",16));
        selectedDay=button("Holiday date: "+day,()->chooseDate("holiday_day","Choose holiday date",LocalDate.parse(day),d->{day=d.toString();selectedDay.setText("Holiday date: "+day);}));screen.addView(selectedDay);
        name=new EditText(this);name.setHint("Holiday name, e.g. Poya holiday");name.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});screen.addView(name);
        block=button("Block selected date as holiday",()->holiday(true));remove=button("Remove selected holiday",()->holiday(false));screen.addView(block);screen.addView(remove);
        list=text("Loading holidays...",15);screen.addView(list);refresh=button("Refresh holiday calendar",this::load);screen.addView(refresh);
        if(state!=null){day=state.getString("day",day);name.setText(state.getString("name",""));selectedDay.setText("Holiday date: "+day);}
        bindDateChoice("holiday_start",d->{from=d.toString();start.setText("Coverage start: "+from);});bindDateChoice("holiday_end",d->{to=d.toString();end.setText("Coverage end: "+to);});bindDateChoice("holiday_day",d->{day=d.toString();selectedDay.setText("Holiday date: "+day);});
        draft=state;load();
    }
    private void controls(){
        saveRange.setEnabled(!saving&&policyLoaded);start.setEnabled(!saving&&policyLoaded);end.setEnabled(!saving&&policyLoaded);
        for(CheckBox b:weekdays)b.setEnabled(!saving&&policyLoaded);
        for(Button b:new Button[]{selectedDay,block,remove,refresh})b.setEnabled(!saving);
        name.setEnabled(!saving);saveRange.setText(saving?"Saving holiday calendar...":"Confirm holiday calendar coverage");
    }
    private void load(){
        if(saving)return;final int run=++generation;policyLoaded=false;controls();coverage.setText("Loading holiday policy...");list.setText("Loading holidays...");
        ShiftNestRequests.read(db.collection("settings").document("leavePolicy").get(Source.SERVER)).addOnSuccessListener(d->{
            if(run!=generation||isFinishing()||isDestroyed())return;
            from=ShiftNestData.string(d,"holidayCoverageStart");to=ShiftNestData.string(d,"holidayCoverageEnd");start.setText("Coverage start: "+(from.isEmpty()?"not set":from));end.setText("Coverage end: "+(to.isEmpty()?"not set":to));coverage.setText("Confirmed range: "+(from.isEmpty()?"not configured":from+" to "+to));
            for(CheckBox b:weekdays)b.setChecked(false);
            Object days=d.get("blockedWeekdays");if(days instanceof List)for(Object value:(List<?>)days)if(value instanceof Number){int i=((Number)value).intValue();if(i>=0&&i<7)weekdays.get(i).setChecked(true);}
            if(draft!=null&&draft.containsKey("draftFrom")){from=draft.getString("draftFrom",from);to=draft.getString("draftTo",to);start.setText("Coverage start: "+(from.isEmpty()?"not set":from));end.setText("Coverage end: "+(to.isEmpty()?"not set":to));for(int i=0;i<7;i++)weekdays.get(i).setChecked(draft.getBoolean("weekday"+i));draft=null;}
            policyLoaded=true;controls();
        }).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;coverage.setText(backendError("Load holiday policy",e)+"\nTap Refresh holiday calendar.");controls();});
        ShiftNestRequests.read(db.collection("holidays").get(Source.SERVER)).addOnSuccessListener(q->{
            if(run!=generation||isFinishing()||isDestroyed())return;
            StringBuilder body=new StringBuilder("Blocked holidays:\n");List<DocumentSnapshot> docs=q.getDocuments();docs.sort(Comparator.comparing(DocumentSnapshot::getId));int count=0;
            for(DocumentSnapshot d:docs)if(!Boolean.FALSE.equals(d.get("blocked"))){count++;body.append(d.getId()).append(" • ").append(ShiftNestData.string(d,"name")).append('\n');}
            if(count==0)body.append("No holidays saved yet.");list.setText(body.toString());
        }).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;list.setText(backendError("Load holidays",e)+"\nTap Refresh holiday calendar.");});
    }
    private void pick(boolean first){String value=first?from:to;chooseDate(first?"holiday_start":"holiday_end",first?"Holiday coverage: From":"Holiday coverage: To",LocalDate.parse(value.isEmpty()?ShiftNestData.date():value),d->{if(first){from=d.toString();start.setText("Coverage start: "+from);}else{to=d.toString();end.setText("Coverage end: "+to);}});}
    private void confirm(){
        if(saving)return;if(!policyLoaded){problem("Load the holiday policy before changing coverage. Tap Refresh.");return;}
        if(from.isEmpty()||to.isEmpty()||from.compareTo(to)>0){problem("Choose both coverage dates. From must be on or before To.");return;}
        ShiftNestLanguage.dialog(this).setTitle("Confirm complete holiday calendar").setMessage("Have you entered ALL public, Poya and company holidays from "+from+" to "+to+"? Leave becomes available for unblocked dates in this range.").setNegativeButton("Review first",null).setPositiveButton("Confirm",(v,n)->{
            if(saving||!requireOnline("Save holiday coverage"))return;
            Map<String,Object> data=new HashMap<>();data.put("holidayCoverageStart",from);data.put("holidayCoverageEnd",to);List<Integer> blocked=new ArrayList<>();for(int i=0;i<7;i++)if(weekdays.get(i).isChecked())blocked.add(i);data.put("blockedWeekdays",blocked);
            saving=true;controls();ShiftNestRequests.write(db.collection("settings").document("leavePolicy").set(data,SetOptions.merge())).addOnSuccessListener(a->saved("Holiday calendar coverage saved successfully.")).addOnFailureListener(e->failed("Save holiday coverage",e));
        }).show();
    }
    private void holiday(boolean blocked){
        if(saving)return;if(blocked&&name.getText().toString().trim().isEmpty()){validationError(name,"Enter holiday name");return;}
        final String date=day;
        if(blocked){if(!requireOnline("Save holiday"))return;Map<String,Object> data=new HashMap<>();data.put("name",name.getText().toString().trim());data.put("blocked",true);saving=true;controls();ShiftNestRequests.write(db.collection("holidays").document(date).set(data)).addOnSuccessListener(v->saved("Holiday saved successfully: "+date)).addOnFailureListener(e->failed("Save holiday",e));}
        else ShiftNestLanguage.dialog(this).setTitle("Remove holiday").setMessage("Remove holiday on "+date+"?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(v,n)->{if(saving||!requireOnline("Delete holiday"))return;saving=true;controls();ShiftNestRequests.write(db.collection("holidays").document(date).delete()).addOnSuccessListener(x->saved("Holiday removed successfully: "+date)).addOnFailureListener(e->failed("Delete holiday",e));}).show();
    }
    private void saved(String message){if(isFinishing()||isDestroyed())return;saving=false;controls();success(message);load();}
    private void failed(String action,Exception e){if(isFinishing()||isDestroyed())return;saving=false;controls();problem(backendError(action,e));}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("day",day);state.putString("name",name.getText().toString());if(policyLoaded){state.putString("draftFrom",from);state.putString("draftTo",to);for(int i=0;i<7;i++)state.putBoolean("weekday"+i,weekdays.get(i).isChecked());}}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
