package com.example.mediconnect;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Help & Support Activity
 *
 * Firestore collection: "supportTickets"
 * Document fields:
 *   userId    : String  (Firebase Auth UID)
 *   email     : String
 *   category  : String  ("Technical", "Billing", "Appointments", "General", "Other")
 *   message   : String
 *   timestamp : Long    (System.currentTimeMillis())
 *   status    : String  ("open" | "resolved")
 */
public class HelpSupportActivity extends AppCompatActivity {

    private static final int MAX_CHARS = 500;

    // ── Views ─────────────────────────────────────────────────────────────────
    private Spinner           spinnerCategory;
    private TextInputEditText etMessage;
    private TextView          tvCharCount;
    private MaterialButton    btnSubmit;
    private ProgressBar       submitLoading, ticketsLoading;
    private LinearLayout      successBanner;
    private RecyclerView      ticketsRecycler;
    private TextView          tvTicketsEmpty;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;
    private FirebaseUser      currentUser;

    // ── Tickets ───────────────────────────────────────────────────────────────
    private final List<Map<String, Object>> ticketList = new ArrayList<>();
    private TicketsAdapter ticketsAdapter;

    // ── Categories ────────────────────────────────────────────────────────────
    private static final String[] CATEGORIES = {
            "Select category…",
            "Appointments",
            "Billing",
            "Technical Issue",
            "Doctor / Specialist",
            "Account & Privacy",
            "General Inquiry",
            "Other"
    };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        bindViews();
        setupCategorySpinner();
        setupCharCounter();
        loadMyTickets();

        btnSubmit.setOnClickListener(v -> submit());

        View back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        spinnerCategory = findViewById(R.id.spinner_category);
        etMessage       = findViewById(R.id.et_message);
        tvCharCount     = findViewById(R.id.tv_char_count);
        btnSubmit       = findViewById(R.id.btn_submit);
        submitLoading   = findViewById(R.id.submit_loading);
        successBanner   = findViewById(R.id.success_banner);
        ticketsRecycler = findViewById(R.id.tickets_recycler);
        ticketsLoading  = findViewById(R.id.tickets_loading);
        tvTicketsEmpty  = findViewById(R.id.tv_tickets_empty);
    }

    // ── Category spinner ──────────────────────────────────────────────────────

    private void setupCategorySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    // ── Char counter ──────────────────────────────────────────────────────────

    private void setupCharCounter() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                int len = s.length();
                tvCharCount.setText(len + " / " + MAX_CHARS);
                tvCharCount.setTextColor(getResources().getColor(
                        len > MAX_CHARS ? R.color.status_critical : R.color.text_tertiary));
                if (len > MAX_CHARS) {
                    // Trim to max
                    etMessage.setText(s.subSequence(0, MAX_CHARS));
                    etMessage.setSelection(MAX_CHARS);
                }
            }
        });
    }

    // ── Validate + Submit ─────────────────────────────────────────────────────

    private void submit() {
        if (currentUser == null) {
            toast("You must be logged in to submit a request.");
            return;
        }

        int categoryPos = spinnerCategory.getSelectedItemPosition();
        if (categoryPos == 0) {
            toast("Please select a category.");
            return;
        }

        String message = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (message.isEmpty()) {
            etMessage.setError("Please describe your issue.");
            etMessage.requestFocus();
            return;
        }
        if (message.length() < 10) {
            etMessage.setError("Please provide more detail (at least 10 characters).");
            etMessage.requestFocus();
            return;
        }

        setSubmitLoading(true);

        String category = CATEGORIES[categoryPos];

        Map<String, Object> ticket = new HashMap<>();
        ticket.put("userId",    currentUser.getUid());
        ticket.put("email",     currentUser.getEmail() != null ? currentUser.getEmail() : "");
        ticket.put("category",  category);
        ticket.put("message",   message);
        ticket.put("timestamp", System.currentTimeMillis());
        ticket.put("status",    "open");

        db.collection("supportTickets")
            .add(ticket)
            .addOnSuccessListener(ref -> {
                setSubmitLoading(false);
                showSuccess();
                etMessage.setText("");
                spinnerCategory.setSelection(0);
                loadMyTickets();   // refresh list
            })
            .addOnFailureListener(e -> {
                setSubmitLoading(false);
                toast("Failed to submit: " + e.getMessage());
            });
    }

    private void showSuccess() {
        successBanner.setVisibility(View.VISIBLE);
        successBanner.postDelayed(() -> {
            if (!isFinishing()) successBanner.setVisibility(View.GONE);
        }, 4000);
    }

    private void setSubmitLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);
        btnSubmit.setText(loading ? "Submitting…" : "Submit Request");
        submitLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ── Load previous tickets ─────────────────────────────────────────────────

    private void loadMyTickets() {
        if (currentUser == null) { hideTicketsLoading(); return; }

        ticketsLoading.setVisibility(View.VISIBLE);
        ticketsRecycler.setVisibility(View.GONE);
        tvTicketsEmpty.setVisibility(View.GONE);

        db.collection("supportTickets")
            .whereEqualTo("userId", currentUser.getUid())
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener(snapshots -> {
                if (isFinishing()) return;
                hideTicketsLoading();
                ticketList.clear();
                for (QueryDocumentSnapshot doc : snapshots) {
                    Map<String, Object> data = doc.getData();
                    data.put("id", doc.getId());
                    ticketList.add(data);
                }
                if (ticketList.isEmpty()) {
                    tvTicketsEmpty.setVisibility(View.VISIBLE);
                } else {
                    setupTicketsList();
                }
            })
            .addOnFailureListener(e -> { if (!isFinishing()) hideTicketsLoading(); });
    }

    private void hideTicketsLoading() {
        ticketsLoading.setVisibility(View.GONE);
    }

    private void setupTicketsList() {
        ticketsRecycler.setVisibility(View.VISIBLE);
        ticketsAdapter = new TicketsAdapter(ticketList);
        ticketsRecycler.setLayoutManager(new LinearLayoutManager(this));
        ticketsRecycler.setAdapter(ticketsAdapter);
        ticketsAdapter.notifyDataSetChanged();
    }

    // ── Toast (deduped) ───────────────────────────────────────────────────────

    private Toast mToast;
    private void toast(String msg) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mToast.show();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Inner adapter for previous tickets
    // ════════════════════════════════════════════════════════════════════════

    static class TicketsAdapter extends RecyclerView.Adapter<TicketsAdapter.TicketVH> {

        private final List<Map<String, Object>> tickets;

        TicketsAdapter(List<Map<String, Object>> tickets) { this.tickets = tickets; }

        @NonNull
        @Override
        public TicketVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ticket_card, parent, false);
            return new TicketVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TicketVH h, int position) {
            Map<String, Object> t = tickets.get(position);

            h.tvCategory.setText(str(t.get("category")));
            h.tvMessage.setText(str(t.get("message")));

            // Status
            String status = str(t.get("status"));
            h.tvStatus.setText(status.isEmpty() ? "Open" :
                    Character.toUpperCase(status.charAt(0)) + status.substring(1));

            // Timestamp
            Object ts = t.get("timestamp");
            if (ts instanceof Long) {
                String formatted = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
                        .format(new Date((Long) ts));
                h.tvTime.setText(formatted);
            }
        }

        @Override public int getItemCount() { return tickets.size(); }

        private static String str(Object o) {
            return o != null ? o.toString() : "";
        }

        static class TicketVH extends RecyclerView.ViewHolder {
            TextView tvCategory, tvMessage, tvStatus, tvTime;
            TicketVH(@NonNull View v) {
                super(v);
                tvCategory = v.findViewById(R.id.tv_ticket_category);
                tvMessage  = v.findViewById(R.id.tv_ticket_message);
                tvStatus   = v.findViewById(R.id.tv_ticket_status);
                tvTime     = v.findViewById(R.id.tv_ticket_time);
            }
        }
    }
}
