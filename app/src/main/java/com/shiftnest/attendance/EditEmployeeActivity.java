package com.shiftnest.attendance;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.*;

public class EditEmployeeActivity extends ShiftNestBaseActivity {
    private EditText name, phone, number, allowance, used, year;
    private ShiftNestChoiceSpinner role, site;
    private TextView quotaNote,siteNote;
    private boolean sitesReady,formBusy;
    private final Map<EditText,TextView> fieldLabels=new HashMap<>();
    private CheckBox active, fixed;
    private Button save;
    private String employeeId;
    private int loadGeneration;
    private final List<String> siteIds=new ArrayList<>();
    private DocumentSnapshot employee;
    private Bundle draft;
    private final String[] roles={"Sales Officer","Staff Officer","Hotel Officer"};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state); if(isFinishing())return; page("Edit Employee");
        employeeId=getIntent().getStringExtra("employeeId");draft=state;
        if(employeeId==null || employeeId.isEmpty()){toast("Select an employee from Employee List.");finish();return;}
        name=input("Employee name",InputType.TYPE_CLASS_TEXT);
        number=input("Employee number",InputType.TYPE_CLASS_TEXT);
        phone=input("Phone number",InputType.TYPE_CLASS_PHONE);
        screen.addView(text("Department",16));role=new ShiftNestChoiceSpinner(this);
        ArrayAdapter<String> rolesAdapter=new ArrayAdapter<>(this,R.layout.shiftnest_spinner_selected,new String[]{"Sales Officers","Office Staff (Staff Officers)","Hotel Staff"});
        rolesAdapter.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);role.setAdapter(rolesAdapter);styleSpinner(role);screen.addView(role,new LinearLayout.LayoutParams(-1,-2));
        active=new CheckBox(this);active.setText("Active employee");active.setTextColor(color(R.color.shiftnest_text));screen.addView(active);
        fixed=new CheckBox(this);fixed.setText("Assigned fixed work location");fixed.setTextColor(color(R.color.shiftnest_text));screen.addView(fixed);
        site=new ShiftNestChoiceSpinner(this);styleSpinner(site);screen.addView(site,new LinearLayout.LayoutParams(-1,-2));siteNote=text("Loading work locations...",16);screen.addView(siteNote);fixed.setOnCheckedChangeListener((b,on)->siteAvailability());
        quotaNote=text("",16);screen.addView(quotaNote);
        year=input("Allowance year",InputType.TYPE_CLASS_NUMBER);allowance=input("Annual leave allowance",InputType.TYPE_CLASS_NUMBER);used=input("Already taken before using this app",InputType.TYPE_CLASS_NUMBER);
        role.setOnChoiceChangedListener(this::quotaVisibility);
        site.setOnChoiceChangedListener(this::siteAvailability);
        role.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?>p,View v,int i,long id){quotaVisibility();}public void onNothingSelected(AdapterView<?>p){}});
        screen.addView(text("Phone changes require the employee to log in with the new number. Attendance / leave history stays under the same employee ID. Decide pending leave before changing department.",16));
        save=button("Save Employee Changes",this::saveEmployee);save.setEnabled(true);screen.addView(save);
        role.setPrompt("Select employee role");site.setPrompt("Select assigned location");
        screen.addView(button("Retry loading employee / sites",this::loadEmployee));
        loadEmployee();
    }
    private void loadEmployee(){
        if(formBusy){problem("An employee save is still in progress. Wait for its result before reloading.");return;}
        final int run=++loadGeneration;sitesReady=false;siteAvailability();
        save.setEnabled(true);save.setText("Save Employee Changes");feedback("Loading employee details and saved locations...",false);
        siteIds.clear();
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        ShiftNestRequests.read(db.collection("employees").document(employeeId).get(Source.SERVER)).addOnSuccessListener(d->{
            if(run!=loadGeneration||isFinishing()||isDestroyed())return;
            if(!d.exists()){problem("Employee not found");finish();return;}employee=d;
            name.setText(ShiftNestData.string(d,"name"));phone.setText(ShiftNestData.string(d,"phone"));number.setText(ShiftNestData.string(d,"employeeNumber"));
            String dep=ShiftNestData.department(ShiftNestData.string(d,"role"));role.setSelection(dep.equals("Sales")?0:dep.equals("Hotel")?2:1);
            active.setChecked(Boolean.TRUE.equals(d.get("active")));fixed.setChecked("fixed".equals(d.getString("workLocationType")));
            year.setText(d.get("annualLeaveYear")==null?ShiftNestData.date().substring(0,4):String.valueOf(d.get("annualLeaveYear")));
            allowance.setText(ShiftNestData.string(d,"annualLeaveAllowance"));used.setText(ShiftNestData.string(d,"annualLeaveUsedBeforeApp"));
            ShiftNestRequests.read(db.collection("locations").get(Source.SERVER)).addOnSuccessListener(q->{
                if(run!=loadGeneration||isFinishing()||isDestroyed())return;
                List<String> names=new ArrayList<>();names.add("Select assigned site");siteIds.clear();siteIds.add("");
                int selected=0;for(DocumentSnapshot l:q){String title=ShiftNestData.string(l,"name");if(title.trim().isEmpty())continue;siteIds.add(l.getId());names.add(title);if(l.getId().equals(d.getString("locationId")))selected=siteIds.size()-1;}
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,R.layout.shiftnest_spinner_selected,names);adapter.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);site.setAdapter(adapter);site.setSelection(selected);sitesReady=true;restoreDraft();siteAvailability();save.setEnabled(true);save.setText("Save Employee Changes");quotaVisibility();
                if(siteIds.size()==1)problem("No saved locations. Add Location first for fixed-location employees.");
            }).addOnFailureListener(e->{if(run==loadGeneration)loadFailed("Assigned locations",e);});
        }).addOnFailureListener(e->{if(run==loadGeneration)loadFailed("Employee",e);});
    }
    private void restoreDraft(){
        if(draft==null||!draft.containsKey("draftName"))return;
        name.setText(draft.getString("draftName",""));phone.setText(draft.getString("draftPhone",""));number.setText(draft.getString("draftNumber",""));
        year.setText(draft.getString("draftYear",""));allowance.setText(draft.getString("draftAllowance",""));used.setText(draft.getString("draftUsed",""));
        role.setSelection(Math.max(0,Math.min(2,draft.getInt("draftRole",0))));active.setChecked(draft.getBoolean("draftActive"));fixed.setChecked(draft.getBoolean("draftFixed"));
        int index=siteIds.indexOf(draft.getString("draftSite",""));site.setSelection(Math.max(0,index));draft=null;
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("draftName",name.getText().toString());state.putString("draftPhone",phone.getText().toString());state.putString("draftNumber",number.getText().toString());
        state.putString("draftYear",year.getText().toString());state.putString("draftAllowance",allowance.getText().toString());state.putString("draftUsed",used.getText().toString());
        state.putInt("draftRole",role.getSelectedItemPosition());state.putBoolean("draftActive",active.isChecked());state.putBoolean("draftFixed",fixed.isChecked());int i=site.getSelectedItemPosition();state.putString("draftSite",i>=0&&i<siteIds.size()?siteIds.get(i):"");
    }
    private void loadFailed(String stage,Exception error){
        if(isFinishing()||isDestroyed())return;
        sitesReady=false;siteAvailability();save.setEnabled(true);save.setText("Save Employee Changes");feedback(stage+" loading failed. Use Retry loading employee / sites.",true);
        ShiftNestLanguage.dialog(this).setTitle(stage+" loading failed").setMessage(backendError("Load "+stage,error)).setPositiveButton("Retry",(d,w)->loadEmployee()).setNegativeButton("Close",null).show();
    }
    private EditText input(String hint,int type){TextView label=text(hint,16);screen.addView(label);EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);styleEditable(e);e.setBackgroundResource(R.drawable.shiftnest_input);e.setPadding(dp(16),dp(12),dp(16),dp(12));screen.addView(e,new LinearLayout.LayoutParams(-1,-2));fieldLabels.put(e,label);return e;}
    private void siteAvailability(){
        if(site==null||fixed==null||siteNote==null)return;
        site.setEnabled(fixed.isChecked()&&sitesReady&&!formBusy);
        site.setVisibility(fixed.isChecked()?View.VISIBLE:View.GONE);
        siteNote.setText(!fixed.isChecked()?"No Fixed Location: no site selection is required.":!sitesReady?"Work locations are loading/unavailable. Use Retry loading if this does not finish.":site.getSelectedItemPosition()<1?"Search and select an assigned site before saving.":"Selected site: "+site.getSelectedItem());
    }
    private void quotaVisibility(){
        if(year==null||allowance==null||used==null)return;
        boolean sales=role.getSelectedItemPosition()==0;
        for(EditText field:new EditText[]{year,allowance,used}){field.setEnabled(!formBusy);field.setVisibility(sales?View.GONE:View.VISIBLE);TextView label=fieldLabels.get(field);if(label!=null)label.setVisibility(sales?View.GONE:View.VISIBLE);}
        quotaNote.setText(sales?"Sales Officers: 10 completed working days earn 1 leave. Annual allowance is automatic; no manual quota fields are required.":"Office / Hotel: edit the allowance year, annual quota and already-taken leave below. Blank annual allowance remains #.");
    }
    private void savingState(boolean busy){
        formBusy=busy;for(EditText field:new EditText[]{name,phone,number})field.setEnabled(!busy);role.setEnabled(!busy);active.setEnabled(!busy);fixed.setEnabled(!busy);quotaVisibility();siteAvailability();
    }
    private void saveEmployee(){
        if(formBusy){feedback("Employee changes are still being saved. Please wait.",false);return;}if(employee==null){problem("Employee details are not loaded. Use Retry loading employee / sites first.");return;}
        if(fixed.isChecked()&&!sitesReady){problem("Assigned sites are not loaded. Tap Retry loading employee / sites before saving.");return;}
        String title=name.getText().toString().trim(),normalized=ShiftNestData.normalizePhone(phone.getText().toString());
        if(title.isEmpty()){validationError(name,"Enter employee name");return;}if(title.length()>120){validationError(name,"Employee name must be 120 characters or fewer");return;}if(normalized.isEmpty()){validationError(phone,"Enter a valid Sri Lankan phone number");return;}
        Map<String,Object> data=new HashMap<>();data.put("employeeId",employeeId);data.put("name",title);data.put("phone",normalized);data.put("employeeNumber",number.getText().toString().trim());data.put("role",roles[role.getSelectedItemPosition()]);data.put("active",active.isChecked());data.put("workLocationType",fixed.isChecked()?"fixed":"no_fixed");
        int index=site.getSelectedItemPosition();if(fixed.isChecked()&&(index<1||index>=siteIds.size())){problem("Select an assigned site.");return;}data.put("locationId",fixed.isChecked()?siteIds.get(index):"");
        if(role.getSelectedItemPosition()!=0){
            String raw=allowance.getText().toString().trim();
            if(raw.isEmpty()){String opening=used.getText().toString().trim();if(!opening.isEmpty()&&!opening.matches("0+")){validationError(allowance,"Enter annual allowance before recording used leave");return;}data.put("annualLeaveAllowance",null);}
            else try{int quota=Integer.parseInt(raw),opening=used.getText().toString().trim().isEmpty()?0:Integer.parseInt(used.getText().toString().trim()),y=Integer.parseInt(year.getText().toString().trim());
                if(quota<0||quota>366||opening<0||opening>quota||y<2000||y>2100)throw new NumberFormatException();data.put("annualLeaveAllowance",quota);data.put("annualLeaveUsedBeforeApp",opening);data.put("annualLeaveYear",y);
            }catch(NumberFormatException e){validationError(allowance,"Use whole days 0-366, used <= allowance, and a valid year");return;}
        }
        if(!requireOnline("Edit Employee"))return;hideKeyboard();
        loadGeneration++;savingState(true);save.setEnabled(false);save.setText("Saving...");feedback("Saving employee changes to the server...",false);
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("updateEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data).addOnSuccessListener(r->{if(isFinishing()||isDestroyed())return;success("Employee details updated successfully.",this::finish);}).addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;savingState(false);save.setEnabled(true);save.setText("Save Employee Changes");problem(backendError("Edit Employee",e));});
    }
}
