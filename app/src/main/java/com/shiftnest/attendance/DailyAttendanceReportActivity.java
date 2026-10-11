package com.shiftnest.attendance;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailyAttendanceReportActivity extends ShiftNestBaseActivity {

    private FirebaseFirestore db;

    private TextView tvReportDate;
    private TextView tvOverallSummary;

    private TextView tvBarCheckIn;
    private TextView tvBarLate;
    private TextView tvBarAbsent;
    private TextView tvBarCheckout;

    private ProgressBar pbCheckIn;
    private ProgressBar pbLate;
    private ProgressBar pbAbsent;
    private ProgressBar pbCheckout;

    private TextView tvOnTimeCount;
    private TextView tvLateCount;
    private TextView tvAbsentCount;
    private TextView tvCheckoutCount;

    private LinearLayout employeeChartContainer;
    private LinearLayout departmentSummaryContainer;
    private LinearLayout roleContainer;

    private TextView tvLoading;

    private Button btnDownloadPdf;
    private Button btnSharePdf;

    private final List<EmployeeData> employees = new ArrayList<>();
    private final Map<String, AttendanceData> attendanceMap = new HashMap<>();

    private String todayDate;
    private final Map<String,DocumentSnapshot> lateMessages=new HashMap<>();

    private int overallTotal = 0;
    private int overallOnTime = 0;
    private int overallLate = 0;
    private int overallAbsent = 0;
    private int overallCheckedOut = 0;

    private boolean creatingDailyPdf,reportReady;
    private int reportGeneration;
    private static final String ON_TIME_CUTOFF = "08:35:00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;

        setContentView(R.layout.activity_attendance_report);

        db = FirebaseFirestore.getInstance();

        todayDate = ShiftNestData.date();

        initViews();
        android.widget.LinearLayout parent=(android.widget.LinearLayout)btnDownloadPdf.getParent();
        parent.addView(button("Reload daily report",this::loadReport));

        tvReportDate.setText(
                "Daily Attendance Report - " + todayDate
        );

        String requestedDate = getIntent().getStringExtra("reportDate");
        if (requestedDate != null && requestedDate.matches("\\d{4}-\\d{2}-\\d{2}")) todayDate = requestedDate;
        tvReportDate.setText("Daily Attendance Report - " + todayDate);
        loadReport();
    }

    private void initViews() {

        tvReportDate = findViewById(R.id.tvReportDate);
        tvOverallSummary = findViewById(R.id.tvOverallSummary);

        tvBarCheckIn = findViewById(R.id.tvBarCheckIn);
        tvBarLate = findViewById(R.id.tvBarLate);
        tvBarAbsent = findViewById(R.id.tvBarAbsent);
        tvBarCheckout = findViewById(R.id.tvBarCheckout);

        pbCheckIn = findViewById(R.id.pbCheckIn);
        pbLate = findViewById(R.id.pbLate);
        pbAbsent = findViewById(R.id.pbAbsent);
        pbCheckout = findViewById(R.id.pbCheckout);

        tvOnTimeCount = findViewById(R.id.tvOnTimeCount);
        tvLateCount = findViewById(R.id.tvLateCount);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        tvCheckoutCount = findViewById(R.id.tvCheckoutCount);

        employeeChartContainer =
                findViewById(R.id.employeeChartContainer);

        departmentSummaryContainer =
                findViewById(R.id.departmentSummaryContainer);

        roleContainer =
                findViewById(R.id.roleContainer);

        tvLoading =
                findViewById(R.id.tvLoading);

        btnDownloadPdf =
                findViewById(R.id.btnDownloadPdf);

        btnSharePdf =
                findViewById(R.id.btnSharePdf);

        btnDownloadPdf.setOnClickListener(v ->
                generateAndDownloadPdf()
        );

        btnSharePdf.setOnClickListener(v ->
                generateAndSharePdf()
        );
    }

    // =========================================================
    // LOAD REPORT
    // =========================================================

    private void loadReport() {

        if(creatingDailyPdf){toast("PDF is being prepared. Please wait.");return;}
        if(!ShiftNestLeaveMath.validDate(todayDate)||todayDate.compareTo(ShiftNestData.date())>0){
            reportGeneration++;reportReady=false;btnDownloadPdf.setEnabled(false);btnSharePdf.setEnabled(false);
            tvLoading.setText("Attendance reports cannot include future dates.");tvLoading.setVisibility(View.VISIBLE);
            problem("Attendance reports cannot include future dates.");return;
        }
        final int run=++reportGeneration;
        reportReady=false;btnDownloadPdf.setEnabled(false);btnSharePdf.setEnabled(false);
        tvLoading.setText("Loading daily report...");tvLoading.setVisibility(View.VISIBLE);

        employees.clear();
        attendanceMap.clear();lateMessages.clear();

        ShiftNestRequests.read(db.collection("employees")
                .whereEqualTo("active", true)
                .get())
                .addOnSuccessListener(employeeSnapshots -> {
                    if(run!=reportGeneration||isFinishing()||isDestroyed())return;

                    for (DocumentSnapshot doc : employeeSnapshots) {

                        EmployeeData employee = new EmployeeData();

                        employee.id = doc.getId();

                        employee.name =
                                safeString(doc.getString("name"));

                        employee.phone =
                                safeString(doc.getString("phone"));

                        employee.role =
                                normalizeRole(
                                        safeString(
                                                doc.getString("role")
                                        )
                                );

                        employee.locationName =
                                safeString(
                                        doc.getString("locationName")
                                );

                        employees.add(employee);
                    }

                    loadTodayAttendance(run);

                })
                .addOnFailureListener(e -> {
                    if(run!=reportGeneration||isFinishing()||isDestroyed())return;

                    tvLoading.setText("Report unavailable. Tap Reload daily report.");tvLoading.setVisibility(View.VISIBLE);

                    problem(backendError("Load daily employee report",e));
                });
    }

    private void loadTodayAttendance(int run) {

        ShiftNestRequests.read(db.collection("attendance")
                .whereEqualTo("date", todayDate)
                .get())
                .addOnSuccessListener(snapshot -> {
                    if(run!=reportGeneration||isFinishing()||isDestroyed())return;

                    attendanceMap.clear();

                    for (DocumentSnapshot doc : snapshot) {

                        String employeeId =
                                safeString(
                                        doc.getString("employeeId")
                                );

                        if (employeeId.isEmpty()) {
                            continue;
                        }

                        AttendanceData data =
                                new AttendanceData();

                        data.employeeId = employeeId;

                        data.employeeName =
                                safeString(
                                        doc.getString("employeeName")
                                );

                        data.employeeRole =
                                normalizeRole(
                                        safeString(
                                                doc.getString("employeeRole")
                                        )
                                );

                        data.phone =
                                safeString(
                                        doc.getString("phone")
                                );

                        data.date =
                                safeString(
                                        doc.getString("date")
                                );

                        data.checkIn =
                                safeString(
                                        doc.getString("checkIn")
                                );

                        data.checkOut =
                                safeString(
                                        doc.getString("checkOut")
                                );

                        data.status =
                                safeString(
                                        doc.getString("status")
                                );

                        data.workStatus =
                                safeString(
                                        doc.getString("workStatus")
                                );

                        data.checkInSiteName=safeString(doc.getString("checkInSiteName"));
                        data.checkInLocationName=safeString(doc.getString("checkInLocationName"));
                        data.checkOutSiteName=safeString(doc.getString("checkOutSiteName"));
                        data.checkOutLocationName=safeString(doc.getString("checkOutLocationName"));
                        data.locationName =
                                safeString(
                                        doc.getString("locationName")
                                );

                        attendanceMap.put(
                                employeeId,
                                data
                        );
                    }

                    loadLateMessages(run);

                })
                .addOnFailureListener(e -> {
                    if(run!=reportGeneration||isFinishing()||isDestroyed())return;

                    tvLoading.setText("Report unavailable. Tap Reload daily report.");tvLoading.setVisibility(View.VISIBLE);

                    problem(backendError("Load daily attendance report",e));
                });
    }

    private void loadLateMessages(int run){
        ShiftNestRequests.read(db.collection("attendanceNotices").whereEqualTo("date",todayDate).get(com.google.firebase.firestore.Source.SERVER))
            .addOnSuccessListener(q->{if(run!=reportGeneration||isFinishing()||isDestroyed())return;lateMessages.clear();for(DocumentSnapshot d:q)lateMessages.put(ShiftNestData.string(d,"employeeId"),d);buildReport();})
            .addOnFailureListener(e->{if(run!=reportGeneration||isFinishing()||isDestroyed())return;reportReady=false;btnDownloadPdf.setEnabled(false);btnSharePdf.setEnabled(false);tvLoading.setText(backendError("Load daily late messages",e)+"\nReload daily report; incomplete reports cannot be exported.");tvLoading.setVisibility(View.VISIBLE);});
    }
    private List<DocumentSnapshot> sortedLateMessages(){List<DocumentSnapshot> list=new ArrayList<>(lateMessages.values());list.sort(java.util.Comparator.comparing(d->ShiftNestData.string(d,"employeeName")));return list;}

    // =========================================================
    // BUILD REPORT
    // =========================================================

    private void buildReport() {

        calculateOverall();

        updateOverallUI();

        buildEmployeeChart();

        buildDepartmentSummary();

        buildRoleSections();
        buildPlaceDetails();
        roleContainer.addView(text("Late check-in messages • "+todayDate,20));
        for(DocumentSnapshot note:sortedLateMessages())roleContainer.addView(text(ShiftNestData.string(note,"employeeName")+" • "+ShiftNestData.string(note,"employeeRole")+"\n"+ShiftNestData.string(note,"lateMessage")+"\nSubmitted: "+(ShiftNestData.timestamp(note,"updatedAt")==null?"--":ShiftNestData.format(ShiftNestData.timestamp(note,"updatedAt"),"HH:mm:ss")),16));
        if(lateMessages.isEmpty())roleContainer.addView(text("No late messages submitted for this date.",16));

        tvLoading.setVisibility(View.GONE);reportReady=true;btnDownloadPdf.setEnabled(true);btnSharePdf.setEnabled(true);
    }

    private void calculateOverall() {

        overallTotal = employees.size();

        overallOnTime = 0;
        overallLate = 0;
        overallAbsent = 0;
        overallCheckedOut = 0;

        for (EmployeeData employee : employees) {

            AttendanceData attendance =
                    attendanceMap.get(employee.id);

            if (attendance == null ||
                    attendance.checkIn.isEmpty()) {

                overallAbsent++;

                continue;
            }

            if (isLate(attendance.checkIn)) {
                overallLate++;
            } else {
                overallOnTime++;
            }

            if (!attendance.checkOut.isEmpty()) {
                overallCheckedOut++;
            }
        }
    }

    private void updateOverallUI() {

        tvOverallSummary.setText(
                "Overall Total: " + overallTotal +
                        "\nOn Time: " + overallOnTime +
                        "\nLate: " + overallLate +
                        "\nAbsent: " + overallAbsent +
                        "\nChecked Out: " + overallCheckedOut
        );

        tvBarCheckIn.setText(
                "Check In - " +
                        (overallOnTime + overallLate)
        );

        tvBarLate.setText(
                "Late - " + overallLate
        );

        tvBarAbsent.setText(
                "Absent - " + overallAbsent
        );

        tvBarCheckout.setText(
                "Checked Out - " +
                        overallCheckedOut
        );

        int max = Math.max(overallTotal, 1);

        pbCheckIn.setMax(max);
        pbLate.setMax(max);
        pbAbsent.setMax(max);
        pbCheckout.setMax(max);

        pbCheckIn.setProgress(
                overallOnTime + overallLate
        );

        pbLate.setProgress(overallLate);

        pbAbsent.setProgress(overallAbsent);

        pbCheckout.setProgress(
                overallCheckedOut
        );

        tvOnTimeCount.setText(
                "ON TIME\n" + overallOnTime
        );

        tvLateCount.setText(
                "LATE\n" + overallLate
        );

        tvAbsentCount.setText(
                "ABSENT\n" + overallAbsent
        );

        tvCheckoutCount.setText(
                "CHECKED OUT\n" +
                        overallCheckedOut
        );
    }

    // =========================================================
    // EMPLOYEE ATTENDANCE CHART
    // =========================================================

    private void buildEmployeeChart() {

        employeeChartContainer.removeAllViews();

        TextView title =
                createTitleText(
                        "EMPLOYEE ATTENDANCE"
                );

        employeeChartContainer.addView(title);

        if (employees.isEmpty()) {

            employeeChartContainer.addView(
                    createNormalText(
                            "No active employees."
                    )
            );

            return;
        }

        List<EmployeeData> sorted =
                new ArrayList<>(employees);

        Collections.sort(
                sorted,
                Comparator.comparing(
                        e -> e.name.toLowerCase(Locale.getDefault())
                )
        );

        for (EmployeeData employee : sorted) {

            AttendanceData attendance =
                    attendanceMap.get(employee.id);

            String status;

            if (attendance == null ||
                    attendance.checkIn.isEmpty()) {

                status = "ABSENT";

            } else if (isLate(attendance.checkIn)) {

                status = "LATE";

            } else {

                status = "ON TIME";
            }

            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            row.setPadding(
                    8,
                    10,
                    8,
                    10
            );

            TextView name =
                    new TextView(this);
            name.setTextColor(color(R.color.shiftnest_text));

            name.setText(
                    employee.name
            );

            name.setTextSize(14);

            name.setTypeface(
                    null,
                    Typeface.BOLD
            );

            TextView statusView =
                    new TextView(this);
            statusView.setTextColor(color(R.color.shiftnest_text));

            statusView.setText(
                    status
            );

            statusView.setTextSize(14);

            statusView.setGravity(
                    Gravity.END
            );

            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            2
                    );

            LinearLayout.LayoutParams statusParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    );

            row.addView(
                    name,
                    nameParams
            );

            row.addView(
                    statusView,
                    statusParams
            );

            employeeChartContainer.addView(row);
        }
    }

    // =========================================================
    // DEPARTMENT SUMMARY
    // =========================================================

    private void buildDepartmentSummary() {

        departmentSummaryContainer.removeAllViews();

        TextView title =
                createTitleText(
                        "DEPARTMENT / ROLE SUMMARY"
                );

        departmentSummaryContainer.addView(title);

        addDepartmentSummary(
                "STAFF OFFICER"
        );

        addDepartmentSummary(
                "SALES OFFICER"
        );

        addDepartmentSummary(
                "HOTEL STAFF"
        );
    }

    private void addDepartmentSummary(
            String role
    ) {

        List<EmployeeData> list =
                getEmployeesByRole(role);

        int total = list.size();

        int onTime = 0;
        int late = 0;
        int absent = 0;
        int checkedOut = 0;

        for (EmployeeData employee : list) {

            AttendanceData attendance =
                    attendanceMap.get(employee.id);

            if (attendance == null ||
                    attendance.checkIn.isEmpty()) {

                absent++;

                continue;
            }

            if (isLate(attendance.checkIn)) {
                late++;
            } else {
                onTime++;
            }

            if (!attendance.checkOut.isEmpty()) {
                checkedOut++;
            }
        }

        TextView summary =
                createNormalText(
                        role +
                                "\nTotal: " + total +
                                "   On Time: " + onTime +
                                "   Late: " + late +
                                "   Absent: " + absent +
                                "   Checked Out: " +
                                checkedOut
                );

        summary.setPadding(
                8,
                10,
                8,
                10
        );

        departmentSummaryContainer.addView(
                summary
        );
    }

    // =========================================================
    // ROLE SECTIONS
    // =========================================================

    private void buildRoleSections() {

        roleContainer.removeAllViews();

        addRoleSection(
                "STAFF OFFICER"
        );

        addRoleSection(
                "SALES OFFICER"
        );

        addRoleSection(
                "HOTEL STAFF"
        );
    }

    private void addRoleSection(
            String role
    ) {

        List<EmployeeData> roleEmployees =
                getEmployeesByRole(role);

        LinearLayout section =
                new LinearLayout(this);

        section.setOrientation(
                LinearLayout.VERTICAL
        );

        section.setPadding(
                10,
                15,
                10,
                15
        );

        TextView title =
                createTitleText(role);

        section.addView(title);

        int total =
                roleEmployees.size();

        int onTime = 0;
        int late = 0;
        int absent = 0;
        int checkedOut = 0;

        for (EmployeeData employee :
                roleEmployees) {

            AttendanceData attendance =
                    attendanceMap.get(employee.id);

            if (attendance == null ||
                    attendance.checkIn.isEmpty()) {

                absent++;

            } else if (isLate(
                    attendance.checkIn
            )) {

                late++;

            } else {

                onTime++;
            }

            if (attendance != null &&
                    !attendance.checkOut.isEmpty()) {

                checkedOut++;
            }
        }

        TextView summary =
                createNormalText(
                        "Total: " + total +
                                "    On Time: " + onTime +
                                "    Late: " + late +
                                "    Absent: " + absent +
                                "    Checked Out: " +
                                checkedOut
                );

        summary.setPadding(
                8,
                5,
                8,
                15
        );

        section.addView(summary);

        addTableHeader(section);

        if (roleEmployees.isEmpty()) {

            section.addView(
                    createNormalText(
                            "No employees in this role."
                    )
            );

        } else {

            Collections.sort(
                    roleEmployees,
                    Comparator.comparing(
                            e -> e.name.toLowerCase(
                                    Locale.getDefault()
                            )
                    )
            );

            for (EmployeeData employee :
                    roleEmployees) {

                section.addView(
                        createEmployeeRow(
                                employee
                        )
                );
            }
        }

        roleContainer.addView(section);
    }

    private void addTableHeader(
            LinearLayout section
    ) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setPadding(
                5,
                8,
                5,
                8
        );

        addHeaderCell(
                row,
                "Name",
                2.0f
        );

        addHeaderCell(
                row,
                "Check In",
                1.2f
        );

        addHeaderCell(
                row,
                "Late",
                0.9f
        );

        addHeaderCell(
                row,
                "Absent",
                0.9f
        );

        addHeaderCell(
                row,
                "Check Out",
                1.2f
        );

        addHeaderCell(
                row,
                "Working",
                1.1f
        );

        section.addView(row);
    }

    private void addHeaderCell(
            LinearLayout row,
            String text,
            float weight
    ) {

        TextView tv =
                new TextView(this);
            tv.setTextColor(color(R.color.shiftnest_text));

        tv.setText(text);

        tv.setTextSize(11);

        tv.setTypeface(
                null,
                Typeface.BOLD
        );

        tv.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        weight
                );

        row.addView(
                tv,
                params
        );
    }

    private View createEmployeeRow(
            EmployeeData employee
    ) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setPadding(
                5,
                10,
                5,
                10
        );

        AttendanceData attendance =
                attendanceMap.get(employee.id);

        String checkIn = "";
        String late = "";
        String absent = "";
        String checkOut = "";
        String workingTime = "";

        if (attendance == null ||
                attendance.checkIn.isEmpty()) {

            absent = "✓";

        } else {

            if (isLate(attendance.checkIn)) {

                late = formatTime(
                        attendance.checkIn
                );

            } else {

                checkIn = formatTime(
                        attendance.checkIn
                );
            }

            if (!attendance.checkOut.isEmpty()) {

                checkOut = formatTime(
                        attendance.checkOut
                );

                workingTime =
                        calculateWorkingTime(
                                attendance.checkIn,
                                attendance.checkOut
                        );
            }
        }

        addBodyCell(
                row,
                employee.name,
                2.0f
        );

        addBodyCell(
                row,
                checkIn,
                1.2f
        );

        addBodyCell(
                row,
                late,
                0.9f
        );

        addBodyCell(
                row,
                absent,
                0.9f
        );

        addBodyCell(
                row,
                checkOut,
                1.2f
        );

        addBodyCell(
                row,
                workingTime,
                1.1f
        );

        return row;
    }

    private void addBodyCell(
            LinearLayout row,
            String text,
            float weight
    ) {

        TextView tv =
                new TextView(this);
            tv.setTextColor(color(R.color.shiftnest_text));

        tv.setText(
                text == null ? "" : text
        );

        tv.setTextSize(10);

        tv.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        weight
                );

        row.addView(
                tv,
                params
        );
    }

    // =========================================================
    // ROLE FILTER
    // =========================================================

    private List<EmployeeData> getEmployeesByRole(
            String role
    ) {

        List<EmployeeData> result =
                new ArrayList<>();

        for (EmployeeData employee : employees) {

            if (normalizeRole(employee.role)
                    .equalsIgnoreCase(
                            normalizeRole(role)
                    )) {

                result.add(employee);
            }
        }

        return result;
    }

    // =========================================================
    // PDF - DOWNLOAD
    // =========================================================

    private void generateAndDownloadPdf(){createDailyPdf(false);}
    private void generateAndSharePdf(){createDailyPdf(true);}
    private void createDailyPdf(boolean share){
        if(!reportReady || creatingDailyPdf){toast("Load the report first, or wait for the current PDF.");return;}
        creatingDailyPdf=true;btnDownloadPdf.setEnabled(false);btnSharePdf.setEnabled(false);toast("Creating PDF...");
        new Thread(()->{
            try {
                File pdfFile=createPdfFile();
                runOnUiThread(()->{
                    creatingDailyPdf=false;if(isFinishing()||isDestroyed())return;
                    btnDownloadPdf.setEnabled(reportReady);btnSharePdf.setEnabled(reportReady);
                    if(!share){exportExistingPdf(pdfFile,"Daily_Attendance_"+todayDate);return;}
                    try {
                        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pdfFile);
                        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("application/pdf");intent.putExtra(Intent.EXTRA_STREAM,uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);toast("PDF prepared successfully. Choose an app to share it.");startActivity(Intent.createChooser(intent,"Share Attendance PDF"));
                    } catch(Exception e){problem("PDF could not be shared. Please try again.");}
                });
            } catch(Exception e){runOnUiThread(()->{creatingDailyPdf=false;if(isFinishing()||isDestroyed())return;btnDownloadPdf.setEnabled(reportReady);btnSharePdf.setEnabled(reportReady);problem("PDF could not be created. Please try again.");});}
        },"ShiftNestDailyPdf").start();
    }

    private File createPdfFile() throws Exception {

        File reportsDir =
                new File(
                        getExternalFilesDir(
                                Environment.DIRECTORY_DOCUMENTS
                        ),
                        "AttendanceReports"
                );

        if (!reportsDir.exists()) {

            if (!reportsDir.mkdirs()) {

                throw new Exception(
                        "Could not create report folder"
                );
            }
        }

        String fileName =
                "ShiftNest_Attendance_" +
                        todayDate +
                        ".pdf";

        File pdfFile =
                new File(
                        reportsDir,
                        fileName
                );

        PdfDocument document =
                new PdfDocument();

        try {

            drawFullPdf(
                    document
            );

            FileOutputStream outputStream =
                    new FileOutputStream(
                            pdfFile
                    );

            document.writeTo(
                    outputStream
            );

            outputStream.flush();

            outputStream.close();

        } finally {

            document.close();
        }

        return pdfFile;
    }

    // =========================================================
    // FULL PDF
    // =========================================================

    private void drawFullPdf(
            PdfDocument document
    ) {

        int pageWidth = 595;
        int pageHeight = 842;

        int pageNumber = 1;

        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        pageNumber
                ).create();

        PdfDocument.Page page =
                document.startPage(
                        pageInfo
                );

        Canvas canvas =
                page.getCanvas();

        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        int y = 35;

        y = drawPdfHeader(
                canvas,
                paint,
                y,
                pageWidth
        );

        y = drawPdfOverall(
                canvas,
                paint,
                y
        );

        y = drawPdfRoleTable(
                document,
                page,
                canvas,
                paint,
                y,
                "STAFF OFFICER",
                getEmployeesByRole(
                        "STAFF OFFICER"
                ),
                pageNumber
        );

        // drawPdfRoleTable can return a new page
        canvas = currentCanvas;
        page = currentPage;

        y = drawPdfRoleTable(
                document,
                page,
                canvas,
                paint,
                y,
                "SALES OFFICER",
                getEmployeesByRole(
                        "SALES OFFICER"
                ),
                currentPageNumber
        );

        canvas = currentCanvas;
        page = currentPage;

        y = drawPdfRoleTable(
                document,
                page,
                canvas,
                paint,
                y,
                "HOTEL STAFF",
                getEmployeesByRole(
                        "HOTEL STAFF"
                ),
                currentPageNumber
        );

        document.finishPage(
                currentPage
        );
        drawLateMessagePages(document);
        drawPlaceDetailsPages(document);
    }

    private Canvas currentCanvas;
    private PdfDocument.Page currentPage;
    private int currentPageNumber = 1;

    // =========================================================
    // PDF HEADER
    // =========================================================

    private int drawPdfHeader(
            Canvas canvas,
            Paint paint,
            int y,
            int pageWidth
    ) {

        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        paint.setTextSize(24);

        canvas.drawText(
                "SHIFTNEST ATTENDANCE",
                40,
                y,
                paint
        );

        y += 30;

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(13);

        canvas.drawText(
                "Daily Attendance Report",
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "Date: " + todayDate,
                40,
                y,
                paint
        );

        y += 25;

        paint.setStrokeWidth(2);

        canvas.drawLine(
                40,
                y,
                pageWidth - 40,
                y,
                paint
        );

        return y + 25;
    }

    // =========================================================
    // PDF OVERALL
    // =========================================================

    private int drawPdfOverall(
            Canvas canvas,
            Paint paint,
            int y
    ) {

        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        paint.setTextSize(17);

        canvas.drawText(
                "COMPANY OVERALL",
                40,
                y,
                paint
        );

        y += 25;

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(12);

        canvas.drawText(
                "Overall Total: " +
                        overallTotal,
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "Check In: " +
                        (overallOnTime +
                                overallLate),
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "On Time: " +
                        overallOnTime,
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "Late: " +
                        overallLate,
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "Absent: " +
                        overallAbsent,
                40,
                y,
                paint
        );

        y += 20;

        canvas.drawText(
                "Checked Out: " +
                        overallCheckedOut,
                40,
                y,
                paint
        );

        y += 25;

        paint.setTextSize(11);

        canvas.drawText(
                "Attendance Summary",
                40,
                y,
                paint
        );

        y += 20;

        int maxBarWidth = 450;

        y = drawPdfBar(
                canvas,
                paint,
                "Check In",
                overallOnTime + overallLate,
                overallTotal,
                y,
                maxBarWidth
        );

        y = drawPdfBar(
                canvas,
                paint,
                "Late",
                overallLate,
                overallTotal,
                y,
                maxBarWidth
        );

        y = drawPdfBar(
                canvas,
                paint,
                "Absent",
                overallAbsent,
                overallTotal,
                y,
                maxBarWidth
        );

        y = drawPdfBar(
                canvas,
                paint,
                "Checked Out",
                overallCheckedOut,
                overallTotal,
                y,
                maxBarWidth
        );

        return y + 20;
    }

    private int drawPdfBar(
            Canvas canvas,
            Paint paint,
            String label,
            int value,
            int total,
            int y,
            int maxWidth
    ) {

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(10);

        canvas.drawText(
                label + " - " + value,
                40,
                y,
                paint
        );

        y += 7;

        paint.setStyle(
                Paint.Style.STROKE
        );

        canvas.drawRect(
                40,
                y,
                40 + maxWidth,
                y + 12,
                paint
        );

        paint.setStyle(
                Paint.Style.FILL
        );

        int width = 0;

        if (total > 0) {

            width =
                    (int)
                            (((float) value /
                                    total) *
                                    maxWidth);
        }

        if (width > 0) {

            canvas.drawRect(
                    40,
                    y,
                    40 + width,
                    y + 12,
                    paint
            );
        }

        return y + 25;
    }

    // =========================================================
    // PDF ROLE TABLE
    // =========================================================

    private int drawPdfRoleTable(
            PdfDocument document,
            PdfDocument.Page page,
            Canvas canvas,
            Paint paint,
            int y,
            String role,
            List<EmployeeData> roleEmployees,
            int pageNumber
    ) {

        currentPage = page;
        currentCanvas = canvas;
        currentPageNumber = pageNumber;

        if (y > 700) {

            document.finishPage(
                    currentPage
            );

            currentPageNumber++;

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(
                            595,
                            842,
                            currentPageNumber
                    ).create();

            currentPage =
                    document.startPage(
                            pageInfo
                    );

            currentCanvas =
                    currentPage.getCanvas();

            currentPageNumber =
                    pageNumber + 1;

            canvas =
                    currentCanvas;

            y = 45;
        }

        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        paint.setTextSize(16);

        canvas.drawText(
                role,
                40,
                y,
                paint
        );

        y += 25;

        int total =
                roleEmployees.size();

        int onTime = 0;
        int late = 0;
        int absent = 0;
        int checkedOut = 0;

        for (EmployeeData employee :
                roleEmployees) {

            AttendanceData attendance =
                    attendanceMap.get(
                            employee.id
                    );

            if (attendance == null ||
                    attendance.checkIn.isEmpty()) {

                absent++;

            } else if (
                    isLate(
                            attendance.checkIn
                    )
            ) {

                late++;

            } else {

                onTime++;
            }

            if (attendance != null &&
                    !attendance.checkOut.isEmpty()) {

                checkedOut++;
            }
        }

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(9);

        canvas.drawText(
                "Total: " + total +
                        "   On Time: " + onTime +
                        "   Late: " + late +
                        "   Absent: " + absent +
                        "   Checked Out: " +
                        checkedOut,
                40,
                y,
                paint
        );

        y += 20;

        y = drawPdfTableHeader(
                canvas,
                paint,
                y
        );

        for (EmployeeData employee :
                roleEmployees) {

            if (y > 780) {

                document.finishPage(
                        currentPage
                );

                currentPageNumber++;

                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(
                                595,
                                842,
                                currentPageNumber
                        ).create();

                currentPage =
                        document.startPage(
                                pageInfo
                        );

                currentCanvas =
                        currentPage.getCanvas();

                canvas =
                        currentCanvas;

                y = 45;

                paint.setTypeface(
                        Typeface.create(
                                Typeface.DEFAULT,
                                Typeface.BOLD
                        )
                );

                paint.setTextSize(14);

                canvas.drawText(
                        role + " - Continued",
                        40,
                        y,
                        paint
                );

                y += 25;

                y = drawPdfTableHeader(
                        canvas,
                        paint,
                        y
                );
            }

            y = drawPdfEmployeeRow(
                    canvas,
                    paint,
                    y,
                    employee
            );
        }

        y += 20;

        currentCanvas = canvas;
        currentPage = page;

        return y;
    }

    private int drawPdfTableHeader(
            Canvas canvas,
            Paint paint,
            int y
    ) {

        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        paint.setTextSize(8);

        canvas.drawText(
                "Name",
                40,
                y,
                paint
        );

        canvas.drawText(
                "Check In",
                185,
                y,
                paint
        );

        canvas.drawText(
                "Late",
                255,
                y,
                paint
        );

        canvas.drawText(
                "Absent",
                300,
                y,
                paint
        );

        canvas.drawText(
                "Check Out",
                350,
                y,
                paint
        );

        canvas.drawText(
                "Working",
                445,
                y,
                paint
        );

        paint.setStrokeWidth(1);

        canvas.drawLine(
                40,
                y + 5,
                555,
                y + 5,
                paint
        );

        return y + 20;
    }

    private int drawPdfEmployeeRow(
            Canvas canvas,
            Paint paint,
            int y,
            EmployeeData employee
    ) {

        AttendanceData attendance =
                attendanceMap.get(
                        employee.id
                );

        String checkIn = "";
        String late = "";
        String absent = "";
        String checkOut = "";
        String working = "";

        if (attendance == null ||
                attendance.checkIn.isEmpty()) {

            absent = "✓";

        } else if (
                isLate(
                        attendance.checkIn
                )
        ) {

            late =
                    formatTime(
                            attendance.checkIn
                    );

        } else {

            checkIn =
                    formatTime(
                            attendance.checkIn
                    );
        }

        if (attendance != null &&
                !attendance.checkOut.isEmpty()) {

            checkOut =
                    formatTime(
                            attendance.checkOut
                    );

            working =
                    calculateWorkingTime(
                            attendance.checkIn,
                            attendance.checkOut
                    );
        }

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(7);

        canvas.drawText(
                shorten(
                        employee.name,
                        23
                ),
                40,
                y,
                paint
        );

        canvas.drawText(
                checkIn,
                185,
                y,
                paint
        );

        canvas.drawText(
                late,
                255,
                y,
                paint
        );

        canvas.drawText(
                absent,
                300,
                y,
                paint
        );

        canvas.drawText(
                checkOut,
                350,
                y,
                paint
        );

        canvas.drawText(
                working,
                445,
                y,
                paint
        );

        return y + 22;
    }

    // =========================================================
    // DOWNLOAD PDF TO DOWNLOADS
    // =========================================================

    private void savePdfToDownloads(
            File pdfFile
    ) throws Exception {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            ContentValues values =
                    new ContentValues();

            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    pdfFile.getName()
            );

            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    "application/pdf"
            );

            values.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                            "/ShiftNest Attendance"
            );

            Uri uri =
                    getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );

            if (uri == null) {

                throw new Exception(
                        "Could not create download file"
                );
            }

            try (
                    InputStream inputStream =
                            new java.io.FileInputStream(
                                    pdfFile
                            );

                    OutputStream outputStream =
                            getContentResolver()
                                    .openOutputStream(uri)
            ) {

                if (outputStream == null) {

                    throw new Exception(
                            "Could not open download output"
                    );
                }

                byte[] buffer =
                        new byte[8192];

                int length;

                while (
                        (length =
                                inputStream.read(buffer))
                                > 0
                ) {

                    outputStream.write(
                            buffer,
                            0,
                            length
                    );
                }

                outputStream.flush();
            }

        } else {

            File downloads =
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                    );

            File folder =
                    new File(
                            downloads,
                            "ShiftNest Attendance"
                    );

            if (!folder.exists()) {
                folder.mkdirs();
            }

            File destination =
                    new File(
                            folder,
                            pdfFile.getName()
                    );

            try (
                    InputStream inputStream =
                            new java.io.FileInputStream(
                                    pdfFile
                            );

                    OutputStream outputStream =
                            new FileOutputStream(
                                    destination
                            )
            ) {

                byte[] buffer =
                        new byte[8192];

                int length;

                while (
                        (length =
                                inputStream.read(buffer))
                                > 0
                ) {

                    outputStream.write(
                            buffer,
                            0,
                            length
                    );
                }

                outputStream.flush();
            }
        }

        Toast.makeText(
                this,
                "PDF saved to Downloads/ShiftNest Attendance",
                Toast.LENGTH_LONG
        ).show();
    }

    // =========================================================
    // TIME / STATUS
    // =========================================================

    private boolean isLate(
            String time
    ) {

        if (time == null ||
                time.trim().isEmpty()) {

            return false;
        }

        return time.compareTo(
                ON_TIME_CUTOFF
        ) >= 0;
    }

    private String formatTime(
            String time
    ) {

        if (time == null ||
                time.trim().isEmpty()) {

            return "";
        }

        try {

            SimpleDateFormat input =
                    new SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                    );

            SimpleDateFormat output =
                    new SimpleDateFormat(
                            "hh:mm a",
                            Locale.getDefault()
                    );

            Date date =
                    input.parse(time);

            if (date == null) {
                return time;
            }

            return output.format(date);

        } catch (Exception e) {

            return time;
        }
    }

    private String calculateWorkingTime(
            String checkIn,
            String checkOut
    ) {

        if (checkIn == null ||
                checkOut == null ||
                checkIn.isEmpty() ||
                checkOut.isEmpty()) {

            return "";
        }

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                    );

            Date in =
                    format.parse(checkIn);

            Date out =
                    format.parse(checkOut);

            if (in == null ||
                    out == null) {

                return "";
            }

            long difference =
                    out.getTime() -
                            in.getTime();

            if (difference < 0) {

                difference +=
                        24 * 60 * 60 * 1000L;
            }

            long totalMinutes =
                    difference /
                            (60 * 1000);

            long hours =
                    totalMinutes / 60;

            long minutes =
                    totalMinutes % 60;

            return hours +
                    "h " +
                    minutes +
                    "m";

        } catch (ParseException e) {

            return "";
        }
    }

    // =========================================================
    // ROLE NORMALIZATION
    // =========================================================

    private String normalizeRole(
            String role
    ) {

        if (role == null) {
            return "";
        }

        String value =
                role.trim()
                        .toUpperCase(
                                Locale.getDefault()
                        );

        if (value.equals(
                "HOTEL OFFICER"
        )) {

            return "HOTEL STAFF";
        }

        if (value.equals(
                "HOTEL STAFF"
        )) {

            return "HOTEL STAFF";
        }

        if (value.equals(
                "STAFF OFFICER"
        )) {

            return "STAFF OFFICER";
        }

        if (value.equals(
                "SALES OFFICER"
        )) {

            return "SALES OFFICER";
        }

        return value;
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private TextView createTitleText(
            String text
    ) {

        TextView tv =
                new TextView(this);
            tv.setTextColor(color(R.color.shiftnest_text));

        tv.setText(text);

        tv.setTextSize(18);

        tv.setTextColor(
                color(R.color.shiftnest_heading)
        );

        tv.setTypeface(
                null,
                Typeface.BOLD
        );

        tv.setPadding(
                5,
                5,
                5,
                10
        );

        return tv;
    }

    private TextView createNormalText(
            String text
    ) {

        TextView tv =
                new TextView(this);
            tv.setTextColor(color(R.color.shiftnest_text));

        tv.setText(text);

        tv.setTextSize(14);

        tv.setTextColor(
                color(R.color.shiftnest_text)
        );

        return tv;
    }

    /** Full explanations are paginated rather than shortened in the daily/morning PDF. */
    private void drawLateMessagePages(PdfDocument document){
        if(lateMessages.isEmpty())return;
        List<String> lines=new ArrayList<>();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10);
        for(DocumentSnapshot note:sortedLateMessages()){
            String block=ShiftNestData.string(note,"employeeName")+" • "+ShiftNestData.string(note,"employeeRole")+"\nLate message: "+ShiftNestData.string(note,"lateMessage")+"\nSubmitted: "+(ShiftNestData.timestamp(note,"updatedAt")==null?"--":ShiftNestData.format(ShiftNestData.timestamp(note,"updatedAt"),"yyyy-MM-dd HH:mm:ss"))+"\n";
            for(String paragraph:block.split("\n",-1)){if(paragraph.isEmpty()){lines.add("");continue;}String rest=paragraph;while(!rest.isEmpty()){int count=Math.max(1,p.breakText(rest,true,515,null));lines.add(rest.substring(0,count));rest=rest.substring(count);}}
        }
        int offset=0;while(offset<lines.size()){
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++currentPageNumber).create());Canvas canvas=page.getCanvas();p.setTextSize(14);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("Late check-in messages • "+todayDate,40,40,p);p.setTextSize(9);p.setTypeface(Typeface.DEFAULT);canvas.drawText("Employee explanations; attendance status remains based on check-in.",40,60,p);p.setTextSize(10);int y=88;
            while(offset<lines.size()&&y<790){canvas.drawText(lines.get(offset++),40,y,p);y+=15;}
            document.finishPage(page);
        }
    }

    private String placeLabel(String value){return value==null||value.trim().isEmpty()?"--":value;}
    private List<AttendanceData> sortedPlaceDetails(){
        List<AttendanceData> list=new ArrayList<>(attendanceMap.values());
        list.sort(java.util.Comparator.comparing((AttendanceData a)->a.employeeName).thenComparing(a->a.employeeId));return list;
    }
    private void buildPlaceDetails(){
        roleContainer.addView(text("Check-in / Check-out site details • "+todayDate,20));
        List<String[]> rows=new ArrayList<>();
        for(AttendanceData a:sortedPlaceDetails())rows.add(new String[]{a.employeeName,placeLabel(a.checkInSiteName),placeLabel(a.checkInLocationName),placeLabel(a.checkOutSiteName),placeLabel(a.checkOutLocationName)});
        if(rows.isEmpty())roleContainer.addView(text("No saved attendance site details for this date.",16));
        else roleContainer.addView(ShiftNestTable.create(this,new String[]{"Employee","In site","In location","Out site","Out location"},rows,null));
        roleContainer.addView(text("These are typed attendance labels. Older records may show --. The charts above count attendance status.",14));
    }
    private void drawPlaceDetailsPages(PdfDocument document){
        if(attendanceMap.isEmpty())return;
        List<String> lines=new ArrayList<>();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10);
        for(AttendanceData a:sortedPlaceDetails()){
            String block=a.employeeName+" • "+a.employeeRole+"\nCheck-in "+placeLabel(a.checkIn)+" | Site: "+placeLabel(a.checkInSiteName)+" | Location: "+placeLabel(a.checkInLocationName)+"\nCheck-out "+placeLabel(a.checkOut)+" | Site: "+placeLabel(a.checkOutSiteName)+" | Location: "+placeLabel(a.checkOutLocationName)+"\n";
            for(String paragraph:block.split("\n",-1)){if(paragraph.isEmpty()){lines.add("");continue;}String rest=paragraph;while(!rest.isEmpty()){int count=Math.max(1,p.breakText(rest,true,515,null));lines.add(rest.substring(0,count));rest=rest.substring(count);}}
        }
        int offset=0;while(offset<lines.size()){
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++currentPageNumber).create());Canvas canvas=page.getCanvas();p.setTextSize(14);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("Attendance site details • "+todayDate,40,40,p);p.setTextSize(9);p.setTypeface(Typeface.DEFAULT);canvas.drawText("Typed check-in/out labels; -- means no value was recorded.",40,60,p);p.setTextSize(10);int y=88;
            while(offset<lines.size()&&y<790){canvas.drawText(lines.get(offset++),40,y,p);y+=15;}document.finishPage(page);
        }
    }

    private String safeString(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    private String shorten(
            String value,
            int max
    ) {

        if (value == null) {
            return "";
        }

        if (value.length() <= max) {
            return value;
        }

        return value.substring(
                0,
                max - 3
        ) + "...";
    }

    // =========================================================
    // DATA CLASSES
    // =========================================================

    private static class EmployeeData {

        String id = "";
        String name = "";
        String phone = "";
        String role = "";
        String locationName = "";
    }

    private static class AttendanceData {

        String employeeId = "";
        String employeeName = "";
        String employeeRole = "";
        String phone = "";
        String date = "";
        String checkIn = "";
        String checkOut = "";
        String status = "";
        String workStatus = "";
        String locationName = "";
        String checkInSiteName="",checkInLocationName="",checkOutSiteName="",checkOutLocationName="";
    }
}