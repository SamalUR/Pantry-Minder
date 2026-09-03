package com.example.pantryminder;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;

public class ProfileActivity extends AppCompatActivity {
    private TextView nameText, uidText, emailText;
    private Button btnEditDetails;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = currentUser.getUid();

        nameText = findViewById(R.id.profile_name);
        uidText = findViewById(R.id.profile_uid);
        emailText = findViewById(R.id.profile_email);
        btnEditDetails = findViewById(R.id.btn_edit_details);

        uidText.setText("User ID: " + userId);
        emailText.setText("Email: " + currentUser.getEmail());

        DocumentReference userRef = db.collection("Users").document(userId);
        userRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String name = doc.getString("name");
                nameText.setText("Name: " + (name != null ? name : "Not set"));
            }
        });

        btnEditDetails.setOnClickListener(v -> showEditOptionsDialog());
    }

    private void showEditOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Details");
        String[] options = {"Change Name", "Change Email", "Change Password"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showChangeNameDialog();
                    break;
                case 1:
                    showChangeEmailDialog();
                    break;
                case 2:
                    showChangePasswordDialog();
                    break;
            }
        });
        builder.show();
    }

    private void showChangeNameDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_detail, null);
        EditText passwordEdit = dialogView.findViewById(R.id.edit_password);
        EditText newDetailEdit = dialogView.findViewById(R.id.edit_new_detail);
        newDetailEdit.setHint("New Name");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Name");
        builder.setView(dialogView);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String password = passwordEdit.getText().toString().trim();
            String newName = newDetailEdit.getText().toString().trim();
            if (password.isEmpty() || newName.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            reauthenticateAndUpdateName(password, newName);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showChangeEmailDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_detail, null);
        EditText passwordEdit = dialogView.findViewById(R.id.edit_password);
        EditText newDetailEdit = dialogView.findViewById(R.id.edit_new_detail);
        newDetailEdit.setHint("New Email");
        newDetailEdit.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Email");
        builder.setView(dialogView);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String password = passwordEdit.getText().toString().trim();
            String newEmail = newDetailEdit.getText().toString().trim();
            if (password.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            reauthenticateAndUpdateEmail(password, newEmail);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText currentPasswordEdit = dialogView.findViewById(R.id.edit_current_password);
        EditText newPasswordEdit = dialogView.findViewById(R.id.edit_new_password);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password");
        builder.setView(dialogView);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String currentPassword = currentPasswordEdit.getText().toString().trim();
            String newPassword = newPasswordEdit.getText().toString().trim();
            if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            reauthenticateAndUpdatePassword(currentPassword, newPassword);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void reauthenticateAndUpdateName(String password, String newName) {
        FirebaseUser user = mAuth.getCurrentUser();
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    db.collection("Users").document(userId).update("name", newName);
                    nameText.setText("Name: " + newName);
                    Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Authentication failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void reauthenticateAndUpdateEmail(String password, String newEmail) {
        FirebaseUser user = mAuth.getCurrentUser();
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    user.updateEmail(newEmail)
                            .addOnSuccessListener(aVoid2 -> {
                                emailText.setText("Email: " + newEmail);
                                Toast.makeText(this, "Email updated", Toast.LENGTH_SHORT).show();
                                // Update Firestore if needed
                                db.collection("Users").document(userId).update("email", newEmail);
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update email: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Authentication failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void reauthenticateAndUpdatePassword(String currentPassword, String newPassword) {
        FirebaseUser user = mAuth.getCurrentUser();
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid2 -> Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update password: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Authentication failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}