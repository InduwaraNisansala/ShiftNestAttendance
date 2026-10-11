package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.time.LocalDate;
import java.util.*;
public class LeaveSheetActivity extends ShiftNestBaseActivity {
    private LocalDate from=LocalDate.parse(ShiftNestData.date()).minusMonths(2),to=LocalDate.parse(ShiftNestData.date()).plusMonths(3);private Spinner department,status;private LinearLayout table;private TextView summary;private Button pdf,start,end;private ListenerRegistration changes;
    private final List<DocumentSnapshot>requests=new ArrayList<>(),visible=new ArrayList<>(),attendance=new ArrayList<>();private final List<String[]>rows=new ArrayList<>();private final String[]headers={"Employee / No.","Dept / Leave date","Applied / Status","Reason / Decision","Worked / Earned","Used / Pending","Available"};private boolean attendanceLoaded,employeesLoaded,requestsLoaded;private int sheetGeneration;private String loadError="";private final Set<String> decisions=new HashSet<>();private final Map<String,DocumentSnapshot>employees=new HashMap<>();
    @Override protected void onCreate(Bundle s){super.onCreate(s);if(isFinishing())return;page("Leave Sheet");if(s!=null){from=LocalDate.parse(s.getString("from",from.toString()));to=LocalDate.parse(s.getString("to",to.toString()));}start=button("From: "+from,()->pick(true));end=button("To: "+to,()->pick(false));screen.addView(start);screen.addView(end);department=departments(this::render);status=new ShiftNestChoiceSpinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,R.layout.shiftnest_spinner_selected,new String[]{"All statuses","pending","approved","rejected"});a.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);status.setAdapter(a);styleSpinner(status);status.setPrompt("Select leave status");screen.addView(status,new LinearLayout.LayoutParams(-1,-2));status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?>p,android.view.View v,int i,long id){render();}public void onNothingSelected(AdapterView<?>p){}});screen.addView(button("Pending approvals",()->{department.setSelection(0);status.setSelection(1);render();}));pdf=button("Download Leave Sheet PDF",this::downloadSheet);pdf.setEnabled(true);screen.addView(pdf);screen.addView(text("Future leave dates are allowed. From must be on or before To.",16));summary=text("Tap a pending row to record Chairman Sir's decision.",16);screen.addView(summary);table=new LinearLayout(this);table.setOrientation(1);screen.addView(table);screen.addView(button("Refresh leave sheet",this::loadSheet));bindDateChoice("leave_from",d->dateChanged(true,d));bindDateChoice("leave_to",d->dateChanged(false,d));}
    @Override protected void onStart(){super.onStart();loadSheet();}
    private void loadSheet(){
        final int run=++sheetGeneration;if(changes!=null)changes.remove();
        attendanceLoaded=false;employeesLoaded=false;requestsLoaded=false;loadError="";
        attendance.clear();employees.clear();requests.clear();rows.clear();visible.clear();table.removeAllViews();pdf.setEnabled(true);summary.setText("Loading leave sheet and balances...");
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        ShiftNestRequests.read(db.collection("employees").get(Source.SERVER)).addOnSuccessListener(q->{if(!currentSheet(run))return;employees.clear();for(DocumentSnapshot e:q)employees.put(e.getId(),e);employeesLoaded=true;render();}).addOnFailureListener(e->{if(!currentSheet(run))return;loadError=backendError("Load employee leave allowances",e);render();});
        ShiftNestRequests.read(db.collection("attendance").get(Source.SERVER)).addOnSuccessListener(q->{if(!currentSheet(run))return;attendance.clear();attendance.addAll(q.getDocuments());attendanceLoaded=true;render();}).addOnFailureListener(e->{if(!currentSheet(run))return;loadError=backendError("Load leave balances",e);render();});
        changes=db.collection("leaveRequests").addSnapshotListener((q,e)->{
            if(!currentSheet(run))return;
            if(e!=null){requestsLoaded=false;loadError=backendError("Load leave requests",e);render();return;}
            requests.clear();if(q!=null)requests.addAll(q.getDocuments());requests.sort(Comparator.comparing((DocumentSnapshot d)->ShiftNestData.string(d,"date")).reversed());requestsLoaded=true;render();
        });
    }
    private boolean currentSheet(int run){return run==sheetGeneration&&!isFinishing()&&!isDestroyed();}
    @Override protected void onStop(){sheetGeneration++;if(changes!=null){changes.remove();changes=null;}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("from",from.toString());state.putString("to",to.toString());}
    private void pick(boolean first){chooseDate(first?"leave_from":"leave_to",first?"Leave Sheet: From date":"Leave Sheet: To date",first?from:to,day->dateChanged(first,day));}
    private void dateChanged(boolean first,LocalDate day){
        if(first){from=day;start.setText("From: "+from);}else{to=day;end.setText("To: "+to);}
        render();if(from.isAfter(to))problem("From date must be on or before To date. Change one of the dates.");
    }
    private void render(){if(table==null||department==null||status==null)return;rows.clear();visible.clear();if(!loadError.isEmpty()){table.removeAllViews();pdf.setEnabled(true);summary.setText(loadError+"\nTap Refresh leave sheet to retry.");return;}if(!attendanceLoaded||!employeesLoaded||!requestsLoaded){table.removeAllViews();pdf.setEnabled(true);summary.setText("Loading leave sheet and balances...");return;}if(from.isAfter(to)){table.removeAllViews();pdf.setEnabled(true);summary.setText("From date must be before To date.");return;}String dep=String.valueOf(department.getSelectedItem()),st=String.valueOf(status.getSelectedItem());Map<String,Set<String>> workDates=new HashMap<>();
for(DocumentSnapshot a:attendance)if(ShiftNestLeaveMath.completedDay(ShiftNestData.string(a,"date"),ShiftNestData.string(a,"checkIn"),ShiftNestData.string(a,"checkOut"),ShiftNestData.string(a,"workStatus"),ShiftNestData.date()))workDates.computeIfAbsent(ShiftNestData.string(a,"employeeId"),k->new HashSet<>()).add(ShiftNestData.string(a,"date"));
Map<String,long[]> balances=new HashMap<>(),annualBalances=new HashMap<>();
for(DocumentSnapshot l:requests){String id=ShiftNestData.string(l,"employeeId"),date=ShiftNestData.string(l,"date"),state=ShiftNestData.string(l,"status");int index="approved".equals(state)?0:"pending".equals(state)?1:-1;if(index<0)continue;balances.computeIfAbsent(id,k->new long[2])[index]++;if(ShiftNestLeaveMath.validDate(date))annualBalances.computeIfAbsent(id+"_"+date.substring(0,4),k->new long[2])[index]++;}
for(DocumentSnapshot d:requests){String day=ShiftNestData.string(d,"date"),dept=ShiftNestData.string(d,"department"),state=ShiftNestData.string(d,"status");if(!ShiftNestLeaveMath.validDate(day)||day.compareTo(from.toString())<0||day.compareTo(to.toString())>0||(!dep.equals("All Employees")&&!dep.equals(dept))||(!st.equals("All statuses")&&!st.equals(state)))continue;visible.add(d);String id=ShiftNestData.string(d,"employeeId");Set<String>dates=workDates.getOrDefault(id,Collections.emptySet());long[] totals=balances.getOrDefault(id,new long[2]);long used=totals[0],pending=totals[1];long earned=dates.size()/10;boolean sales=dept.equals("Sales")&&attendanceLoaded;
String earnedCell=sales?dates.size()+" / "+earned:"# / #",usedCell=sales?used+" / "+pending:"# / #",availableCell=sales?String.valueOf(ShiftNestLeaveMath.available(earned,used,pending)):"#";
if(!dept.equals("Sales")){
    DocumentSnapshot employee=employees.get(id);
    if(employee!=null && day.length()>=4){
        Long year=employee.getLong("annualLeaveYear"),quota=employee.getLong("annualLeaveAllowance"),opening=employee.getLong("annualLeaveUsedBeforeApp");
        if(ShiftNestLeaveMath.validQuota(year,quota,opening,day)){
            long[] yearly=annualBalances.getOrDefault(id+"_"+day.substring(0,4),new long[2]);
            long annualUsed=opening+yearly[0],annualPending=yearly[1];
            earnedCell="Annual "+year+" / "+quota;usedCell=annualUsed+" / "+annualPending;availableCell=String.valueOf(ShiftNestLeaveMath.available(quota,annualUsed,annualPending));
        }
    }
}
Date created=ShiftNestData.timestamp(d,"createdAt");rows.add(new String[]{ShiftNestData.string(d,"employeeName")+"\n"+ShiftNestData.string(d,"employeeNumber"),dept+"\n"+day,(created==null?"--":ShiftNestData.format(created,"yyyy-MM-dd"))+"\n"+state,ShiftNestData.string(d,"reason")+"\n"+ShiftNestData.string(d,"decisionNote"),earnedCell,usedCell,availableCell});}summary.setText("Requests: "+rows.size()+"\nTap a pending row to record Chairman Sir's decision.\nSales balances use completed work days. Office / Hotel balances use the leave-date year’s configured allowance; pending requests reserve leave.");table.removeAllViews();table.addView(ShiftNestTable.create(this,headers,rows,i->decide(visible.get(i))));pdf.setEnabled(true);}
    private void downloadSheet(){
        if(from.isAfter(to)){problem("From date must be on or before To date.");return;}
        if(!loadError.isEmpty()){problem(loadError+"\nTap Refresh leave sheet, then retry download.");return;}
        if(!attendanceLoaded||!employeesLoaded||!requestsLoaded){problem("Leave sheet data is still loading. Wait for the result or tap Refresh leave sheet.");return;}
        if(rows.isEmpty()){problem("No leave requests match these dates, department and status. Change the filters before downloading.");return;}
        feedback("Preparing leave sheet PDF. Choose a destination when the save window opens.",false);
        exportPdf("Leave Sheet "+from+" to "+to,headers,rows);
    }
    private void decide(DocumentSnapshot d){if(decisions.contains(d.getId())){problem("A decision for this request is already being saved. Wait for its result.");return;}if(!"pending".equals(d.getString("status"))){problem("This request has already been decided.");return;}EditText note=new EditText(this);note.setHint("Chairman Sir's decision / note");styleEditable(note);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});AlertDialog dialog=ShiftNestLanguage.dialog(this).setTitle(ShiftNestData.string(d,"employeeName")+" • "+d.getString("date")).setMessage("Ask Chairman Sir first, then record Approve or Reject. The employee receives the saved decision.").setView(note).setPositiveButton("Approve",null).setNegativeButton("Reject",null).setNeutralButton("Close",null).create();dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->send(d,"approved",note,dialog));dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(b->send(d,"rejected",note,dialog));});dialog.show();}
    private void send(DocumentSnapshot d,String choice,EditText note,AlertDialog dialog){if(decisions.contains(d.getId()))return;if(note.getText().toString().trim().isEmpty()){validationError(note,"Enter the Chairman decision note");return;}if(!requireOnline("Save leave decision"))return;decisions.add(d.getId());hideKeyboard();note.setEnabled(false);dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);Map<String,Object>data=new HashMap<>();data.put("id",d.getId());data.put("status",choice);data.put("note",note.getText().toString().trim());FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("decideLeave").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(data).addOnSuccessListener(r->{decisions.remove(d.getId());dialog.dismiss();if(isFinishing()||isDestroyed())return;success("Decision saved. Employee status updated; notification delivery is handled separately.");}).addOnFailureListener(e->{decisions.remove(d.getId());if(isFinishing()||isDestroyed())return;dialog.setCancelable(true);note.setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);problem(backendError("Save leave decision",e));});}
}
