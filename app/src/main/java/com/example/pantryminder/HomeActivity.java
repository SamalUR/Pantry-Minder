package com.example.pantryminder;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    private Spinner pantrySpinner;
    private TextView expiringSoonText;
    private TextView totalItemsText;
    private RecyclerView expiringItemsRecycler;
    private ExpiryItemAdapter adapter;

    private DrawerLayout drawerLayout;
    private androidx.appcompat.app.ActionBarDrawerToggle toggle;
    private NavigationView navigationView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    private List<Pantry> userPantries = new ArrayList<>();
    private List<String> spinnerItems = new ArrayList<>();
    private ListenerRegistration userListener;
    private final List<ListenerRegistration> pantryListeners = new ArrayList<>();
    private final List<ListenerRegistration> itemListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        userId = currentUser.getUid();

        pantrySpinner = findViewById(R.id.pantrySpinner);
        expiringSoonText = findViewById(R.id.expiringSoonText);
        totalItemsText = findViewById(R.id.totalItemsText);
        expiringItemsRecycler = findViewById(R.id.expiringItemsRecycler);

        expiringItemsRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpiryItemAdapter(new ArrayList<>());
        expiringItemsRecycler.setAdapter(adapter);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        toggle = new androidx.appcompat.app.ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_profile) {
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
            } else if (id == R.id.nav_join_pantry) {
                showJoinPantryDialog();
            } else if (id == R.id.nav_logout) {
                mAuth.signOut();
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                finish();
            }

            drawerLayout.closeDrawers();
            return true;
        });

        Button pantryListButton = findViewById(R.id.btn_pantry_list);
        if (pantryListButton != null) {
            pantryListButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, PantryListActivity.class);
                startActivity(intent);
            });
        }

        loadUserPantries();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        for (ListenerRegistration l : pantryListeners) l.remove();
        for (ListenerRegistration l : itemListeners) l.remove();
        pantryListeners.clear();
        itemListeners.clear();
    }

    private void loadUserPantries() {
        if (userListener != null) userListener.remove();
        for (ListenerRegistration l : pantryListeners) l.remove();
        for (ListenerRegistration l : itemListeners) l.remove();
        pantryListeners.clear();
        itemListeners.clear();

        DocumentReference userRef = db.collection("Users").document(userId);

        userListener = userRef.addSnapshotListener((documentSnapshot, e) -> {
            if (e != null) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                List<String> pantryIds = (List<String>) documentSnapshot.get("pantries");
                userPantries.clear();

                if (pantryIds != null && !pantryIds.isEmpty()) {
                    for (String pantryId : pantryIds) {
                        ListenerRegistration reg = db.collection("Pantries")
                                .document(pantryId)
                                .addSnapshotListener((doc, err) -> {
                                    if (err != null) return;
                                    if (doc != null && doc.exists()) {
                                        String name = doc.getString("name");
                                        boolean updated = false;
                                        for (int i = 0; i < userPantries.size(); i++) {
                                            if (userPantries.get(i).getId().equals(doc.getId())) {
                                                userPantries.set(i, new Pantry(doc.getId(), name));
                                                updated = true;
                                                break;
                                            }
                                        }
                                        if (!updated) {
                                            userPantries.add(new Pantry(doc.getId(), name));
                                        }
                                        updateSpinner();
                                    }
                                });
                        pantryListeners.add(reg);
                    }
                } else {
                    updateSpinner();
                }
            }
        });
    }

    private void updateSpinner() {
        spinnerItems.clear();
        spinnerItems.add("All");
        for (Pantry pantry : userPantries) {
            spinnerItems.add(pantry.getName());
        }

        ArrayAdapter<String> spinnerAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerItems);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pantrySpinner.setAdapter(spinnerAdapter);

        pantrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                for (ListenerRegistration l : itemListeners) l.remove();
                itemListeners.clear();
                if (position == 0) {
                    loadAllPantriesData();
                } else {
                    Pantry selectedPantry = userPantries.get(position - 1);
                    loadPantryData(selectedPantry.getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (pantrySpinner.getSelectedItemPosition() == 0) {
            loadAllPantriesData();
        }
    }

    private void showJoinPantryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Pantry");
        final EditText input = new EditText(this);
        input.setHint("Invitation Code");
        builder.setView(input);

        builder.setPositiveButton("Join", (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (!code.isEmpty()) {
                joinPantry(code);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void joinPantry(String code) {
        db.collection("invitations")
                .whereEqualTo("code", code)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot invitationDoc = querySnapshot.getDocuments().get(0);
                        String pantryId = invitationDoc.getString("pantryId");

                        db.collection("Users").document(userId)
                                .update("pantries", FieldValue.arrayUnion(pantryId))
                                .addOnSuccessListener(aVoid -> {
                                    invitationDoc.getReference().delete();
                                    Toast.makeText(this, "Joined pantry", Toast.LENGTH_SHORT).show();
                                    loadUserPantries();
                                });
                    } else {
                        Toast.makeText(this, "Invalid invitation code", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadPantryData(String pantryId) {
        CollectionReference itemsRef = db.collection("Pantries").document(pantryId).collection("items");
        ListenerRegistration listener = itemsRef.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show();
                return;
            }
            if (querySnapshot != null) {
                processItems(querySnapshot.getDocuments());
            }
        });
        itemListeners.add(listener);
    }

    private void loadAllPantriesData() {
        if (userPantries.isEmpty()) {
            updateUI(0, 0, new ArrayList<>());
            return;
        }

        for (Pantry pantry : userPantries) {
            CollectionReference itemsRef = db.collection("Pantries").document(pantry.getId()).collection("items");
            ListenerRegistration listener = itemsRef.addSnapshotListener((querySnapshot, e) -> {
                if (e != null) {
                    Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (querySnapshot != null) {
                    List<DocumentSnapshot> allDocs = new ArrayList<>();
                    for (Pantry p : userPantries) {
                        CollectionReference ref = db.collection("Pantries").document(p.getId()).collection("items");
                        ref.get().addOnSuccessListener(qs -> allDocs.addAll(qs.getDocuments()))
                                .addOnCompleteListener(task -> processItems(allDocs));
                    }
                }
            });
            itemListeners.add(listener);
        }
    }

    private void processItems(List<DocumentSnapshot> documents) {
        List<Item> items = new ArrayList<>();
        for (DocumentSnapshot doc : documents) {
            Item item = doc.toObject(Item.class);
            if (item != null) {
                items.add(item);
            }
        }

        int total = items.size();
        int soonToExpire = 0;
        List<Item> expiringList = new ArrayList<>();

        long now = System.currentTimeMillis();
        long sevenDaysMillis = TimeUnit.DAYS.toMillis(7);

        for (Item item : items) {
            Timestamp expiry = item.getExpiryDate();
            if (expiry != null) {
                long expiryTime = expiry.toDate().getTime();
                if (expiryTime <= now + sevenDaysMillis) {
                    soonToExpire++;
                    expiringList.add(item);
                }
            }
        }

        updateUI(total, soonToExpire, expiringList);
    }

    private void updateUI(int total, int soonToExpire, List<Item> expiringList) {
        totalItemsText.setText("Total Items: " + total);
        expiringSoonText.setText("Expiring Soon: " + soonToExpire);
        adapter.updateList(expiringList);
    }
}