package com.example.pantryminder;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditItemActivity extends AppCompatActivity {


    private TextInputEditText nameEditText, categoryEditText, quantityEditText, unitEditText, expiryEditText;
    private MaterialButton updateButton, deleteButton, pickExpiryButton;
    private FirebaseFirestore db;
    private String pantryId, itemId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_item);

        db = FirebaseFirestore.getInstance();

        pantryId = getIntent().getStringExtra("pantryId");
        itemId = getIntent().getStringExtra("itemId");

        if (pantryId == null || itemId == null || pantryId.isEmpty() || itemId.isEmpty()) {
            Toast.makeText(this, "❌ Invalid item data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadItemDetails();
        setupClickListeners();
    }

    private void initViews() {
        nameEditText = findViewById(R.id.name_edit_text);
        categoryEditText = findViewById(R.id.category_edit_text);
        quantityEditText = findViewById(R.id.quantity_edit_text);
        unitEditText = findViewById(R.id.unit_edit_text);
        expiryEditText = findViewById(R.id.expiry_edit_text);
        updateButton = findViewById(R.id.btn_update);
        deleteButton = findViewById(R.id.btn_delete);
        pickExpiryButton = findViewById(R.id.btn_pick_expiry);
    }

    private void setupClickListeners() {
        if (pickExpiryButton != null) {
            pickExpiryButton.setOnClickListener(v -> showDatePickerDialog());
        }
        if (updateButton != null) {
            updateButton.setOnClickListener(v -> updateItem());
        }
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> deleteItem());
        }
    }

    private void loadItemDetails() {
        db.collection("Pantries")
                .document(pantryId)
                .collection("items")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        nameEditText.setText(documentSnapshot.getString("name"));
                        categoryEditText.setText(documentSnapshot.getString("category"));
                        Long quantity = documentSnapshot.getLong("quantity");
                        if (quantity != null)
                            quantityEditText.setText(String.valueOf(quantity));
                        unitEditText.setText(documentSnapshot.getString("unit"));

                        Timestamp expiry = documentSnapshot.getTimestamp("expiryDate");
                        if (expiry != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            expiryEditText.setText(sdf.format(expiry.toDate()));
                        }

                        Toast.makeText(this, "✅ Item loaded", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "❌ Item not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Failed to load item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void showDatePickerDialog() {
        final Calendar currentCalendar = Calendar.getInstance();

        String currentExpiry = expiryEditText.getText().toString();
        if (!currentExpiry.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                currentCalendar.setTime(sdf.parse(currentExpiry));
            } catch (Exception e) {

            }
        }

        int year = currentCalendar.get(Calendar.YEAR);
        int month = currentCalendar.get(Calendar.MONTH);
        int day = currentCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                EditItemActivity.this,
                (view, yearSelected, monthSelected, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(yearSelected, monthSelected, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    expiryEditText.setText(sdf.format(selectedCal.getTime()));
                },
                year, month, day
        );

        datePickerDialog.show();
    }

    private void updateItem() {
        String name = getTextSafely(nameEditText);
        String category = getTextSafely(categoryEditText);
        String quantityStr = getTextSafely(quantityEditText);
        String unit = getTextSafely(unitEditText);
        String expiryStr = getTextSafely(expiryEditText);

        if (name.isEmpty() || category.isEmpty() || quantityStr.isEmpty() ||
                unit.isEmpty() || expiryStr.isEmpty()) {
            Toast.makeText(this, "❌ Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                Toast.makeText(this, "❌ Quantity must be positive", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "❌ Invalid quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp expiry;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            expiry = new Timestamp(sdf.parse(expiryStr));
        } catch (Exception e) {
            Toast.makeText(this, "❌ Invalid expiry date", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("category", category);
        updates.put("quantity", quantity);
        updates.put("unit", unit);
        updates.put("expiryDate", expiry);

        db.collection("Pantries")
                .document(pantryId)
                .collection("items")
                .document(itemId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Item updated successfully!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteItem() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete '" + nameEditText.getText().toString() + "'?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    db.collection("Pantries")
                            .document(pantryId)
                            .collection("items")
                            .document(itemId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(EditItemActivity.this, "✅ Item deleted successfully!", Toast.LENGTH_LONG).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(EditItemActivity.this, "❌ Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getTextSafely(TextInputEditText editText) {
        return editText != null ? editText.getText().toString().trim() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
