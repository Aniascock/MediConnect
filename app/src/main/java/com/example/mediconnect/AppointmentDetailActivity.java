package com.example.mediconnect;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppointmentDetailActivity extends AppCompatActivity {

    private TextView tvDoctorName, tvSpecialty, tvHospital, tvDate, tvTime;
    private TextView tvType, tvStatus, tvNotes, tvDoctorInitials;
    private View     statusIndicator;
    private MaterialButton cancelButton, rescheduleButton;
    private View     actionButtonsContainer;

    private String appointmentId, currentStatus;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment_detail);

        db = FirebaseFirestore.getInstance();

        appointmentId = getIntent().getStringExtra("appointment_id");
        String doctorName    = getIntent().getStringExtra("doctor_name");
        String specialty     = getIntent().getStringExtra("doctor_specialty");
        String hospital      = getIntent().getStringExtra("hospital");
        String date          = getIntent().getStringExtra("date");
        String time          = getIntent().getStringExtra("time");
        String type          = getIntent().getStringExtra("type");
        currentStatus        = getIntent().getStringExtra("status");
        String notes         = getIntent().getStringExtra("notes");

        bindViews();
        populate(doctorName, specialty, hospital, date, time, type, currentStatus, notes);
        setupButtons();
    }

    private void bindViews() {
        tvDoctorInitials      = findViewById(R.id.detail_doctor_initials);
        tvDoctorName          = findViewById(R.id.detail_doctor_name);
        tvSpecialty           = findViewById(R.id.detail_specialty);
        tvHospital            = findViewById(R.id.detail_hospital);
        tvDate                = findViewById(R.id.detail_date);
        tvTime                = findViewById(R.id.detail_time);
        tvType                = findViewById(R.id.detail_type);
        tvStatus              = findViewById(R.id.detail_status);
        statusIndicator       = findViewById(R.id.detail_status_indicator);
        tvNotes               = findViewById(R.id.detail_notes);
        cancelButton          = findViewById(R.id.cancel_button);
        rescheduleButton      = findViewById(R.id.reschedule_button);

        actionButtonsContainer= findViewById(R.id.action_buttons_container);

        // Back button
        View backBtn = findViewById(R.id.back_button);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    private void populate(String doctorName, String specialty, String hospital,
                          String date, String time, String type, String status, String notes) {
        // Format name
        String name = (doctorName != null && !doctorName.isEmpty()) ? doctorName : "Doctor";
        if (!name.startsWith("Dr.")) name = "Dr. " + name;
        tvDoctorName.setText(name);

        // Initials
        tvDoctorInitials.setText(getInitials(doctorName));

        tvSpecialty.setText(specialty != null ? specialty : "");
        tvHospital.setText(hospital   != null ? hospital  : "");
        tvDate.setText(date           != null ? date      : "—");
        tvTime.setText(time           != null ? time      : "—");
        tvType.setText(type           != null ? type      : "Consultation");
        tvNotes.setText((notes != null && !notes.isEmpty()) ? notes : "No additional notes.");

        // Status styling
        if (status == null) status = "pending";
        switch (status.toLowerCase()) {
            case "confirmed":
                tvStatus.setText("Confirmed");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_available));
                statusIndicator.setBackgroundResource(R.drawable.bg_status_available);
                break;
            case "completed":
                tvStatus.setText("Completed");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
                statusIndicator.setBackgroundResource(R.drawable.bg_status_completed);
                break;
            case "cancelled":
                tvStatus.setText("Cancelled");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_busy));
                statusIndicator.setBackgroundResource(R.drawable.bg_status_busy);
                break;
            default:
                tvStatus.setText("Pending");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_orange));
                statusIndicator.setBackgroundResource(R.drawable.bg_status_away);
        }
    }

    private void setupButtons() {
        if (currentStatus == null) return;
        String type = getIntent().getStringExtra("type");

        switch (currentStatus.toLowerCase()) {
            case "confirmed":
            case "pending":
                actionButtonsContainer.setVisibility(View.VISIBLE);
                cancelButton.setVisibility(View.VISIBLE);
                rescheduleButton.setVisibility(View.VISIBLE);
                boolean isVideo = type != null &&
                        (type.toLowerCase().contains("video") || type.toLowerCase().contains("online"));

                break;
            case "completed":
                actionButtonsContainer.setVisibility(View.GONE);

                break;
            case "cancelled":
                actionButtonsContainer.setVisibility(View.VISIBLE);
                cancelButton.setVisibility(View.GONE);
                rescheduleButton.setVisibility(View.VISIBLE);

                break;
        }

        cancelButton.setOnClickListener(v -> confirmCancel());
        rescheduleButton.setOnClickListener(v ->
                Toast.makeText(this, "Reschedule — coming soon", Toast.LENGTH_SHORT).show());

    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Appointment")
                .setMessage("Are you sure you want to cancel this appointment?")
                .setPositiveButton("Yes, Cancel", (d, w) -> cancelAppointment())
                .setNegativeButton("No, Keep It", null)
                .show();
    }

    private void cancelAppointment() {
        if (appointmentId == null) return;
        db.collection("appointments").document(appointmentId)
                .update("status", "cancelled")
                .addOnSuccessListener(v -> {
                    currentStatus = "cancelled";
                    tvStatus.setText("Cancelled");
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_busy));
                    statusIndicator.setBackgroundResource(R.drawable.bg_status_busy);
                    actionButtonsContainer.setVisibility(View.VISIBLE);
                    cancelButton.setVisibility(View.GONE);

                    Toast.makeText(this, "Appointment cancelled", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String clean = name.replaceAll("(?i)^Dr\\.?\\s*", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length == 0) return "?";
        if (parts.length == 1)
            return String.valueOf(Character.toUpperCase(parts[0].charAt(0)));
        return String.valueOf(Character.toUpperCase(parts[0].charAt(0)))
             + String.valueOf(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
    }
}
