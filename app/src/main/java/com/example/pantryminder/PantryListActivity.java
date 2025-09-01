package com.example.pantryminder;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pantryminder.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class PantryListActivity extends AppCompatActivity {
    private ListView pantryListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantry_list);

        pantryListView = findViewById(R.id.pantry_list_view);
        loadPantries();
    }

    private void loadPantries() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("Users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<String> pantryIds = (List<String>) documentSnapshot.get("pantries");
                if (pantryIds != null) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, pantryIds);
                    pantryListView.setAdapter(adapter);
                }
            }
        });
    }
}