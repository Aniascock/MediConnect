 package com.example.mediconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService;
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig;
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton;
import com.zegocloud.uikit.service.defines.ZegoUIKitUser;

import java.util.ArrayList;
import java.util.List;

public class Consultation_Schedule extends AppCompatActivity {

    private static final long   ZEGO_APP_ID   = 1986084612;
    private static final String ZEGO_APP_SIGN = "33bce858e0d1f726dc8ffbcfa2e7fea78120215f59c63b8cb6b281ce9d1e1adc";

    private LinearLayout      consultationContainer;
    private TextView          tvEmpty;
    private FirebaseFirestore db;
    private String            patientId;
    private String            patientName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_consultation_schedule);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        consultationContainer = findViewById(R.id.consultationContainer);
        tvEmpty               = findViewById(R.id.tvEmpty);
        db                    = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("User not logged in.");
            return;
        }
        patientId = currentUser.getUid();

        // Fetch patient name first so Zego shows it on the doctor's incoming-call popup
        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(patientId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String first = snapshot.child("firstName").getValue(String.class);
                    String last  = snapshot.child("lastName").getValue(String.class);
                    patientName  = (nn(first) + " " + nn(last)).trim();
                    if (patientName.isEmpty()) patientName = patientId;

                    initZegoAndLoadCards();
                })
                .addOnFailureListener(e -> {
                    patientName = patientId; // fallback to UID
                    initZegoAndLoadCards();
                });
    }

    private void initZegoAndLoadCards() {
        // Init Zego with patient's Firebase UID as the Zego userID.
        // The doctor's Firebase UID will be the invitee — both sides
        // use their own UID so no ID mapping is ever needed.
        ZegoUIKitPrebuiltCallService.init(
                getApplication(),
                ZEGO_APP_ID,
                ZEGO_APP_SIGN,
                patientId,   // Zego userID  = Firebase Auth UID
                patientName, // shown on the doctor's incoming-call screen
                new ZegoUIKitPrebuiltCallInvitationConfig()
        );

        loadConsultations();
    }

    private void loadConsultations() {
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("Loading consultations...");

        db.collection("appointments")
                .whereEqualTo("patientId", patientId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    consultationContainer.removeAllViews();

                    if (snapshots.isEmpty()) {
                        tvEmpty.setText("🗓️ No consultations found");
                        return;
                    }
                    tvEmpty.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String doctorId = doc.getString("doctorId");
                        String date     = doc.getString("date");
                        String time     = doc.getString("time");
                        String notes    = doc.getString("notes");
                        String type     = doc.getString("type");

                        if (doctorId == null) continue;

                        // Fetch doctor name + specialization from RTDB
                        FirebaseDatabase.getInstance()
                                .getReference("Doctors")
                                .child(doctorId)
                                .get()
                                .addOnSuccessListener(ds -> addConsultationCard(
                                        ds.child("fullName").getValue(String.class),
                                        ds.child("specialization").getValue(String.class),
                                        date, time, notes, type, doctorId
                                ))
                                .addOnFailureListener(e -> addConsultationCard(
                                        null, null, date, time, notes, type, doctorId
                                ));
                    }
                })
                .addOnFailureListener(e -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("❌ Failed to load consultations");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void addConsultationCard(String doctorName, String specialization,
                                     String date, String time,
                                     String notes, String type,
                                     String doctorId) {

        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_consultation_card, consultationContainer, false);

        String displayName = (doctorName != null) ? doctorName : "Unknown Doctor";
        String displaySpec = (specialization != null) ? specialization
                : (type != null)           ? type
                : "GP TELECALL";

        ((TextView) card.findViewById(R.id.tvDoctorName)).setText(displayName);
        ((TextView) card.findViewById(R.id.tvAvatar))
                .setText(String.valueOf(displayName.charAt(0)).toUpperCase());
        ((TextView) card.findViewById(R.id.tvSpecialization))
                .setText(displaySpec.toUpperCase());
        ((TextView) card.findViewById(R.id.tvDateTime))
                .setText(nn(date) + "  ·  " + nn(time));
        ((TextView) card.findViewById(R.id.tvNotes))
                .setText((notes != null && !notes.isEmpty()) ? notes : "No notes");

        // Wire the call button — doctorId IS the doctor's Zego userID.
        // Zego handles outgoing ring, the doctor's incoming popup,
        // accept/decline, and room join — nothing else needed on this side.
        ZegoSendCallInvitationButton btnVideoCall = card.findViewById(R.id.btnVideoCall);
        TextView tvEnded = card.findViewById(R.id.tvEnded);

        if (btnVideoCall != null) {
            if (isConsultationPast(date, time)) {
                // Past the valid window — show "Ended"
                btnVideoCall.setVisibility(View.GONE);
                if (tvEnded != null) {
                    tvEnded.setText("Ended");
                    tvEnded.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
                    tvEnded.setVisibility(View.VISIBLE);
                }
            } else if (isConsultationActive(date, time)) {
                // Within the valid 1-hour window — show call button
                btnVideoCall.setVisibility(View.VISIBLE);
                List<ZegoUIKitUser> invitees = new ArrayList<>();
                invitees.add(new ZegoUIKitUser(doctorId, displayName));
                btnVideoCall.setInvitees(invitees);
                btnVideoCall.setIsVideoCall(true);
                if (tvEnded != null) tvEnded.setVisibility(View.GONE);
            } else {
                // Still in the future — show "Upcoming"
                btnVideoCall.setVisibility(View.GONE);
                if (tvEnded != null) {
                    tvEnded.setText("Upcoming");
                    tvEnded.setTextColor(android.graphics.Color.parseColor("#1ABCD6"));
                    tvEnded.setVisibility(View.VISIBLE);
                }
            }
        }

        consultationContainer.addView(card);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ZegoUIKitPrebuiltCallService.unInit();
    }


    // ADD THIS ABOVE nn():
    private boolean isConsultationPast(String date, String time) {
        try {
            java.util.Date consultationDateTime = parseDateTime(date, time);
            if (consultationDateTime == null) return false;
            // "Ended" = more than 1 hour after scheduled time
            java.util.Date endTime = new java.util.Date(consultationDateTime.getTime() + 60 * 60 * 1000);
            return new java.util.Date().after(endTime);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isConsultationActive(String date, String time) {
        try {
            java.util.Date consultationDateTime = parseDateTime(date, time);
            if (consultationDateTime == null) return false;
            java.util.Date now = new java.util.Date();
            java.util.Date endTime = new java.util.Date(consultationDateTime.getTime() + 60 * 60 * 1000);
            // "Active" = now is on or after scheduled time AND before end time
            return !now.before(consultationDateTime) && now.before(endTime);
        } catch (Exception e) {
            return false;
        }
    }

    private java.util.Date parseDateTime(String date, String time) {
        try {
            String dateTimeStr = nn(date).trim() + " " + nn(time).trim();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd hh:mm a", java.util.Locale.US);
            sdf.setLenient(false);
            return sdf.parse(dateTimeStr);
        } catch (Exception e) {
            return null;
        }
    }
    private static String nn(String s) { return s != null ? s : ""; }
}