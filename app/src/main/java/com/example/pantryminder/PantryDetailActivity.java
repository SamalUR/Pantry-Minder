package com.example.pantryminder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PantryDetailActivity extends AppCompatActivity {

    private RecyclerView itemsRecyclerView;
    private ItemAdapter itemAdapter;
    private List<Item> itemList = new ArrayList<>();
    private Spinner categorySpinner;
    private List<String> categoryList = new ArrayList<>();
    private FirebaseFirestore db;
    private String pantryId;
    private String userId;
    private ListenerRegistration itemsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantry_detail);

        db = FirebaseFirestore.getInstance();
        pantryId = getIntent().getStringExtra("pantryId");
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        itemsRecyclerView = findViewById(R.id.items_recycler_view);
        categorySpinner = findViewById(R.id.categorySpinner);
        itemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ItemAdapter(itemList, this::onItemSelected);
        itemsRecyclerView.setAdapter(itemAdapter);

        Button addItemButton = findViewById(R.id.btn_add_item);
        Button generateCodeButton = findViewById(R.id.btn_generate_code);

        addItemButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddItemActivity.class);
            intent.putExtra("pantryId", pantryId);
            startActivity(intent);
        });

        generateCodeButton.setOnClickListener(v -> generateInvitationCode());

        // Setup Swipe-to-Delete Listener
        setupSwipeToDelete();

        loadCategories();
        setupCategorySpinner();
        loadItems(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemsListener != null) itemsListener.remove();
    }

    private void loadCategories() {
        db.collection("Pantries").document(pantryId).collection("items")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Set<String> categories = new HashSet<>();
                    categories.add("All");
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String category = doc.getString("category");
                        if (category != null && !category.isEmpty()) {
                            categories.add(category);
                        }
                    }
                    categoryList.clear();
                    categoryList.addAll(categories);
                    updateCategorySpinner();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load categories", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupCategorySpinner() {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categoryList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = categoryList.get(position);
                if (selectedCategory.equals("All")) {
                    loadItems(null);
                } else {
                    loadItems(selectedCategory);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                loadItems(null);
            }
        });
    }

    private void updateCategorySpinner() {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) categorySpinner.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadItems(String category) {
        if (itemsListener != null) itemsListener.remove();
        Query query = db.collection("Pantries").document(pantryId).collection("items");
        if (category != null) {
            query = query.whereEqualTo("category", category);
        }
        itemsListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show();
                return;
            }
            if (querySnapshot != null) {
                itemList.clear();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Item item = doc.toObject(Item.class);
                    if (item != null) {
                        item.setId(doc.getId());
                        itemList.add(item);
                    }
                }

                // Sort Items Alphabetically (A-Z) by Name
                Collections.sort(itemList, (i1, i2) -> {
                    if (i1.getName() == null) return -1;
                    if (i2.getName() == null) return 1;
                    return i1.getName().compareToIgnoreCase(i2.getName());
                });

                itemAdapter.notifyDataSetChanged();
                loadCategories();
            }
        });
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Item item = itemList.get(position);

                // Show a confirmation dialog before deleting
                new AlertDialog.Builder(PantryDetailActivity.this)
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete '" + item.getName() + "'?")
                        .setPositiveButton("Delete", (dialog, which) -> deleteItem(item))
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            itemAdapter.notifyItemChanged(position);
                        })
                        .setOnCancelListener(dialog -> {
                            itemAdapter.notifyItemChanged(position);
                        })
                        .show();
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(itemsRecyclerView);
    }

    private void deleteItem(Item item) {
        db.collection("Pantries")
                .document(pantryId)
                .collection("items")
                .document(item.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    itemAdapter.notifyDataSetChanged();
                });
    }

    private void onItemSelected(Item item) {
        Intent intent = new Intent(this, EditItemActivity.class);
        intent.putExtra("pantryId", pantryId);
        intent.putExtra("itemId", item.getId());
        startActivity(intent);
    }

    private void generateInvitationCode() {
        db.collection("Pantries").document(pantryId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && userId.equals(doc.getString("createdBy"))) {
                String code = generateUniqueCode();
                Map<String, Object> invitation = new HashMap<>();
                invitation.put("pantryId", pantryId);
                invitation.put("code", code);
                invitation.put("invitedBy", userId);
                Timestamp expiresAt = new Timestamp(
                        new java.util.Date(System.currentTimeMillis() + 86400 * 1000)
                );
                invitation.put("expiresAt", expiresAt);
                db.collection("invitations").add(invitation).addOnSuccessListener(ref -> {
                    showInvitationCodeDialog(code);
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to generate invitation code", Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, "Only creator can generate code", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String generateUniqueCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void showInvitationCodeDialog(String code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Invitation Code");
        builder.setMessage(code);
        builder.setPositiveButton("Copy", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Invitation Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }
}