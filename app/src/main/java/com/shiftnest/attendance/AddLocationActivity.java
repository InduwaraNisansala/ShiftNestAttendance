package com.shiftnest.attendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AddLocationActivity extends ShiftNestBaseActivity {
    private EditText name, latitude, longitude, radius;
    private Button save, gps;
    private boolean saving,gpsLoading;
    private String pendingLocationId;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (hasPermission()) currentLocation();
            else problem("GPS permission denied. You can still enter coordinates manually.");
        });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (isFinishing()) return;
        page("Add Location");
        screen.addView(text("Enter site coordinates manually. You do not need to visit the site.", 16));
        name = input("Location name", InputType.TYPE_CLASS_TEXT);
        latitude = input("Latitude (-90 to 90)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        longitude = input("Longitude (-180 to 180)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        radius = input("Allowed radius in meters (1-10000)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        gps = button("Use Current GPS (optional)", () -> {
            if (hasPermission()) currentLocation();
            else permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        screen.addView(gps);
        save = button("Save Location", this::saveLocation);
        screen.addView(save);
        if (state != null) {
            pendingLocationId=state.getString("pendingLocationId");
            name.setText(state.getString("name", "")); latitude.setText(state.getString("latitude", ""));
            longitude.setText(state.getString("longitude", "")); radius.setText(state.getString("radius", ""));
        }
    }
    private EditText input(String hint, int type) {
        screen.addView(text(hint, 16));
        EditText e = new EditText(this); e.setHint(hint); e.setInputType(type);
        e.setTextColor(color(R.color.shiftnest_text)); e.setHintTextColor(color(R.color.shiftnest_hint));
        e.setBackgroundResource(R.drawable.shiftnest_input); e.setPadding(dp(16),dp(12),dp(16),dp(12));
        screen.addView(e, new android.widget.LinearLayout.LayoutParams(-1,-2)); return e;
    }
    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void currentLocation() {
        if (!hasPermission() || saving || gpsLoading) return;
        if(!locationEnabled()){problem("Phone Location is turned off. Turn it on in phone Settings, or enter coordinates manually.");return;}
        gpsLoading=true;
        gps.setEnabled(false); gps.setText("Getting GPS...");
        ShiftNestRequests.read(LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(new com.google.android.gms.location.CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(25000).build(), null))
            .addOnSuccessListener(location -> {
                if(isFinishing()||isDestroyed())return;gpsLoading=false;
                gps.setEnabled(!saving); gps.setText("Use Current GPS (optional)");
                if (location == null) { problem("GPS unavailable. Enter the coordinates manually."); return; }
                latitude.setText(String.valueOf(location.getLatitude())); longitude.setText(String.valueOf(location.getLongitude()));
            }).addOnFailureListener(e -> {if(isFinishing()||isDestroyed())return;gpsLoading=false; gps.setEnabled(!saving); gps.setText("Use Current GPS (optional)"); problem("Location unavailable. Enable phone Location and try again."); });
    }
    private Double coordinate(EditText field, double min, double max) {
        try {
            double value = Double.parseDouble(field.getText().toString().trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { validationError(field,"Enter a number from " + min + " to " + max); return null; }
    }
    private void saveLocation() {
        if (saving) return;
        if(gpsLoading){problem("GPS is still loading. Wait for its result before saving the location.");return;}
        String title = name.getText().toString().trim();
        if (title.isEmpty()) { validationError(name,"Enter location name"); return; }
        Double lat = coordinate(latitude,-90,90), lon = coordinate(longitude,-180,180), distance = coordinate(radius,1,10000);
        if (lat == null || lon == null || distance == null) return;
        Map<String,Object> data = new HashMap<>(); data.put("name",title); data.put("latitude",lat);
        data.put("longitude",lon); data.put("allowedRadius",distance);
        if(!requireOnline("Save Location"))return;hideKeyboard();
        saving=true;locationInputs(false); save.setEnabled(false); gps.setEnabled(false); save.setText("Saving...");feedback("Saving location to the server...",false);
        com.google.firebase.firestore.CollectionReference sites=FirebaseFirestore.getInstance().collection("locations");
        if(pendingLocationId==null)pendingLocationId=sites.document().getId();
        ShiftNestRequests.write(sites.document(pendingLocationId).set(data)).addOnSuccessListener(r -> {
            pendingLocationId=null;
            if(isFinishing()||isDestroyed())return;reset(); name.setText(""); latitude.setText(""); longitude.setText(""); radius.setText(""); success("Location saved successfully.");
        }).addOnFailureListener(e -> {if(isFinishing()||isDestroyed())return; reset(); problem(backendError("Save Location",e)); });
    }
    private void locationInputs(boolean enabled){for(EditText field:new EditText[]{name,latitude,longitude,radius})field.setEnabled(enabled);}
    private void reset() { saving=false;locationInputs(true); save.setEnabled(true); gps.setEnabled(true); save.setText("Save Location"); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putString("pendingLocationId",pendingLocationId); state.putString("name",name.getText().toString()); state.putString("latitude",latitude.getText().toString());
        state.putString("longitude",longitude.getText().toString()); state.putString("radius",radius.getText().toString());
    }
}
