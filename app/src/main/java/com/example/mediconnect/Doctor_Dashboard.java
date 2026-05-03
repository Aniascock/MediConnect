package com.example.mediconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService;
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Doctor_Dashboard extends AppCompatActivity {

    private static final long   ZEGO_APP_ID   = 1986084612;
    private static final String ZEGO_APP_SIGN = "33bce858e0d1f726dc8ffbcfa2e7fea78120215f59c63b8cb6b281ce9d1e1adc";

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView tvDoctorName;
    private TextView tvAppointmentCount, tvPendingCount, tvPatientCount;
    private TextView tvAvailabilityLabel, tvViewAll;
    private SwitchMaterial switchAvailability;
    private View viewStatusDot;
    private View layoutEmptySchedule;
    private RecyclerView rvAppointments;
    private BottomNavigationView bottomNav;
    private com.example.mediconnect.PendingRequestsAdapter pendingAdapter;
    private final List<AppointmentItem> pendingList = new ArrayList<>();
    private View layoutEmptyPending;
    private RecyclerView rvPendingRequests;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private FirebaseFirestore firestore;

    // ── Adapter ────────────────────────────────────────────────────────────────
    private AppointmentScheduleAdapter scheduleAdapter;
    private final List<AppointmentItem> scheduleList = new ArrayList<>();

    private final String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(new Date());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);
        mAuth     = FirebaseAuth.getInstance();
        dbRef     = FirebaseDatabase.getInstance().getReference("Doctors");
        firestore = FirebaseFirestore.getInstance();

        bindViews();
        loadDoctorNameThenInitZego();
        loadDashboardStats();
        loadTodaySchedule();
        setupAvailabilityToggle();
        loadPendingRequests();

        tvViewAll.setOnClickListener(v -> {});

        setupBottomNav();
    }

    // ─── Bind Views ───────────────────────────────────────────────────────────

    private void bindViews() {
        tvDoctorName        = findViewById(R.id.tvDoctorName);
        tvAppointmentCount  = findViewById(R.id.tvAppointmentCount);
        tvPendingCount      = findViewById(R.id.tvPendingCount);
        tvPatientCount      = findViewById(R.id.tvPatientCount);
        tvAvailabilityLabel = findViewById(R.id.tvAvailabilityLabel);
        tvViewAll           = findViewById(R.id.tvViewAll);
        switchAvailability  = findViewById(R.id.switchAvailability);
        viewStatusDot       = findViewById(R.id.viewStatusDot);
        layoutEmptySchedule = findViewById(R.id.layoutEmptySchedule);
        rvAppointments      = findViewById(R.id.rvAppointments);
        bottomNav           = findViewById(R.id.bottomNav);

        scheduleAdapter = new AppointmentScheduleAdapter(scheduleList);
        rvAppointments.setLayoutManager(new LinearLayoutManager(this));
        rvAppointments.setAdapter(scheduleAdapter);
        rvAppointments.setNestedScrollingEnabled(false);


        layoutEmptyPending = findViewById(R.id.layoutEmptyPending);
        rvPendingRequests  = findViewById(R.id.rvPendingRequests);

        pendingAdapter = new com.example.mediconnect.PendingRequestsAdapter(pendingList, (item, position, action) -> {
            String newStatus = "accept".equals(action) ? "confirmed" : "cancelled";
            updateAppointmentStatus(item.appointmentId, newStatus, position);
        });
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        rvPendingRequests.setAdapter(pendingAdapter);
        rvPendingRequests.setNestedScrollingEnabled(false);
    }

    // ─── Load doctor name then init Zego ──────────────────────────────────────

    private void loadDoctorNameThenInitZego() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        dbRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String fullName = snapshot.child("fullName").getValue(String.class);
                if (fullName == null || fullName.isEmpty()) fullName = user.getUid();

                // Update UI
                String cleaned   = fullName.replace("Dr.", "").trim();
                String firstName = cleaned.split(" ")[0];
                tvDoctorName.setText("Dr. " + firstName);

                // Init Zego — doctor's Firebase UID = their Zego userID
                // This registers the doctor as reachable for incoming calls
                ZegoUIKitPrebuiltCallService.init(
                        getApplication(),
                        ZEGO_APP_ID,
                        ZEGO_APP_SIGN,
                        user.getUid(),
                        fullName,
                        new ZegoUIKitPrebuiltCallInvitationConfig()
                );
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(Doctor_Dashboard.this,
                        "Failed to load profile: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── Load Dashboard Stats ─────────────────────────────────────────────────

    private void loadDashboardStats() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        firestore.collection("appointments")
                .whereEqualTo("doctorId", uid)
                .whereEqualTo("date", todayDate)
                .whereEqualTo("status", "confirmed")
                .get()
                .addOnSuccessListener(q -> tvAppointmentCount.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> tvAppointmentCount.setText("0"));

        firestore.collection("appointments")
                .whereEqualTo("doctorId", uid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(q -> tvPendingCount.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> tvPendingCount.setText("0"));

        firestore.collection("appointments")
                .whereEqualTo("doctorId", uid)
                .get()
                .addOnSuccessListener(q -> {
                    android.util.Log.d("PATIENT_COUNT", "Documents found: " + q.size());
                    Set<String> patientIds = new HashSet<>();
                    for (DocumentSnapshot doc : q.getDocuments()) {
                        String pid = doc.getString("patientId");
                        if (pid != null) patientIds.add(pid);
                    }
                    android.util.Log.d("PATIENT_COUNT", "Unique patients: " + patientIds.size());
                    tvPatientCount.setText(String.valueOf(patientIds.size()));
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PATIENT_COUNT", "ERROR: " + e.getMessage());
                    tvPatientCount.setText("0");
                });
    }

    // ─── Load Today's Schedule ────────────────────────────────────────────────

    // ─── Load Today's Schedule ────────────────────────────────────────────────
// Replace your existing loadTodaySchedule() with this:

    private void loadTodaySchedule() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        firestore.collection("appointments")
                .whereEqualTo("doctorId", user.getUid())
                .whereEqualTo("date", todayDate)
                .whereEqualTo("status", "confirmed")   // ← ADD THIS FILTER
                .orderBy("time", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    scheduleList.clear();

                    if (querySnapshot.isEmpty()) {
                        layoutEmptySchedule.setVisibility(View.VISIBLE);
                        rvAppointments.setVisibility(View.GONE);
                        return;
                    }

                    layoutEmptySchedule.setVisibility(View.GONE);
                    rvAppointments.setVisibility(View.VISIBLE);

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        AppointmentItem item = new AppointmentItem();
                        item.appointmentId = doc.getString("appointmentId");
                        item.patientId     = doc.getString("patientId");
                        item.patientName   = doc.getString("patientName"); // stored at booking time
                        item.time          = doc.getString("time");
                        item.type          = doc.getString("type");
                        item.status        = doc.getString("status");
                        item.notes         = doc.getString("notes");
                        item.date          = doc.getString("date");

                        // If patientName wasn't saved on the appointment doc, fetch from RTDB
                        if (item.patientName == null || item.patientName.isEmpty()) {
                            fetchPatientNameAndAdd(item);
                        } else {
                            scheduleList.add(item);
                            scheduleAdapter.notifyItemInserted(scheduleList.size() - 1);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load schedule: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

// ─── Add below loadTodaySchedule() ───────────────────────────────────────

    private void fetchPatientNameAndAdd(AppointmentItem item) {
        if (item.patientId == null) {
            item.patientName = "Unknown Patient";
            scheduleList.add(item);
            scheduleAdapter.notifyItemInserted(scheduleList.size() - 1);
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(item.patientId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String first = snapshot.child("firstName").getValue(String.class);
                    String last  = snapshot.child("lastName").getValue(String.class);
                    String full  = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    item.patientName = full.isEmpty() ? item.patientId : full;
                    scheduleList.add(item);
                    scheduleAdapter.notifyItemInserted(scheduleList.size() - 1);
                })
                .addOnFailureListener(e -> {
                    item.patientName = item.patientId;
                    scheduleList.add(item);
                    scheduleAdapter.notifyItemInserted(scheduleList.size() - 1);
                });
    }
    // ─── Availability Toggle ──────────────────────────────────────────────────

    private void setupAvailabilityToggle() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        dbRef.child(user.getUid()).child("isAvailable")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Boolean isAvailable = snapshot.getValue(Boolean.class);
                        boolean available = isAvailable == null || isAvailable;
                        updateAvailabilityUI(available);
                        switchAvailability.setChecked(available);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });

        switchAvailability.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateAvailabilityUI(isChecked);
            dbRef.child(user.getUid()).child("isAvailable").setValue(isChecked)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this,
                                    isChecked ? "Now accepting patients." : "Availability turned off.",
                                    Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to update.", Toast.LENGTH_SHORT).show());
        });
    }


    // ─── Load Pending Requests ────────────────────────────────────────────────

    private void loadPendingRequests() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        firestore.collection("appointments")
                .whereEqualTo("doctorId", user.getUid())
                .whereEqualTo("status", "pending")
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    pendingList.clear();

                    if (querySnapshot.isEmpty()) {
                        layoutEmptyPending.setVisibility(View.VISIBLE);
                        rvPendingRequests.setVisibility(View.GONE);
                        return;
                    }

                    layoutEmptyPending.setVisibility(View.GONE);
                    rvPendingRequests.setVisibility(View.VISIBLE);

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        AppointmentItem item = new AppointmentItem();
                        item.appointmentId = doc.getString("appointmentId");
                        item.patientId     = doc.getString("patientId");
                        item.patientName   = doc.getString("patientName");
                        item.time          = doc.getString("time");
                        item.type          = doc.getString("type");
                        item.status        = doc.getString("status");
                        item.notes         = doc.getString("notes");
                        item.date          = doc.getString("date");

                        pendingList.add(item);
                        pendingAdapter.notifyItemInserted(pendingList.size() - 1);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load requests: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void updateAppointmentStatus(String appointmentId, String newStatus, int position) {
        firestore.collection("appointments")
                .document(appointmentId)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    pendingList.remove(position);
                    pendingAdapter.notifyItemRemoved(position);
                    pendingAdapter.notifyItemRangeChanged(position, pendingList.size());

                    if (pendingList.isEmpty()) {
                        layoutEmptyPending.setVisibility(View.VISIBLE);
                        rvPendingRequests.setVisibility(View.GONE);
                    }

                    String msg = "confirmed".equals(newStatus)
                            ? "Appointment confirmed!" : "Appointment declined.";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    // Refresh stats + today's schedule
                    loadDashboardStats();
                    loadTodaySchedule();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to update: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }


    private void updateAvailabilityUI(boolean isAvailable) {
        if (isAvailable) {
            tvAvailabilityLabel.setText("Accepting new patients");
            viewStatusDot.setBackgroundResource(R.drawable.circle_status_active);
        } else {
            tvAvailabilityLabel.setText("Not accepting new patients");
            viewStatusDot.setBackgroundResource(R.drawable.circle_status_inactive);
        }
    }





    // ─── Bottom Navigation ────────────────────────────────────────────────────

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home)     return true;
            if (id == R.id.nav_schedule) {
                Schedules();
                return true; }
            if (id == R.id.nav_patients) {
                PatientList();
            return true; }
            if (id == R.id.nav_profile)  {
                Profile();
                return true; }
            return false;
        });
    }


    public void Schedules() {
        Intent intent = new Intent(this, ScheduleActivity.class);
        startActivity(intent);
    }

    public void PatientList() {
        Intent intent = new Intent(this, PatientListActivity.class);
        startActivity(intent);
    }

    public void Profile() {
        Intent intent = new Intent(this, DoctorPersonalProfile.class);
        startActivity(intent);
    }




    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, Login.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ZegoUIKitPrebuiltCallService.unInit();
    }
}