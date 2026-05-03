package com.example.mediconnect;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ProfileFragment extends Fragment {

    static final String PREFS_NAME    = "medi_connect_prefs";
    static final String KEY_DARK_MODE = "dark_mode_preference";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView tvAvatarInitials, tvFullName, tvEmail;
    private TextView tvStatTotal, tvStatUpcoming, tvStatCompleted;
    private TextView tvPhone, tvDob, tvSex;
    private View     infoSection, cardStats, loadingOverlay;
    private TextView btnSystem, btnLight, btnDark;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private DatabaseReference usersRef;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth    = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        db       = FirebaseFirestore.getInstance();

        // ── Bind ──────────────────────────────────────────────────────────────
        tvAvatarInitials  = view.findViewById(R.id.tv_avatar_initials);
        tvFullName        = view.findViewById(R.id.tv_full_name);
        tvEmail           = view.findViewById(R.id.tv_email);
        tvStatTotal       = view.findViewById(R.id.tv_stat_appointments);
        tvStatUpcoming    = view.findViewById(R.id.tv_stat_doctors);
        tvStatCompleted   = view.findViewById(R.id.tv_stat_reports);
        tvPhone           = view.findViewById(R.id.tv_phone_value);
        tvDob             = view.findViewById(R.id.tv_dob_value);
        tvSex             = view.findViewById(R.id.tv_sex_value);
        // Use View (not LinearLayout/CardView) to avoid ClassCastException
        infoSection       = view.findViewById(R.id.info_section);
        cardStats         = view.findViewById(R.id.card_stats);
        loadingOverlay    = view.findViewById(R.id.loading_overlay);

        // ── Dark mode toggle ──────────────────────────────────────────────────
        btnSystem = view.findViewById(R.id.btn_mode_system);
        btnLight  = view.findViewById(R.id.btn_mode_light);
        btnDark   = view.findViewById(R.id.btn_mode_dark);
        refreshToggleUI(getSavedMode());
        btnSystem.setOnClickListener(v -> applyAndSaveMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        btnLight.setOnClickListener( v -> applyAndSaveMode(AppCompatDelegate.MODE_NIGHT_NO));
        btnDark.setOnClickListener(  v -> applyAndSaveMode(AppCompatDelegate.MODE_NIGHT_YES));

        // ── Row: Edit Profile header button ───────────────────────────────────
        view.findViewById(R.id.btn_edit_profile_header)
            .setOnClickListener(v -> openEditProfile());

        // ── Row: Edit Profile list row ────────────────────────────────────────
        view.findViewById(R.id.row_edit_profile)
            .setOnClickListener(v -> openEditProfile());

        // ── Row: Change Password ──────────────────────────────────────────────
        view.findViewById(R.id.row_change_password)
            .setOnClickListener(v ->
                startActivity(new Intent(requireContext(), dialog_change_password.class)));

        // ── Row: Help & Support ───────────────────────────────────────────────
        view.findViewById(R.id.row_help)
            .setOnClickListener(v ->
                startActivity(new Intent(requireContext(), HelpSupportActivity.class)));

        // ── Row: Privacy Policy ───────────────────────────────────────────────
        view.findViewById(R.id.row_privacy)
            .setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PrivacyPolicyActivity.class)));

        // ── Log out ───────────────────────────────────────────────────────────
        view.findViewById(R.id.btn_logout)
            .setOnClickListener(v -> signOut());

        // ── Load data ─────────────────────────────────────────────────────────
        loadUserProfile();
    }

    // Refresh profile data when returning from EditProfile
    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile();
    }

    // ── Open EditProfileActivity ──────────────────────────────────────────────

    private void openEditProfile() {
        startActivity(new Intent(requireContext(), EditProfileActivity.class));
    }

    // ── Sign out ──────────────────────────────────────────────────────────────

    private void signOut() {
        mAuth.signOut();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded()) return;
            Intent i = new Intent(requireContext(), Login.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    // ── Load user profile ─────────────────────────────────────────────────────

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                Intent i = new Intent(requireContext(), Login.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            });
            return;
        }

        showLoading(true);
        String uid = currentUser.getUid();

        String authEmail = currentUser.getEmail();
        if (tvEmail != null && authEmail != null) tvEmail.setText(authEmail);

        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                showLoading(false);
                if (!snapshot.exists()) {
                    if (tvAvatarInitials != null) tvAvatarInitials.setText("?");
                    if (tvFullName       != null) tvFullName.setText("Unknown User");
                    return;
                }
                User user = snapshot.getValue(User.class);
                if (user == null) return;

                String first = user.firstName != null ? user.firstName.trim() : "";
                String last  = user.lastName  != null ? user.lastName.trim()  : "";
                String full  = (first + " " + last).trim();
                if (full.isEmpty()) full = "Unknown User";

                if (tvAvatarInitials != null) tvAvatarInitials.setText(getInitials(first, last));
                if (tvFullName       != null) tvFullName.setText(full);
                if (tvEmail != null) {
                    String email = (authEmail != null && !authEmail.isEmpty())
                            ? authEmail : (user.email != null ? user.email : "");
                    tvEmail.setText(email);
                }
                if (tvPhone != null) tvPhone.setText(notEmpty(user.phone));
                if (tvDob   != null) tvDob.setText(notEmpty(user.dob));
                if (tvSex   != null) tvSex.setText(notEmpty(user.sex));

                if (infoSection != null) infoSection.setVisibility(View.VISIBLE);
                if (cardStats   != null) cardStats.setVisibility(View.VISIBLE);

                loadAppointmentStats(uid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                showLoading(false);
                toast("Could not load profile");
            }
        });
    }

    private void loadAppointmentStats(String uid) {
        db.collection("appointments")
            .whereEqualTo("patientId", uid)
            .get()
            .addOnSuccessListener(snapshots -> {
                if (!isAdded()) return;
                int total = 0, upcoming = 0, completed = 0;
                for (QueryDocumentSnapshot doc : snapshots) {
                    total++;
                    String status = doc.getString("status");
                    if (status == null) continue;
                    if (status.equalsIgnoreCase("confirmed")
                            || status.equalsIgnoreCase("pending")) upcoming++;
                    else if (status.equalsIgnoreCase("completed")) completed++;
                }
                if (tvStatTotal     != null) tvStatTotal.setText(String.valueOf(total));
                if (tvStatUpcoming  != null) tvStatUpcoming.setText(String.valueOf(upcoming));
                if (tvStatCompleted != null) tvStatCompleted.setText(String.valueOf(completed));
            });
    }

    // ── Dark mode ─────────────────────────────────────────────────────────────

    private void applyAndSaveMode(int mode) {
        requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DARK_MODE, mode).apply();
        refreshToggleUI(mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private int getSavedMode() {
        return requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DARK_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void refreshToggleUI(int mode) {
        setButtonInactive(btnSystem);
        setButtonInactive(btnLight);
        setButtonInactive(btnDark);
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM: setButtonActive(btnSystem); break;
            case AppCompatDelegate.MODE_NIGHT_NO:            setButtonActive(btnLight);  break;
            case AppCompatDelegate.MODE_NIGHT_YES:           setButtonActive(btnDark);   break;
        }
    }

    private void setButtonActive(TextView btn) {
        if (btn == null || !isAdded()) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(8));
        bg.setColor(ContextCompat.getColor(requireContext(), R.color.primary_teal));
        btn.setBackground(bg);
        btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
    }

    private void setButtonInactive(TextView btn) {
        if (btn == null || !isAdded()) return;
        btn.setBackground(null);
        btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (infoSection != null && loading) infoSection.setVisibility(View.GONE);
        if (cardStats   != null && loading) cardStats.setVisibility(View.GONE);
    }

    private String getInitials(String first, String last) {
        StringBuilder sb = new StringBuilder();
        if (!first.isEmpty()) sb.append(Character.toUpperCase(first.charAt(0)));
        if (!last.isEmpty())  sb.append(Character.toUpperCase(last.charAt(0)));
        return sb.length() > 0 ? sb.toString() : "?";
    }

    private String notEmpty(String val) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : "—";
    }

    private float dpToPx(int dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }

    private Toast mToast;
    private void toast(String msg) {
        if (!isAdded()) return;
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT);
        mToast.show();
    }
}
