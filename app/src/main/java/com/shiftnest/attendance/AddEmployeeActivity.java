package com.shiftnest.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AddEmployeeActivity extends ShiftNestBaseActivity {
    private boolean employeeSaving;
    private void employeeInputs(boolean enabled){toggleEmployeeInputs(findViewById(android.R.id.content),enabled);}
    private void toggleEmployeeInputs(android.view.View view,boolean enabled){
        if(view instanceof EditText||view instanceof android.widget.Spinner||view instanceof android.widget.RadioButton)view.setEnabled(enabled);
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)toggleEmployeeInputs(group.getChildAt(i),enabled);}
    }

    private EditText etEmployeeName;
    private EditText etEmployeePhone;

    private EditText etEmployeeNumber;
    private EditText etLeaveAllowance, etLeaveUsed;
    private TextView leaveInfo;
    private int siteGeneration;
    private String selectedSiteId="";
    private boolean locationsReady;
    private RadioGroup visibleRoles, visibleLocations;
    private Spinner spinnerRole;
    private Spinner spinnerLocation;

    private RadioGroup radioGroupLocationType;
    private RadioButton radioFixedLocation;
    private RadioButton radioNoFixedLocation;

    private TextView tvLocationInfo;

    private Button btnSaveEmployee;

    private Button btnTopBack;
    private Button btnTopHome;

    private FirebaseFirestore db;

    private final ArrayList<String> locationNames =
            new ArrayList<>();

    private final ArrayList<String> locationIds =
            new ArrayList<>();

    private final String[] roles = {
            "Sales Officer",
            "Staff Officer",
            "Hotel Officer"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        if(savedInstanceState!=null)selectedSiteId=savedInstanceState.getString("selectedSiteId","");

        setContentView(
                R.layout.activity_add_employee
        );

        etEmployeeName =
                findViewById(R.id.etEmployeeName);

        etEmployeePhone =
                findViewById(R.id.etEmployeePhone);

        spinnerRole =
                findViewById(R.id.spinnerRole);

        spinnerLocation =
                findViewById(R.id.spinnerLocation);
        spinnerLocation.setTag("shiftnest_data");

        radioGroupLocationType =
                findViewById(
                        R.id.radioGroupLocationType
                );

        radioFixedLocation =
                findViewById(
                        R.id.radioFixedLocation
                );

        radioNoFixedLocation =
                findViewById(
                        R.id.radioNoFixedLocation
                );

        tvLocationInfo =
                findViewById(
                        R.id.tvLocationInfo
                );

        btnSaveEmployee =
                findViewById(
                        R.id.btnSaveEmployee
                );

        // TOP NAVIGATION
        btnTopBack =
                findViewById(R.id.btnTopBack);

        btnTopHome =
                findViewById(R.id.btnTopHome);

        db =
                FirebaseFirestore.getInstance();

        setupTopNavigation();

        styleSpinner(spinnerRole);
        styleSpinner(spinnerLocation);
        visibleRoles = choicesBelow(spinnerRole);
        visibleLocations = choicesBelow(spinnerLocation);
        setupRoleSpinner();
        renderRoles();

        etEmployeeNumber = new EditText(this);
        etEmployeeNumber.setHint("Employee number (optional)");
        etEmployeeNumber.setTextColor(color(R.color.shiftnest_text));
        etEmployeeNumber.setHintTextColor(color(R.color.shiftnest_hint));
        ((android.view.ViewGroup) etEmployeeName.getParent()).addView(etEmployeeNumber, 1);
        setupLeaveFields();
        setupLocationTypeSelection();
        if(savedInstanceState!=null){
            etEmployeeNumber.setText(savedInstanceState.getString("draftNumber",""));etLeaveAllowance.setText(savedInstanceState.getString("draftAllowance",""));etLeaveUsed.setText(savedInstanceState.getString("draftUsed",""));
            int roleIndex=Math.max(0,Math.min(2,savedInstanceState.getInt("draftRole",0)));spinnerRole.setSelection(roleIndex);visibleRoles.check(visibleRoles.getChildAt(roleIndex).getId());updateLeaveFields();
        }
        loadLocations();

        ((android.widget.LinearLayout)btnSaveEmployee.getParent()).addView(button("Refresh saved locations",this::loadLocations), ((android.widget.LinearLayout)btnSaveEmployee.getParent()).indexOfChild(btnSaveEmployee));
        btnSaveEmployee.setOnClickListener(v -> {

            saveEmployee();
        });
    }

    private void setupLeaveFields() {
        android.widget.LinearLayout parent = (android.widget.LinearLayout) btnSaveEmployee.getParent();
        leaveInfo = text("", 16);
        etLeaveAllowance = new EditText(this);
        etLeaveUsed = new EditText(this);
        etLeaveAllowance.setHint("Annual leave allowance (blank = #)");
        etLeaveUsed.setHint("Already taken before using this app (default 0)");
        for (EditText field : new EditText[]{etLeaveAllowance, etLeaveUsed}) {
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            field.setTextColor(color(R.color.shiftnest_text));
            field.setHintTextColor(color(R.color.shiftnest_hint));
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        }
        parent.addView(leaveInfo, parent.indexOfChild(btnSaveEmployee));
        parent.addView(etLeaveAllowance, parent.indexOfChild(btnSaveEmployee));
        parent.addView(etLeaveUsed, parent.indexOfChild(btnSaveEmployee));
        spinnerRole.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int i, long id) { updateLeaveFields(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        updateLeaveFields();
    }
    private void updateLeaveFields() {
        boolean sales = "Sales Officer".equals(String.valueOf(spinnerRole.getSelectedItem()));
        etLeaveAllowance.setVisibility(sales ? android.view.View.GONE : android.view.View.VISIBLE);
        etLeaveUsed.setVisibility(sales ? android.view.View.GONE : android.view.View.VISIBLE);
        leaveInfo.setText(sales ? "Sales leave: 10 completed working days earn 1 leave."
                : "Annual leave for " + ShiftNestData.date().substring(0, 4)
                + "\nEnter the company allowance and leave already taken before this app. Blank allowance stays #. Casual / Sick quotas: #.");
    }

    // =====================================================
    // TOP NAVIGATION
    // =====================================================

    private void setupTopNavigation() {

        // BACK
        btnTopBack.setOnClickListener(v -> {

            finish();

        });

        // HOME
        btnTopHome.setOnClickListener(v -> {

            Intent intent = new Intent(
                    AddEmployeeActivity.this,
                    AdminDashboardActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);

            finish();

        });
    }

    // =====================================================
    // ROLE SPINNER
    // =====================================================

    private void setupRoleSpinner() {

        ArrayAdapter<String> roleAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.shiftnest_spinner_selected,
                        roles
                );

        roleAdapter.setDropDownViewResource(
                R.layout.shiftnest_spinner_dropdown
        );

        spinnerRole.setAdapter(
                roleAdapter
        );
    }

    // =====================================================
    // LOCATION TYPE
    // =====================================================

    private void setupLocationTypeSelection() {

        // Default = Fixed Location
        radioFixedLocation.setChecked(true);

        spinnerLocation.setEnabled(true);

        tvLocationInfo.setText(
                "Select the employee's fixed work location"
        );

        radioGroupLocationType.setOnCheckedChangeListener(
                (group, checkedId) -> {

                    if (checkedId ==
                            R.id.radioFixedLocation) {

                        // Fixed Location selected
                        spinnerLocation.setEnabled(true);
                        setSiteChoicesEnabled(true);

                        tvLocationInfo.setText(
                                "Select the employee's fixed work location"
                        );

                    } else if (checkedId ==
                            R.id.radioNoFixedLocation) {

                        // No Fixed Location selected
                        spinnerLocation.setEnabled(false);
                        setSiteChoicesEnabled(false);

                        tvLocationInfo.setText(
                                "No fixed location. Employee can Check In from anywhere."
                        );
                    }
                }
        );
    }

    // =====================================================
    // LOAD LOCATIONS
    // =====================================================

    private void loadLocations() {
        if(employeeSaving){problem("Wait for the employee save to finish before refreshing locations.");return;}
        final int run=++siteGeneration;
        locationsReady=false;

        ArrayAdapter<String> loading = new ArrayAdapter<>(this, R.layout.shiftnest_spinner_selected, new String[]{"Loading assigned sites..."});
        loading.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);
        spinnerLocation.setAdapter(loading);
        showSiteStatus("Loading saved locations...");
        ShiftNestRequests.read(db.collection("locations")
                .get(com.google.firebase.firestore.Source.SERVER))
                .addOnSuccessListener(
                        queryDocumentSnapshots -> {
                            if(run!=siteGeneration || isFinishing() || isDestroyed())return;

                            locationNames.clear();
                            locationIds.clear();

                            for (
                                    QueryDocumentSnapshot document :
                                    queryDocumentSnapshots
                            ) {

                                String locationName =
                                        document.getString(
                                                "name"
                                        );

                                if (locationName != null
                                        && !locationName
                                        .trim()
                                        .isEmpty()) {

                                    locationNames.add(
                                            locationName
                                    );

                                    locationIds.add(
                                            document.getId()
                                    );
                                }
                            }

                            if (locationNames.isEmpty()) {
                                showLocationMessage("No sites saved — add a location first");
                                tvLocationInfo.setText("No saved sites. Add Location first, or choose No Fixed Location.");

                                problem("No locations found. You can still use No Fixed Location.");
                                tvLocationInfo.setText("No saved locations. Add Location first, or choose No Fixed Location.");

                                spinnerLocation
                                        .setEnabled(false);

                                return;
                            }

                            ArrayAdapter<String>
                                    locationAdapter =
                                    new ArrayAdapter<>(
                                            AddEmployeeActivity.this,
                                            R.layout.shiftnest_spinner_selected,
                                            locationNames
                                    );

                            locationAdapter
                                    .setDropDownViewResource(
                                            R.layout.shiftnest_spinner_dropdown
                                    );

                            locationsReady=true;
                            spinnerLocation.setAdapter(locationAdapter);
                            renderLocations();
                            spinnerLocation.setEnabled(radioFixedLocation.isChecked());
                            tvLocationInfo.setText(radioFixedLocation.isChecked() ? "Select the employee's fixed work location" : "No fixed work location");

                            Toast.makeText(
                                    AddEmployeeActivity.this,
                                    locationNames.size()
                                            + " location(s) loaded",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .addOnFailureListener(e -> {
                    if(run!=siteGeneration || isFinishing() || isDestroyed())return;
                    showLocationMessage("Sites could not be loaded");
                    tvLocationInfo.setText("Locations unavailable. Tap Reload locations.");
                    problem(backendError("Load locations",e));
                    tvLocationInfo.setText("Locations unavailable. Tap Reload locations.");

                    spinnerLocation
                            .setEnabled(false);
                });
    }

    private RadioGroup choicesBelow(Spinner storedSelection) {
        android.widget.LinearLayout parent=(android.widget.LinearLayout)storedSelection.getParent();
        RadioGroup choices=new RadioGroup(this);
        choices.setOrientation(RadioGroup.VERTICAL);
        android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,0,0,dp(20));
        parent.addView(choices,parent.indexOfChild(storedSelection)+1,params);
        storedSelection.setVisibility(android.view.View.GONE);
        return choices;
    }
    private RadioButton choice(String label) {
        RadioButton item=new androidx.appcompat.widget.AppCompatRadioButton(this);
        item.setId(android.view.View.generateViewId());
        item.setText(label);item.setTextSize(16);item.setTextColor(color(R.color.shiftnest_text));
        item.setButtonTintList(android.content.res.ColorStateList.valueOf(color(R.color.shiftnest_heading)));
        item.setMinimumHeight(dp(56));item.setPadding(dp(12),dp(8),dp(12),dp(8));
        item.setBackgroundResource(R.drawable.shiftnest_input);
        RadioGroup.LayoutParams params=new RadioGroup.LayoutParams(-1,-2);
        params.setMargins(0,0,0,dp(8));item.setLayoutParams(params);
        return item;
    }
    private void renderRoles() {
        String[] labels={"Sales Officers", "Office Staff (Staff Officers)", "Hotel Staff"};
        for(String label:labels)visibleRoles.addView(choice(label));
        visibleRoles.check(visibleRoles.getChildAt(0).getId());
        visibleRoles.setOnCheckedChangeListener((group,id)->{
            int index=group.indexOfChild(group.findViewById(id));
            if(index>=0) {
                spinnerRole.setSelection(index);
                if(etLeaveAllowance!=null)updateLeaveFields();
            }
        });
    }
    private void showSiteStatus(String message) {
        visibleLocations.setOnCheckedChangeListener(null);
        visibleLocations.removeAllViews();visibleLocations.addView(text(message,16));
        visibleLocations.addView(button("Search / reload work locations",()->{if(employeeSaving){problem("Wait for the save to finish.");return;}feedback("Loading work locations from the server...",false);loadLocations();}));
    }
    private void renderLocations() {
        visibleLocations.setOnCheckedChangeListener(null);visibleLocations.removeAllViews();
        int selected=locationIds.indexOf(selectedSiteId);
        if(selected<0)selectedSiteId="";
        Button select=button(selected<0?"Search and select work location ▾":"Work location: "+locationNames.get(selected)+" ▾",()->{
            if(employeeSaving){problem("Wait for the employee save to finish.");return;}
            if(!radioFixedLocation.isChecked()){problem("Choose Fixed Location before selecting a work site.");return;}
            if(!locationsReady){problem("Locations are not loaded. Refreshing saved locations; retry selection after loading finishes.");loadLocations();return;}
            if(locationNames.isEmpty()){problem("No saved locations. Add a location first.");return;}
            ShiftNestSearchChoices.show(this,"Select fixed work location",locationNames,locationIds.indexOf(selectedSiteId),index->{
                selectedSiteId=locationIds.get(index);spinnerLocation.setSelection(index);renderLocations();
            });
        });
        visibleLocations.addView(select);setSiteChoicesEnabled(radioFixedLocation.isChecked());
    }
    private void setSiteChoicesEnabled(boolean enabled) {
        if(visibleLocations==null)return;
        for(int i=0;i<visibleLocations.getChildCount();i++) {
            android.view.View item=visibleLocations.getChildAt(i);
            if(item instanceof Button){item.setEnabled(!employeeSaving);item.setAlpha(enabled?1f:0.6f);}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("selectedSiteId",selectedSiteId);state.putString("draftNumber",etEmployeeNumber.getText().toString());state.putString("draftAllowance",etLeaveAllowance.getText().toString());state.putString("draftUsed",etLeaveUsed.getText().toString());state.putInt("draftRole",spinnerRole.getSelectedItemPosition());}
    private boolean validEmployeeForm() {
        boolean valid=true;
        if(etEmployeeName.getText().toString().trim().length()>120){validationError(etEmployeeName,"Employee name must be 120 characters or fewer");valid=false;}
        if(etEmployeeName.getText().toString().trim().isEmpty()){validationError(etEmployeeName,"Enter employee name");valid=false;}
        if(ShiftNestData.normalizePhone(etEmployeePhone.getText().toString()).isEmpty()){validationError(etEmployeePhone,"Enter a valid Sri Lankan phone number, e.g. 0771234567");valid=false;}
        if(spinnerRole.getSelectedItem()==null){problem("Select the employee role.");valid=false;}
        int type=radioGroupLocationType.getCheckedRadioButtonId();
        if(type==-1){problem("Choose Fixed Location or No Fixed Location.");valid=false;}
        if(type==R.id.radioFixedLocation){
            if(!locationsReady){problem("Fixed work locations are not available yet. Tap Refresh saved locations. If there are no sites, add one first.");valid=false;}
            else if(selectedSiteId.isEmpty()||!locationIds.contains(selectedSiteId)){problem("Fixed work location is missing. Tap Search and select work location, then choose a site.");valid=false;}
        }
        if(!"Sales Officer".equals(String.valueOf(spinnerRole.getSelectedItem()))){
            String quota=etLeaveAllowance.getText().toString().trim(),used=etLeaveUsed.getText().toString().trim();
            try {
                int opening=used.isEmpty()?0:Integer.parseInt(used);
                if(quota.isEmpty()) {if(opening!=0){validationError(etLeaveAllowance,"Enter annual allowance before recording used leave");valid=false;}}
                else {int total=Integer.parseInt(quota);if(total<0||total>366||opening<0||opening>total){validationError(etLeaveAllowance,"Use 0–366 whole days. Already taken cannot exceed the allowance");valid=false;}}
            } catch(NumberFormatException error){validationError(etLeaveAllowance,"Allowance and already-taken leave must be whole numbers from 0–366");valid=false;}
        }
        return valid;
    }

    private void showLocationMessage(String message) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.shiftnest_spinner_selected, new String[]{message});
        adapter.setDropDownViewResource(R.layout.shiftnest_spinner_dropdown);
        spinnerLocation.setAdapter(adapter);
        showSiteStatus(message);
    }

    // =====================================================
    // SAVE EMPLOYEE
    // =====================================================

    private void saveEmployee() {
        if(employeeSaving){feedback("Employee save is still in progress. Please wait for the result.",false);return;}
        if(!validEmployeeForm())return;

        String name =
                etEmployeeName
                        .getText()
                        .toString()
                        .trim();

        String phone =
                etEmployeePhone
                        .getText()
                        .toString()
                        .trim();

        if (name.isEmpty()) {

            validationError(etEmployeeName,
                    "Enter employee name"
            );

            etEmployeeName.requestFocus();

            problem("Please enter employee name");

            return;
        }

        if (phone.isEmpty()) {

            validationError(etEmployeePhone,
                    "Enter phone number"
            );

            etEmployeePhone.requestFocus();

            problem("Please enter phone number");

            return;
        }

        phone=ShiftNestData.normalizePhone(phone);
        if(phone.isEmpty()){validationError(etEmployeePhone,"Enter a valid Sri Lankan phone number");return;}

        Object selectedRole =
                spinnerRole.getSelectedItem();

        if (selectedRole == null) {

            problem("Please select employee role");

            return;
        }

        String role =
                selectedRole.toString();

        int selectedLocationType =
                radioGroupLocationType
                        .getCheckedRadioButtonId();

        if (selectedLocationType == -1) {

            problem("Please select Work Location Type");

            return;
        }

        Map<String, Object> employee =
                new HashMap<>();

        employee.put(
                "name",
                name
        );

        employee.put(
                "phone",
                phone
        );

        employee.put(
                "role",
                role
        );

        employee.put("active", true);
        if (!"Sales Officer".equals(role)) {
            String allowance = etLeaveAllowance.getText().toString().trim();
            String usedText = etLeaveUsed.getText().toString().trim();
            int used = usedText.isEmpty() ? 0 : Integer.parseInt(usedText);
            if (allowance.isEmpty()) {
                if (used != 0) { validationError(etLeaveAllowance,"Enter allowance before recording used leave"); return; }
            } else {
                int total = Integer.parseInt(allowance);
                if (total > 366 || used > total) { validationError(etLeaveAllowance,"Use 0-366 days; already taken cannot exceed allowance"); return; }
                employee.put("annualLeaveAllowance", total);
                employee.put("annualLeaveUsedBeforeApp", used);
                employee.put("annualLeaveYear", Integer.parseInt(ShiftNestData.date().substring(0,4)));
            }
        }

        // FIXED LOCATION
        if (selectedLocationType ==
                R.id.radioFixedLocation) {

            if (locationNames.isEmpty()
                    || locationIds.isEmpty()) {

                problem("No location available. Please add a location first.");

                return;
            }

            int locationPosition = locationIds.indexOf(selectedSiteId);

            if (locationPosition < 0
                    || locationPosition >=
                    locationIds.size()) {

                problem("Please select a location");

                return;
            }

            String locationId =
                    locationIds.get(
                            locationPosition
                    );

            String locationName =
                    locationNames.get(
                            locationPosition
                    );

            employee.put(
                    "workLocationType",
                    "fixed"
            );

            employee.put(
                    "locationId",
                    locationId
            );

            employee.put(
                    "locationName",
                    locationName
            );

        } else {

            // NO FIXED LOCATION
            employee.put(
                    "workLocationType",
                    "no_fixed"
            );

            employee.put(
                    "locationId",
                    ""
            );

            employee.put(
                    "locationName",
                    "No Fixed Location"
            );
        }

        Toast.makeText(
                this,
                "Saving employee...",
                Toast.LENGTH_SHORT
        ).show();

        if(!requireOnline("Save Employee"))return;
        hideKeyboard();siteGeneration++;employeeSaving=true;employeeInputs(false);
        btnSaveEmployee.setEnabled(false);btnSaveEmployee.setText("Saving employee...");feedback("Saving employee to the server...",false);

        employee.put("employeeNumber", etEmployeeNumber.getText().toString().trim());
        com.google.firebase.functions.FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("createEmployee").withTimeout(30,java.util.concurrent.TimeUnit.SECONDS).call(employee)
                .addOnSuccessListener(
                        documentReference -> {

                            if(isFinishing()||isDestroyed())return;
                            employeeSaving=false;employeeInputs(true);btnSaveEmployee.setText("Save Employee");
                            btnSaveEmployee
                                    .setEnabled(true);

                            success("Employee saved successfully. Open Employee List to view or edit the employee.");

                            selectedSiteId="";
                            etEmployeeNumber.setText("");
                            etLeaveAllowance.setText(""); etLeaveUsed.setText("");
                            etEmployeeName
                                    .setText("");

                            etEmployeePhone
                                    .setText("");

                            spinnerRole
                                    .setSelection(0);
                            visibleRoles.check(visibleRoles.getChildAt(0).getId());
                            updateLeaveFields();

                            radioFixedLocation
                                    .setChecked(true);

                            if (!locationNames
                                    .isEmpty()) {

                                spinnerLocation
                                        .setSelection(0);
                                renderLocations();

                                spinnerLocation
                                        .setEnabled(true);
                            }
                        }
                )
                .addOnFailureListener(
                        e -> {

                            if(isFinishing()||isDestroyed())return;
                            employeeSaving=false;employeeInputs(true);btnSaveEmployee.setText("Save Employee");
                            btnSaveEmployee
                                    .setEnabled(true);

                            problem(backendError("Save Employee",e));
                        }
                );
    }
}