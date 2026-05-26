package com.example.mediconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DoctorProfileActivity extends AppCompatActivity {

    private LinearLayout backButton;
    private TextView doctorName, doctorSpecialty, doctorHospital, doctorRating,
            doctorExperience, doctorStatus, aboutText, availableSlotsLabel, month;
    private LinearLayout videoCallButton, inPersonButton;
    private CardView confirmButton;
    private View statusDot;
    private LinearLayout calendarContainer, timeSlotsContainer;

    private String selectedDate = null;
    private String selectedDateDisplay = null;
    private String selectedTime = null;
    private String selectedConsultationType = "video";
    private String doctorUid;
    private List<String> availableDaysList = new ArrayList<>();
    private String consultationHoursRaw = "";
    private View lastSelectedDayView = null;
    private TextView lastSelectedTimeView = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        initializeViews();
        loadDoctorDataFromFirebase();
        setupClickListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        doctorName = findViewById(R.id.doctor_name_large);
        doctorSpecialty = findViewById(R.id.doctor_specialty_detail);
        doctorHospital = findViewById(R.id.doctor_hospital);
        doctorRating = findViewById(R.id.doctor_rating_detail);
        doctorExperience = findViewById(R.id.doctor_experience_detail);
        doctorStatus = findViewById(R.id.doctor_status_detail);
        statusDot = findViewById(R.id.status_dot_large);
        aboutText = findViewById(R.id.about_doctor_text);
        videoCallButton = findViewById(R.id.video_call_button);
        inPersonButton = findViewById(R.id.in_person_button);
        confirmButton = findViewById(R.id.confirm_appointment_button);
        calendarContainer = findViewById(R.id.calendar_days_container);
        timeSlotsContainer = findViewById(R.id.time_slots_container);
        availableSlotsLabel = findViewById(R.id.available_slots_label); // add this id to your XML TextView
        month = findViewById(R.id.month);

// Set to current month on load
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        month.setText(monthYearFormat.format(Calendar.getInstance().getTime()));
    }

    private void loadDoctorDataFromFirebase() {
        doctorUid = getIntent().getStringExtra("doctor_uid");

        if (doctorUid == null || doctorUid.isEmpty()) {
            Toast.makeText(this, "Doctor not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("Doctors")
                .child(doctorUid);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(DoctorProfileActivity.this, "Doctor data not found.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String name = snapshot.child("fullName").getValue(String.class);
                String specialty = snapshot.child("specialization").getValue(String.class);
                String clinic = snapshot.child("clinicName").getValue(String.class);
                String location = snapshot.child("location").getValue(String.class);
                Boolean isAvailable = snapshot.child("isAvailable").getValue(Boolean.class);
                String availableDays = snapshot.child("availableDays").getValue(String.class);
                String consultationHours = snapshot.child("consultationHours").getValue(String.class);
                String feeStr = snapshot.child("consultationFee").getValue(String.class);

                if (name == null) name = "Unknown Doctor";
                if (specialty == null) specialty = "General";
                if (clinic == null) clinic = "Unknown Clinic";
                if (location == null) location = "";
                if (isAvailable == null) isAvailable = false;
                if (availableDays == null) availableDays = "";
                if (consultationHours == null) consultationHours = "9:00 am - 5:00 pm";
                if (feeStr == null) feeStr = "0";

                consultationHoursRaw = consultationHours;
                parseAvailableDays(availableDays);

                // Set UI
                doctorName.setText(name);
                doctorSpecialty.setText(specialty);
                doctorHospital.setText(clinic + (location.isEmpty() ? "" : " · " + location));
                doctorRating.setText("★ 4.9"); // static or add rating field in DB
                doctorExperience.setText("Fee: ₱" + feeStr);

                if (isAvailable) {
                    statusDot.setBackgroundResource(R.drawable.bg_status_dot);
                    doctorStatus.setText("Available");
                    doctorStatus.setTextColor(ContextCompat.getColor(DoctorProfileActivity.this, R.color.status_normal));
                } else {
                    statusDot.setBackgroundResource(R.drawable.circle_status_inactive);
                    doctorStatus.setText("Busy");
                    doctorStatus.setTextColor(ContextCompat.getColor(DoctorProfileActivity.this, R.color.text_tertiary));
                }

                String displayName = name.startsWith("Dr. ") ? name.substring(4) : name;
                aboutText.setText("Dr. " + displayName + " is a " + specialty.toLowerCase()
                        + " available at " + clinic + ". Consultation fee: ₱" + feeStr
                        + ". Available on " + availableDays + ", " + consultationHours + ".");

                setupCalendar();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(DoctorProfileActivity.this,
                        "Failed to load doctor: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Parses "Saturday & Sunday" or "Monday, Wednesday, Friday" etc.
     * into a list of Calendar.DAY_OF_WEEK integers for comparison.
     */
    private void parseAvailableDays(String availableDays) {
        availableDaysList.clear();
        if (availableDays == null || availableDays.isEmpty()) return;

        String[] parts = availableDays.split("[,&]");
        for (String part : parts) {
            availableDaysList.add(part.trim().toLowerCase(Locale.getDefault()));
        }
    }

    private boolean isDayAvailable(Calendar cal) {
        if (availableDaysList.isEmpty()) return false;

        String[] dayNames = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sunday, 7=Saturday
        String dayName = dayNames[dayOfWeek - 1];

        for (String available : availableDaysList) {
            if (available.contains(dayName) || dayName.contains(available.replace("day", ""))) {
                return true;
            }
        }
        return false;
    }

    private void setupCalendar() {
        calendarContainer.removeAllViews();

        Calendar cal = Calendar.getInstance();
        // Show next 30 days
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        SimpleDateFormat dayNumFormat = new SimpleDateFormat("d", Locale.getDefault());
        SimpleDateFormat dayWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        for (int i = 0; i < 30; i++) {
            Calendar day = (Calendar) cal.clone();
            day.add(Calendar.DAY_OF_MONTH, i);

            boolean available = isDayAvailable(day);

            View dayView = getLayoutInflater().inflate(R.layout.item_calendar_day, calendarContainer, false);
            TextView dayNumber = dayView.findViewById(R.id.day_number);
            TextView dayWeek = dayView.findViewById(R.id.day_week);

            dayNumber.setText(dayNumFormat.format(day.getTime()));
            dayWeek.setText(dayWeekFormat.format(day.getTime()));

            if (!available) {
                // Grayed out, not clickable
                dayView.setAlpha(0.3f);
                dayView.setBackgroundResource(R.drawable.bg_calendar_unselected);
                dayView.setEnabled(false);
            } else {
                dayView.setAlpha(1.0f);
                dayView.setBackgroundResource(R.drawable.bg_calendar_unselected);
                dayView.setEnabled(true);

                final String dateLabel = fullDateFormat.format(day.getTime());
                final String isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(day.getTime());
                final Calendar finalDay = day;
                dayView.setOnClickListener(v -> {
                    selectedDate = isoDate;
                    selectedDateDisplay = dateLabel;
                    selectedTime = null;

                    // Reset previous selection
                    if (lastSelectedDayView != null) {
                        lastSelectedDayView.setBackgroundResource(R.drawable.bg_calendar_unselected);
                        ((TextView) ((LinearLayout) lastSelectedDayView).getChildAt(0))
                                .setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    }

                    v.setBackgroundResource(R.drawable.bg_calendar_selected);
                    ((TextView) ((LinearLayout) v).getChildAt(0))
                            .setTextColor(ContextCompat.getColor(this, R.color.white));
                    lastSelectedDayView = v;

                    // ✅ Update the month label with the selected day's month + year
                    SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                    month.setText(monthYearFormat.format(finalDay.getTime()));

                    if (availableSlotsLabel != null) {
                        availableSlotsLabel.setText("Available Slots - " + dateLabel);
                    }
                    setupTimeSlots();
                });
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    72, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 12, 0);
            dayView.setLayoutParams(params);

            calendarContainer.addView(dayView);
        }
    }

    /**
     * Parses "9:00 am - 5:00 am" and generates 30-minute slots.
     */
    private void setupTimeSlots() {
        timeSlotsContainer.removeAllViews();
        lastSelectedTimeView = null;

        List<String> slots = generateTimeSlots(consultationHoursRaw);

        if (slots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No available time slots.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            timeSlotsContainer.addView(empty);
            return;
        }

        // Make it wrap
        timeSlotsContainer.setOrientation(LinearLayout.HORIZONTAL);

        // Use a FlowLayout workaround: wrap in a custom flow using nested LinearLayouts
        LinearLayout row = null;
        int perRow = 4;
        for (int i = 0; i < slots.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, 12);
                row.setLayoutParams(rowParams);
                timeSlotsContainer.setOrientation(LinearLayout.VERTICAL);
                timeSlotsContainer.addView(row);
            }

            String time = slots.get(i);
            TextView slotView = new TextView(this);
            slotView.setText(time);
            slotView.setPadding(24, 16, 24, 16);
            slotView.setBackgroundResource(R.drawable.bg_time_slot_unselected);
            slotView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 12, 0);
            slotView.setLayoutParams(params);

            slotView.setOnClickListener(v -> {
                selectedTime = time;

                if (lastSelectedTimeView != null) {
                    lastSelectedTimeView.setBackgroundResource(R.drawable.bg_time_slot_unselected);
                    lastSelectedTimeView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }

                slotView.setBackgroundResource(R.drawable.bg_time_slot_selected);
                slotView.setTextColor(ContextCompat.getColor(this, R.color.white));
                lastSelectedTimeView = slotView;
            });

            if (row != null) row.addView(slotView);
        }
    }

    /**
     * Generates 30-minute time slots from a range like "9:00 am - 5:00 pm"
     */
    private List<String> generateTimeSlots(String hoursRaw) {
        List<String> slots = new ArrayList<>();
        try {
            String cleaned = hoursRaw.toLowerCase(Locale.getDefault()).trim();
            String[] parts = cleaned.split("-");
            if (parts.length < 2) return slots;

            int startMinutes = parseTimeToMinutes(parts[0].trim());
            int endMinutes = parseTimeToMinutes(parts[1].trim());

            if (startMinutes < 0 || endMinutes < 0) return slots;

            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Calendar cal = Calendar.getInstance();

            for (int m = startMinutes; m < endMinutes; m += 30) {
                cal.set(Calendar.HOUR_OF_DAY, m / 60);
                cal.set(Calendar.MINUTE, m % 60);
                slots.add(sdf.format(cal.getTime()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return slots;
    }

    private int parseTimeToMinutes(String timeStr) {
        try {
            timeStr = timeStr.trim();
            boolean isPM = timeStr.contains("pm");
            boolean isAM = timeStr.contains("am");
            timeStr = timeStr.replace("am", "").replace("pm", "").trim();

            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;

            if (isPM && hours != 12) hours += 12;
            if (isAM && hours == 12) hours = 0;

            return hours * 60 + minutes;
        } catch (Exception e) {
            return -1;
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        videoCallButton.setOnClickListener(v -> {
            selectedConsultationType = "video";
            videoCallButton.setBackgroundResource(R.drawable.bg_consult_selected);
            inPersonButton.setBackgroundResource(R.drawable.bg_consult_unselected);
        });

        inPersonButton.setOnClickListener(v -> {
            selectedConsultationType = "in_person";
            inPersonButton.setBackgroundResource(R.drawable.bg_consult_selected);
            videoCallButton.setBackgroundResource(R.drawable.bg_consult_unselected);
        });

        confirmButton.setOnClickListener(v -> {
            if (selectedDate == null) {
                Toast.makeText(this, "Please select a date.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedTime == null) {
                Toast.makeText(this, "Please select a time slot.", Toast.LENGTH_SHORT).show();
                return;
            }
            saveAppointmentToFirestore();
        });
    }

    private void saveAppointmentToFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (doctorUid == null || doctorUid.isEmpty()) {
            Toast.makeText(this, "Doctor not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        confirmButton.setEnabled(false);

        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(currentUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    String first = snapshot.child("firstName").getValue(String.class);
                    String last = snapshot.child("lastName").getValue(String.class);
                    String patientName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    if (patientName.isEmpty()) patientName = currentUser.getUid();

                    writeAppointment(currentUser.getUid(), patientName);
                })
                .addOnFailureListener(e -> writeAppointment(currentUser.getUid(), currentUser.getUid()));
    }

    private void writeAppointment(String patientId, String patientName) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String appointmentDocId = firestore.collection("appointments").document().getId();
        String appointmentType = "video".equals(selectedConsultationType)
                ? "Online Consultation"
                : "Consultation";

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("appointmentId", appointmentDocId);
        appointment.put("doctorId", doctorUid);
        appointment.put("patientId", patientId);
        appointment.put("patientName", patientName);
        appointment.put("date", selectedDate);
        appointment.put("time", selectedTime);
        appointment.put("status", "pending");
        appointment.put("type", appointmentType);
        appointment.put("notes", "");

        firestore.collection("appointments")
                .document(appointmentDocId)
                .set(appointment)
                .addOnSuccessListener(unused -> {
                    String displayDate = selectedDateDisplay != null ? selectedDateDisplay : selectedDate;
                    Toast.makeText(this,
                            "Appointment requested for " + displayDate + " at " + selectedTime + ". Waiting for doctor confirmation.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    confirmButton.setEnabled(true);
                    Toast.makeText(this, "Failed to save appointment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}