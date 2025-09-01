package com.example.pantryminder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pantryminder.R;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PantryListActivity extends AppCompatActivity {
    private RecyclerView pantryRecyclerView;
    private PantryAdapter pantryAdapter;
    private List<Pantry> pantryList = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantry_list);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        pantryRecyclerView = findViewById(R.id.pantry_recycler_view);
        pantryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pantryAdapter = new PantryAdapter(pantryList, this::onPantrySelected);
        pantryRecyclerView.setAdapter(pantryAdapter);

        FloatingActionButton fabAddPantry = findViewById(R.id.fab_add_pantry);
        fabAddPantry.setOnClickListener(v -> showAddPantryDialog());

        setupSwipeToDelete();
        loadPantries();
    }

    private void loadPantries() {
        db.collection("Users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            List<String> pantryIds = (List<String>) documentSnapshot.get("pantries");
            if (pantryIds != null) {
                pantryList.clear();
                List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
                for (String pantryId : pantryIds) {
                    tasks.add(db.collection("Pantries").document(pantryId).get());
                }
                Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                    for (Object obj : results) {
                        DocumentSnapshot doc = (DocumentSnapshot) obj;
                        if (doc.exists()) {
                            Pantry pantry = new Pantry(doc.getId(), doc.getString("name"));
                            pantryList.add(pantry);
                        }
                    }
                    pantryAdapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void onPantrySelected(Pantry pantry) {
        Intent intent = new Intent(this, PantryDetailActivity.class);
        intent.putExtra("pantryId", pantry.getId());
        startActivity(intent);
    }

    private void showAddPantryDialog() {
        // Use AlertDialog for adding new pantry (adapt as needed)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Pantry");
        final EditText input = new EditText(this);
        input.setHint("Pantry Name");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                addNewPantry(name);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addNewPantry(String name) {
        Map<String, Object> pantryData = new HashMap<>();
        pantryData.put("name", name);
        pantryData.put("createdBy", userId);
        pantryData.put("members", Arrays.asList(userId));

        db.collection("Pantries").add(pantryData).addOnSuccessListener(documentReference -> {
            String pantryId = documentReference.getId();
            // Update user's pantries array
            db.collection("Users").document(userId).update("pantries", FieldValue.arrayUnion(pantryId));
            loadPantries(); // Refresh list
            Toast.makeText(this, "Pantry added", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to add pantry", Toast.LENGTH_SHORT).show());
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Pantry pantry = pantryList.get(position);
                deletePantry(pantry);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(pantryRecyclerView);
    }

    private void deletePantry(Pantry pantry) {
        String pantryId = pantry.getId();
        db.collection("Pantries").document(pantryId).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("PantryListActivity", "Pantry deleted successfully: " + pantryId);

                    // Optional: Delete items subcollection
                    db.collection("Pantries").document(pantryId).collection("items").get()
                            .addOnSuccessListener(querySnapshot -> {
                                List<Task<Void>> deleteTasks = new ArrayList<>();
                                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                    deleteTasks.add(doc.getReference().delete());
                                }
                                Tasks.whenAll(deleteTasks)
                                        .addOnSuccessListener(aVoid2 -> Log.d("PantryListActivity", "Items subcollection deleted"))
                                        .addOnFailureListener(e -> Log.e("PantryListActivity", "Failed to delete items: " + e.getMessage()));
                            });

                    // Fetch members to update their pantries
                    db.collection("Pantries").document(pantryId).get()
                            .addOnSuccessListener(doc -> {
                                List<String> members = (List<String>) doc.get("members");
                                if (members != null && !members.isEmpty()) {
                                    List<Task<Void>> updateTasks = new ArrayList<>();
                                    for (String memberId : members) {
                                        updateTasks.add(db.collection("Users").document(memberId)
                                                .update("pantries", FieldValue.arrayRemove(pantryId)));
                                    }
                                    Tasks.whenAllSuccess(updateTasks)
                                            .addOnSuccessListener(results -> {
                                                Log.d("PantryListActivity", "Updated pantries for all members");
                                                pantryList.remove(pantry);
                                                pantryAdapter.notifyDataSetChanged();
                                                Toast.makeText(this, "Pantry deleted", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e("PantryListActivity", "Failed to update some members' pantries: " + e.getMessage());
                                                Toast.makeText(this, "Partially deleted, member updates failed", Toast.LENGTH_SHORT).show();
                                            });
                                } else {
                                    Log.d("PantryListActivity", "No members to update for pantry: " + pantryId);
                                    pantryList.remove(pantry);
                                    pantryAdapter.notifyDataSetChanged();
                                    Toast.makeText(this, "Pantry deleted", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e("PantryListActivity", "Failed to fetch pantry members: " + e.getMessage());
                                Toast.makeText(this, "Failed to delete pantry (member fetch error)", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("PantryListActivity", "Failed to delete pantry: " + e.getMessage());
                    Toast.makeText(this, "Failed to delete pantry", Toast.LENGTH_SHORT).show();
                });
    }
}