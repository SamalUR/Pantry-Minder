package com.example.pantryminder;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    private Spinner pantrySpinner;
    private TextView expiringSoonText;
    private TextView totalItemsText;
    private RecyclerView itemsRecycler;
    private ExpiryItemAdapter adapter;
    private EditText searchEditText;

    private ChipGroup filterChipGroup;
    private Chip chipAllItems, chipExpiringSoon;

    private DrawerLayout drawerLayout;
    private androidx.appcompat.app.ActionBarDrawerToggle toggle;
    private NavigationView navigationView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    private List<Pantry> userPantries = new ArrayList<>();
    private List<String> spinnerItems = new ArrayList<>();

    // Master list containing all loaded items
    private List<Item> masterItemList = new ArrayList<>();

    // Map to hold document id -> pantry id correlation
    private Map<String, String> itemPantryMap = new HashMap<>();

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        pantrySpinner = findViewById(R.id.pantrySpinner);
        expiringSoonText = findViewById(R.id.expiringSoonText);
        totalItemsText = findViewById(R.id.totalItemsText);
        itemsRecycler = findViewById(R.id.expiringItemsRecycler);
        searchEditText = findViewById(R.id.searchEditText);

        filterChipGroup = findViewById(R.id.filterChipGroup);
        chipAllItems = findViewById(R.id.chipAllItems);
        chipExpiringSoon = findViewById(R.id.chipExpiringSoon);

        itemsRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpiryItemAdapter(new ArrayList<>());
        itemsRecycler.setAdapter(adapter);

        // --- ITEM CLICK HANDLER: Redirect to EditItemActivity ---
        adapter.setOnItemClickListener(item -> {
            String itemId = item.getId();
            String pantryId = itemPantryMap.get(itemId);

            if (pantryId != null && !pantryId.isEmpty()) {
                Intent intent = new Intent(HomeActivity.this, EditItemActivity.class);
                intent.putExtra("pantryId", pantryId);
                intent.putExtra("itemId", itemId);
                startActivity(intent);
            } else {
                Toast.makeText(HomeActivity.this, "Unable to find pantry information for this item", Toast.LENGTH_SHORT).show();
            }
        });

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

        // Chip selection listener (All Items vs Expiring Soon)
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            applyFilters();
        });

        // Search Bar Text Watcher
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilters();
                }

                @Override
                public void afterTextChanged(Editable s) {}
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
        spinnerItems.add("All Pantries");
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
            public void onNothingSelected(AdapterView<?> parent) {}
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
            updateDashboardNumbers(0, 0);
            adapter.updateList(new ArrayList<>());
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
        masterItemList.clear();
        itemPantryMap.clear();

        for (DocumentSnapshot doc : documents) {
            Item item = doc.toObject(Item.class);
            if (item != null) {
                item.setId(doc.getId());
                masterItemList.add(item);

                // Map Item ID to Parent Pantry ID
                if (doc.getReference().getParent() != null && doc.getReference().getParent().getParent() != null) {
                    String pId = doc.getReference().getParent().getParent().getId();
                    itemPantryMap.put(doc.getId(), pId);
                }
            }
        }

        // Sort Alphabetically (A-Z) by Item Name
        Collections.sort(masterItemList, (i1, i2) -> {
            if (i1.getName() == null) return -1;
            if (i2.getName() == null) return 1;
            return i1.getName().compareToIgnoreCase(i2.getName());
        });

        int total = masterItemList.size();
        int soonToExpire = 0;

        long now = System.currentTimeMillis();
        long sevenDaysMillis = TimeUnit.DAYS.toMillis(7);

        for (Item item : masterItemList) {
            Timestamp expiry = item.getExpiryDate();
            if (expiry != null) {
                long expiryTime = expiry.toDate().getTime();
                if (expiryTime <= now + sevenDaysMillis) {
                    soonToExpire++;
                }
            }
        }

        updateDashboardNumbers(total, soonToExpire);
        applyFilters();
    }

    private void updateDashboardNumbers(int total, int soonToExpire) {
        totalItemsText.setText(String.valueOf(total));
        expiringSoonText.setText(String.valueOf(soonToExpire));
    }

    private void applyFilters() {
        List<Item> filteredList = new ArrayList<>();
        long now = System.currentTimeMillis();
        long sevenDaysMillis = TimeUnit.DAYS.toMillis(7);

        boolean isExpiringOnly = chipExpiringSoon.isChecked();
        String searchQuery = (searchEditText != null) ? searchEditText.getText().toString().trim().toLowerCase() : "";

        for (Item item : masterItemList) {
            boolean matchesExpiry = true;
            boolean matchesSearch = true;

            // Check Expiry Filter
            if (isExpiringOnly) {
                Timestamp expiry = item.getExpiryDate();
                if (expiry != null) {
                    long expiryTime = expiry.toDate().getTime();
                    matchesExpiry = (expiryTime <= now + sevenDaysMillis);
                } else {
                    matchesExpiry = false; // Exclude items with no expiry date when filtering expiring items
                }
            }

            // Check Search Filter
            if (!searchQuery.isEmpty()) {
                matchesSearch = (item.getName() != null && item.getName().toLowerCase().contains(searchQuery));
            }

            if (matchesExpiry && matchesSearch) {
                filteredList.add(item);
            }
        }

        adapter.updateList(filteredList);
    }
}