package com.example.pantryminder;

import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import com.google.android.material.navigation.NavigationView;
import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.example.pantryminder.Item;

public class HomeActivity extends AppCompatActivity {

    private Spinner pantrySpinner;
    private TextView expiringSoonText;
    private TextView totalItemsText;
    private RecyclerView expiringItemsRecycler;
    private ExpiryItemAdapter adapter;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    private List<Pantry> userPantries = new ArrayList<>();
    private List<String> spinnerItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e("HomeActivity", "No authenticated user found, redirecting to login");
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        userId = mAuth.getCurrentUser().getUid();

        // Initialize UI
        pantrySpinner = findViewById(R.id.pantrySpinner);
        expiringSoonText = findViewById(R.id.expiringSoonText);
        totalItemsText = findViewById(R.id.totalItemsText);
        expiringItemsRecycler = findViewById(R.id.expiringItemsRecycler);

        if (pantrySpinner == null || expiringSoonText == null || totalItemsText == null || expiringItemsRecycler == null) {
            Log.e("HomeActivity", "One or more UI elements not found in layout");
            Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Set up Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() == null) {
            Log.e("HomeActivity", "SupportActionBar is null after setting Toolbar");
            Toast.makeText(this, "App bar initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        expiringItemsRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpiryItemAdapter(new ArrayList<>());
        expiringItemsRecycler.setAdapter(adapter);

        // Set up the ActionBar toggle with Toolbar
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        /* Enable the Up button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);     // Enable home button
        toggle.setDrawerIndicatorEnabled(true);  */            // Force hamburger icon



        // Set navigation item selected listener
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_profile) {
                    startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                } else if (id == R.id.nav_pantry_list) {
                    startActivity(new Intent(HomeActivity.this, PantryListActivity.class));
                } else if (id == R.id.nav_logout) {
                    mAuth.signOut();
                    startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                    finish();
                }

                drawerLayout.closeDrawer(navigationView);
                return true;
            }
        });

        // Load user's pantries
        loadUserPantries();
    }
    @Override
    public boolean onSupportNavigateUp() {
        return toggle.onOptionsItemSelected(null) || super.onSupportNavigateUp();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadUserPantries() {
        DocumentReference userRef = db.collection("Users").document(userId);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<String> pantryIds = (List<String>) documentSnapshot.get("pantries");
                if (pantryIds != null && !pantryIds.isEmpty()) {
                    List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
                    for (String pantryId : pantryIds) {
                        Task<DocumentSnapshot> task = db.collection("Pantries").document(pantryId).get();
                        tasks.add(task);
                    }
                    Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                        userPantries.clear();
                        for (Object obj : results) {
                            DocumentSnapshot doc = (DocumentSnapshot) obj;
                            if (doc.exists()) {
                                String name = doc.getString("name");
                                userPantries.add(new Pantry(doc.getId(), name));
                            }
                        }
                        updateSpinner();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load pantries", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    updateSpinner(); // Empty list, just show "All" (though no data)
                }
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateSpinner() {
        spinnerItems.clear();
        spinnerItems.add("All");
        for (Pantry pantry : userPantries) {
            spinnerItems.add(pantry.getName());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerItems);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pantrySpinner.setAdapter(spinnerAdapter);

        pantrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    loadAllPantriesData();
                } else {
                    Pantry selectedPantry = userPantries.get(position - 1);
                    loadPantryData(selectedPantry.getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Load default "All"
        if (pantrySpinner.getSelectedItemPosition() == 0) {
            loadAllPantriesData();
        }
    }

    private void loadPantryData(String pantryId) {
        CollectionReference itemsRef = db.collection("Pantries").document(pantryId).collection("items");
        itemsRef.get().addOnSuccessListener(querySnapshot -> {
            processItems(querySnapshot.getDocuments(), false);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadAllPantriesData() {
        if (userPantries.isEmpty()) {
            updateUI(0, 0, new ArrayList<>());
            return;
        }

        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (Pantry pantry : userPantries) {
            CollectionReference itemsRef = db.collection("Pantries").document(pantry.getId()).collection("items");
            tasks.add(itemsRef.get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<DocumentSnapshot> allDocs = new ArrayList<>();
            for (Object obj : results) {
                QuerySnapshot qs = (QuerySnapshot) obj;
                allDocs.addAll(qs.getDocuments());
            }
            processItems(allDocs, true);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load all items", Toast.LENGTH_SHORT).show();
        });
    }

    private void processItems(List<DocumentSnapshot> documents, boolean isAll) {
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

    private void updateUI(int total, int soonToExpire, List<com.example.pantryminder.Item> expiringList) {
        totalItemsText.setText("Total Items: " + total);
        expiringSoonText.setText("Expiring Soon: " + soonToExpire);
        adapter.updateList(expiringList);
    }

}