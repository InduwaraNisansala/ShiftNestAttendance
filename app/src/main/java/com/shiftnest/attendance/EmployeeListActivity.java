package com.shiftnest.attendance;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import com.google.firebase.firestore.*;
import java.util.*;
public class EmployeeListActivity extends ShiftNestBaseActivity {
    private final List<DocumentSnapshot> employees=new ArrayList<>();private final List<DocumentSnapshot> visible=new ArrayList<>();private final List<String[]> rows=new ArrayList<>();private Spinner filter;private LinearLayout table;private TextView summary;private Button pdf;private boolean loaded,removing;private CheckBox showRemoved;private int generation;
    private final String[] headers={"Employee No.","Name","Department","Phone","Assigned site","Status"};
    @Override protected void onCreate(Bundle s){super.onCreate(s);if(isFinishing())return;page("Employee List");summary=text("Loading employees…",18);screen.addView(summary);filter=departments(this::render);showRemoved=new CheckBox(this);showRemoved.setText("Show removed employees");showRemoved.setTextColor(color(R.color.shiftnest_text));if(s!=null)showRemoved.setChecked(s.getBoolean("showRemoved",false));showRemoved.setOnCheckedChangeListener((b,on)->render());screen.addView(showRemoved);screen.addView(button("Refresh employees",this::load));pdf=button("Download employee sheet PDF",()->exportPdf("Employee List - "+filter.getSelectedItem(),headers,rows));pdf.setEnabled(false);screen.addView(pdf);table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);screen.addView(table);}
    @Override protected void onResume(){super.onResume();load();}
    private void load(){if(removing){problem("Wait for the employee removal to finish.");return;}final int run=++generation;loaded=false;pdf.setEnabled(false);summary.setText("Loading employees...");ShiftNestRequests.read(FirebaseFirestore.getInstance().collection("employees").get(com.google.firebase.firestore.Source.SERVER)).addOnSuccessListener(q->{if(run!=generation||isFinishing()||isDestroyed())return;employees.clear();employees.addAll(q.getDocuments());employees.sort(Comparator.comparing(d->ShiftNestData.string(d,"name")));loaded=true;render();}).addOnFailureListener(e->{if(run!=generation||isFinishing()||isDestroyed())return;table.removeAllViews();summary.setText(backendError("Load employees",e)+"\nTap Refresh employees to retry.");problem(backendError("Load employees",e));});}
    private void render(){if(table==null||filter==null||!loaded)return;String selected=String.valueOf(filter.getSelectedItem());rows.clear();visible.clear();Map<String,Integer>counts=new LinkedHashMap<>();for(DocumentSnapshot e:employees){if(Boolean.TRUE.equals(e.get("archived"))&&!showRemoved.isChecked())continue;String dep=ShiftNestData.department(ShiftNestData.string(e,"role"));counts.put(dep,counts.getOrDefault(dep,0)+1);if(!"All Employees".equals(selected)&&!dep.equals(selected))continue;visible.add(e);String number=ShiftNestData.string(e,"employeeNumber");rows.add(new String[]{number.isEmpty()?e.getId():number,ShiftNestData.string(e,"name"),dep,ShiftNestData.string(e,"phone"),ShiftNestData.string(e,"locationName"),Boolean.TRUE.equals(e.get("archived"))?"Removed":Boolean.TRUE.equals(e.get("active"))?"Active":"Inactive"});}summary.setText("All employees: "+counts.values().stream().mapToInt(Integer::intValue).sum()+"\nSales Officers: "+counts.getOrDefault("Sales",0)+" • Office Staff: "+counts.getOrDefault("Office",0)+" • Hotel Staff: "+counts.getOrDefault("Hotel",0)+"\nShowing: "+rows.size()+"\nTap an employee row to edit or remove.");table.removeAllViews();table.addView(ShiftNestTable.create(this,headers,rows,i->row(visible.get(i))));pdf.setEnabled(!rows.isEmpty());}
    private void edit(DocumentSnapshot employee){android.content.Intent intent=new android.content.Intent(this,EditEmployeeActivity.class);intent.putExtra("employeeId",employee.getId());startActivity(intent);}
    private void row(DocumentSnapshot employee){
        if(removing){problem("Wait for the employee removal to finish.");return;}
        if(Boolean.TRUE.equals(employee.get("archived"))){ShiftNestLanguage.dialog(this).setTitle("Removed employee").setMessage("To restore this employee, open Edit and enable Active employee.").setPositiveButton("Edit Employee",(d,w)->edit(employee)).setNegativeButton("Close",null).show();return;}
        ShiftNestLanguage.dialog(this).setTitle(ShiftNestData.string(employee,"name")).setMessage(ShiftNestData.string(employee,"phone")).setPositiveButton("Edit Employee",(d,w)->edit(employee)).setNegativeButton("Remove Employee",(d,w)->confirmRemove(employee)).setNeutralButton("Close",null).show();
    }
    private void confirmRemove(DocumentSnapshot employee){
        ShiftNestLanguage.dialog(this).setTitle("Remove Employee").setMessage("Remove this employee from the list and disable login? Attendance and leave history are kept. The phone number remains reserved for this employee.").setPositiveButton("Remove",(d,w)->remove(employee.getId())).setNegativeButton("Cancel",null).show();
    }
    private void remove(String id){
        if(removing||!requireOnline("Remove employee"))return;removing=true;feedback("Removing employee...",false);
        com.google.firebase.functions.FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("removeEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(Collections.singletonMap("employeeId",id)).addOnSuccessListener(r->{if(isFinishing()||isDestroyed())return;removing=false;success("Employee removed. History is kept.");load();}).addOnFailureListener(e->{if(isFinishing()||isDestroyed())return;removing=false;problem(backendError("Remove employee",e));});
    }
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putBoolean("showRemoved",showRemoved.isChecked());}
}

