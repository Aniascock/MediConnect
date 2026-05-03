package com.example.mediconnect;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Edit Profile Activity
 * Reads from / writes to: Firebase Realtime Database → "Users/{uid}"
 * Profile image: Firebase Storage → "profile_images/{uid}.jpg"
 *
 * Expected DB fields: firstName, lastName, email, phone, dob, sex, address, city, imageUrl
 */
public class EditProfileActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView     tvAvatarInitials;
    private ImageView    ivProfileImage;
    private TextInputEditText etFirstName, etLastName, etEmail, etPhone, etDob, etAddress, etCity;
    private MaterialButton    btnSave, btnSexMale, btnSexFemale;
    private ProgressBar  saveLoading;

    // ── State ─────────────────────────────────────────────────────────────────
    private String  selectedSex     = "";   // "Male" / "Female"
    private Uri     pendingImageUri = null; // image chosen but not yet uploaded

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private DatabaseReference userRef;
    private StorageReference  storageRef;

    // ── Image picker ──────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> imagePicker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            pendingImageUri = uri;
            // Show preview immediately
            ivProfileImage.setImageURI(uri);
            ivProfileImage.setVisibility(View.VISIBLE);
            tvAvatarInitials.setVisibility(View.GONE);
        });

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        userRef    = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());
        storageRef = FirebaseStorage.getInstance().getReference("profile_images")
                        .child(user.getUid() + ".jpg");

        bindViews();
        loadCurrentData(user);
        setupListeners();
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvAvatarInitials = findViewById(R.id.tv_avatar_initials);
        ivProfileImage   = findViewById(R.id.iv_profile_image);
        etFirstName      = findViewById(R.id.et_first_name);
        etLastName       = findViewById(R.id.et_last_name);
        etEmail          = findViewById(R.id.et_email);
        etPhone          = findViewById(R.id.et_phone);
        etDob            = findViewById(R.id.et_dob);
        etAddress        = findViewById(R.id.et_address);
        etCity           = findViewById(R.id.et_city);
        btnSave          = findViewById(R.id.btn_save);
        btnSexMale       = findViewById(R.id.btn_sex_male);
        btnSexFemale     = findViewById(R.id.btn_sex_female);
        saveLoading      = findViewById(R.id.save_loading);

        // Back button
        View back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());
    }

    // ── Load existing data ────────────────────────────────────────────────────

    private void loadCurrentData(FirebaseUser user) {
        // Email from Auth is always accurate
        etEmail.setText(user.getEmail());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String first   = snapshot.child("firstName").getValue(String.class);
                String last    = snapshot.child("lastName").getValue(String.class);
                String phone   = snapshot.child("phone").getValue(String.class);
                String dob     = snapshot.child("dob").getValue(String.class);
                String sex     = snapshot.child("sex").getValue(String.class);
                String address = snapshot.child("address").getValue(String.class);
                String city    = snapshot.child("city").getValue(String.class);

                if (first   != null) etFirstName.setText(first);
                if (last    != null) etLastName.setText(last);
                if (phone   != null) etPhone.setText(phone);
                if (dob     != null) etDob.setText(dob);
                if (address != null) etAddress.setText(address);
                if (city    != null) etCity.setText(city);

                if (sex != null && !sex.isEmpty()) {
                    selectSex(sex);
                }

                // Avatar initials
                String initials = getInitials(first, last);
                tvAvatarInitials.setText(initials);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Failed to load profile data");
            }
        });
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        // Photo picker
        View changePhoto = findViewById(R.id.btn_change_photo);
        if (changePhoto != null)
            changePhoto.setOnClickListener(v -> imagePicker.launch("image/*"));
        View photoLabel = findViewById(R.id.tv_change_photo_label);
        if (photoLabel != null)
            photoLabel.setOnClickListener(v -> imagePicker.launch("image/*"));

        // Date picker for DOB
        etDob.setOnClickListener(v -> showDatePicker());

        // Sex toggle
        btnSexMale.setOnClickListener(v   -> selectSex("Male"));
        btnSexFemale.setOnClickListener(v -> selectSex("Female"));

        // Save
        btnSave.setOnClickListener(v -> save());
    }

    // ── Sex toggle visuals ────────────────────────────────────────────────────

    private void selectSex(String sex) {
        selectedSex = sex;
        boolean male = "Male".equals(sex);

        // Active = teal background + white text; Inactive = outlined
        styleSexButton(btnSexMale,   male);
        styleSexButton(btnSexFemale, !male);
    }

    private void styleSexButton(MaterialButton btn, boolean active) {
        if (active) {
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.primary_teal)));
            btn.setTextColor(ContextCompat.getColor(this, R.color.white));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary_teal)));
        } else {
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.TRANSPARENT));
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.divider)));
        }
    }

    // ── Date picker ───────────────────────────────────────────────────────────

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    String dob = String.format(java.util.Locale.getDefault(),
                            "%04d-%02d-%02d", year, month + 1, day);
                    etDob.setText(dob);
                },
                cal.get(Calendar.YEAR) - 20,  // default: 20 years ago
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH))
            .show();
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    private boolean validate() {
        String first = getText(etFirstName);
        String last  = getText(etLastName);
        String phone = getText(etPhone);

        if (TextUtils.isEmpty(first)) {
            etFirstName.setError("First name is required");
            etFirstName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(last)) {
            etLastName.setError("Last name is required");
            etLastName.requestFocus();
            return false;
        }
        if (!TextUtils.isEmpty(phone) && phone.length() < 7) {
            etPhone.setError("Enter a valid phone number");
            etPhone.requestFocus();
            return false;
        }
        return true;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void save() {
        if (!validate()) return;

        setLoading(true);

        if (pendingImageUri != null) {
            // Upload image first, then save profile
            uploadImageThenSave();
        } else {
            saveToDatabase(null);
        }
    }

    private void uploadImageThenSave() {
        storageRef.putFile(pendingImageUri)
            .addOnSuccessListener(task ->
                storageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> saveToDatabase(uri.toString()))
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        toast("Image upload failed: " + e.getMessage());
                    }))
            .addOnFailureListener(e -> {
                setLoading(false);
                toast("Upload error: " + e.getMessage());
            });
    }

    private void saveToDatabase(@androidx.annotation.Nullable String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", getText(etFirstName));
        updates.put("lastName",  getText(etLastName));
        updates.put("phone",     getText(etPhone));
        updates.put("dob",       getText(etDob));
        updates.put("address",   getText(etAddress));
        updates.put("city",      getText(etCity));
        if (!selectedSex.isEmpty()) updates.put("sex", selectedSex);
        if (imageUrl != null)       updates.put("imageUrl", imageUrl);

        userRef.updateChildren(updates)
            .addOnSuccessListener(v -> {
                setLoading(false);
                pendingImageUri = null;
                toast("✅ Profile updated successfully!");
                finish();   // go back to Profile tab
            })
            .addOnFailureListener(e -> {
                setLoading(false);
                toast("Save failed: " + e.getMessage());
            });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnSave.setEnabled(!loading);
        btnSave.setText(loading ? "Saving…" : "Save Changes");
        saveLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String getInitials(String first, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isEmpty())
            sb.append(Character.toUpperCase(first.charAt(0)));
        if (last  != null && !last.isEmpty())
            sb.append(Character.toUpperCase(last.charAt(0)));
        return sb.length() > 0 ? sb.toString() : "?";
    }

    private Toast mToast;
    private void toast(String msg) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mToast.show();
    }
}
