package com.example.pantryminder;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;

public class AddItemActivity extends AppCompatActivity {


    private TextInputEditText nameEditText, quantityEditText, unitEditText, expiryEditText, categoryEditText;
    private MaterialButton addButton, pickExpiryButton, micAll;
    private Spinner categorySpinner, unitSpinner;
    private FirebaseFirestore db;
    private String pantryId;
    private Map<String, List<String>> categoryUnitsMap;
    private List<String> categories;
    private static final int REQUEST_CODE_ALL_FIELDS = 1;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        pantryId = getIntent().getStringExtra("pantryId");
        if (pantryId == null || pantryId.isEmpty()) {
            Toast.makeText(this, "❌ No pantry selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        if (!initViews()) {
            Toast.makeText(this, "❌ Layout error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setupSpinners();
        setupClickListeners();

        Toast.makeText(this, "✅ Ready to add item", Toast.LENGTH_SHORT).show();
    }

    private boolean initViews() {
        try {
            nameEditText = findViewById(R.id.name_edit_text);
            categoryEditText = findViewById(R.id.category_edit_text);
            categorySpinner = findViewById(R.id.category_spinner);
            quantityEditText = findViewById(R.id.quantity_edit_text);
            unitEditText = findViewById(R.id.unit_edit_text);
            unitSpinner = findViewById(R.id.unit_spinner);
            expiryEditText = findViewById(R.id.expiry_edit_text);
            addButton = findViewById(R.id.btn_add);
            pickExpiryButton = findViewById(R.id.btn_pick_expiry);
            micAll = findViewById(R.id.mic_all);
            progressBar = findViewById(R.id.progress_bar);

            return nameEditText != null && categorySpinner != null && addButton != null;
        } catch (Exception e) {
            return false;
        }
    }
    private void selectSpinnerValue(Spinner spinner, String valueToMatch) {
        if (spinner == null || spinner.getAdapter() == null || valueToMatch == null || valueToMatch.isEmpty()) {
            return;
        }
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            String item = adapter.getItem(i).toString();
            if (item.equalsIgnoreCase(valueToMatch) || item.toLowerCase().contains(valueToMatch.toLowerCase())) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (micAll != null) micAll.setEnabled(!isLoading);
        if (addButton != null) addButton.setEnabled(!isLoading);
    }
    private void setupSpinners() {
        categoryUnitsMap = createCategoryUnitsMap();
        categories = createCategoriesList();

        if (categorySpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, categories);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(adapter);

            categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateUnitSpinner(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void setupClickListeners() {
        if (pickExpiryButton != null) {
            pickExpiryButton.setOnClickListener(v -> showDatePickerDialog());
        }
        if (addButton != null) {
            addButton.setOnClickListener(v -> addItem());
        }
        if (micAll != null) {
            micAll.setOnClickListener(v -> checkPermissionAndStartVoice());
        }
    }

    private void updateUnitSpinner(int categoryPosition) {
        if (unitSpinner == null || categoryPosition < 0 || categoryPosition >= categories.size()) {
            return;
        }

        String category = categories.get(categoryPosition).toLowerCase();
        List<String> units = categoryUnitsMap.getOrDefault(category, new ArrayList<>());

        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
    }

    private void checkPermissionAndStartVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        startVoiceInput();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput();
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: 2 liters milk tomorrow");
        try {
            startActivityForResult(intent, REQUEST_CODE_ALL_FIELDS);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ALL_FIELDS && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0).toLowerCase().trim();
                parseVoiceCommand(spokenText);
            }
        }
    }


    private void parseVoiceCommand(String spokenText) {
        setLoading(true);

        String url = "http://localhost:3000/api/voice/parse";

        JSONObject postData = new JSONObject();
        try {
            postData.put("text", spokenText);
        } catch (JSONException e) {
            e.printStackTrace();
            setLoading(false);
            Toast.makeText(this, "Failed to build request", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                postData,
                response -> {
                    setLoading(false);
                    try {
                        boolean success = response.getBoolean("success");
                        if (success) {
                            JSONObject data = response.getJSONObject("data");

                            String name = data.optString("name", "");
                            int quantity = data.optInt("quantity", 1);
                            String unit = data.optString("unit", "");
                            String category = data.optString("category", "");
                            String expiryDate = data.optString("expiryDate", "");

                            clearAllFields();

                            // Fill Name, Quantity & Expiry Date
                            if (!name.isEmpty()) nameEditText.setText(capitalizeFirst(name));
                            quantityEditText.setText(String.valueOf(quantity));
                            if (!expiryDate.equals("null") && !expiryDate.isEmpty()) {
                                expiryEditText.setText(expiryDate);
                            }

                            // Category — Spinner එකේ ඇත්නම් Select කරයි, නැත්නම් Custom EditText එකට දමයි
                            if (!category.isEmpty()) {
                                handleCategoryOrUnitInput(categorySpinner, categoryEditText, category);
                            }

                            // Unit — Category එක මාරු වී Spinner එක Refresh වන තෙක් 200ms පොඩි delay එකකින් Update කරයි
                            if (!unit.isEmpty()) {
                                unitSpinner.postDelayed(() ->
                                        handleCategoryOrUnitInput(unitSpinner, unitEditText, unit), 200);
                            }

                            showPerfectFeedback();
                        } else {
                            Toast.makeText(this, "Could not process voice input", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error reading server response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    setLoading(false);
                    error.printStackTrace();
                    Toast.makeText(this, "Server error. Ensure backend is running.", Toast.LENGTH_LONG).show();
                }
        );

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(jsonObjectRequest);
    }

    private void handleCategoryOrUnitInput(Spinner spinner, TextInputEditText editText, String value) {
        if (value == null || value.isEmpty()) return;

        boolean foundInSpinner = false;
        if (spinner != null && spinner.getAdapter() != null) {
            ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                String item = adapter.getItem(i).toString();
                if (item.equalsIgnoreCase(value) || item.toLowerCase().contains(value.toLowerCase())) {
                    spinner.setSelection(i);
                    foundInSpinner = true;
                    break;
                }
            }
        }

        // Spinner එකේ නැතිනම් Custom Value එකක් ලෙස EditText එකට එකතු කරයි
        if (!foundInSpinner && editText != null) {
            editText.setText(capitalizeFirst(value));
        }
    }

    private String findDate(List<String> words) {
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i).toLowerCase();
            if (word.equals("tomorrow")) return getTomorrowDate();
            if (word.equals("today")) return getTodayDate();
            if (word.equals("next") && i + 1 < words.size() && words.get(i + 1).equals("week")) {
                return getNextWeekDate();
            }
        }
        return null;
    }

    private List<String> findDateKeywords(String date) {
        if (date != null) {
            if (date.contains("tomorrow")) return Arrays.asList("tomorrow");
            if (date.contains("today")) return Arrays.asList("today");
            if (date.contains("next week")) return Arrays.asList("next", "week");
        }
        return new ArrayList<>();
    }

    private String findQuantity(List<String> words) {
        for (String word : words) {
            if (word.matches("\\d+")) return word;
        }
        return null;
    }

    private String findUnit(List<String> words) {
        String[] units = {"liter", "litre", "ml", "gram", "g", "kg", "piece", "pieces",
                "pack", "packet", "bottle", "can", "box", "dozen", "bunch"};
        for (String word : words) {
            for (String unit : units) {
                if (word.contains(unit)) return unit;
            }
        }
        return null;
    }

    private String findCategoryKeyword(List<String> words) {
        Map<String, String> keywords = new HashMap<>();
        keywords.put("milk", "Dairy Products");
        keywords.put("cheese", "Dairy Products");
        keywords.put("apple", "Fruits");
        keywords.put("banana", "Fruits");
        keywords.put("rice", "Grains & Cereals");
        keywords.put("bread", "Bakery Items");
        keywords.put("tomato", "Vegetables");
        keywords.put("chicken", "Meat & Seafood");
        keywords.put("water", "Beverages");
        keywords.put("chips", "Snacks & Sweets");

        for (String word : words) {
            if (keywords.containsKey(word.toLowerCase())) {
                return keywords.get(word.toLowerCase());
            }
        }
        return null;
    }

    private void removeWords(List<String> wordList, String target) {
        wordList.removeIf(word -> word.equalsIgnoreCase(target));
    }

    private void removeWords(List<String> wordList, List<String> targets) {
        for (String target : targets) {
            removeWords(wordList, target);
        }
    }

    private List<String> createCategoriesList() {
        return Arrays.asList(
                "Grains & Cereals", "Vegetables", "Fruits", "Dairy Products",
                "Meat & Seafood", "Spices & Condiments", "Beverages",
                "Snacks & Sweets", "Canned & Frozen Foods", "Bakery Items",
                "Oil & Fats", "Cleaning Supplies", "Others / Misc."
        );
    }

    private Map<String, List<String>> createCategoryUnitsMap() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("grains & cereals", Arrays.asList("Kilogram (kg)", "Gram (g)", "Packet", "Cup"));
        map.put("vegetables", Arrays.asList("Kilogram (kg)", "Gram (g)", "Piece(s)", "Bunch"));
        map.put("fruits", Arrays.asList("Kilogram (kg)", "Gram (g)", "Piece(s)", "Dozen"));
        map.put("dairy products", Arrays.asList("Liter (L)", "Milliliter (ml)", "Gram (g)", "Packet"));
        map.put("meat & seafood", Arrays.asList("Kilogram (kg)", "Gram (g)", "Piece(s)", "Packet"));
        map.put("spices & condiments", Arrays.asList("Gram (g)", "Teaspoon (tsp)", "Tablespoon (tbsp)", "Packet"));
        map.put("beverages", Arrays.asList("Liter (L)", "Milliliter (ml)", "Bottle", "Packet"));
        map.put("snacks & sweets", Arrays.asList("Packet", "Gram (g)", "Piece(s)", "Box"));
        map.put("canned & frozen foods", Arrays.asList("Can", "Packet", "Gram (g)", "Kilogram (kg)"));
        map.put("bakery items", Arrays.asList("Piece(s)", "Packet", "Gram (g)", "Box"));
        map.put("oil & fats", Arrays.asList("Liter (L)", "Milliliter (ml)", "Bottle", "Jar"));
        map.put("cleaning supplies", Arrays.asList("Bottle", "Packet", "Piece(s)", "Gram (g)"));
        map.put("others / misc.", Arrays.asList("Piece(s)", "Packet", "Box", "Set"));
        return map;
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, yearSelected, monthSelected, dayOfMonth) -> {
                    calendar.set(yearSelected, monthSelected, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    expiryEditText.setText(sdf.format(calendar.getTime()));
                }, year, month, day);
        datePickerDialog.show();
    }


    private void showPerfectFeedback() {
        StringBuilder msg = new StringBuilder("✅ Filled: ");
        if (!getTextSafely(nameEditText).isEmpty()) msg.append("Name ");
        if (!getTextSafely(quantityEditText).isEmpty()) msg.append("Qty ");
        if (!getTextSafely(unitEditText).isEmpty()) msg.append("Unit ");
        if (!getTextSafely(expiryEditText).isEmpty()) msg.append("Date ");
        if (!getTextSafely(categoryEditText).isEmpty() || categorySpinner.getSelectedItemPosition() > 0) {
            msg.append("Category ");
        }
        Toast.makeText(this, msg.toString(), Toast.LENGTH_LONG).show();
    }

    private String getTextSafely(TextInputEditText editText) {
        return editText != null ? editText.getText().toString().trim() : "";
    }

    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private void clearAllFields() {
        if (nameEditText != null) nameEditText.setText("");
        if (categoryEditText != null) categoryEditText.setText("");
        if (quantityEditText != null) quantityEditText.setText("");
        if (unitEditText != null) unitEditText.setText("");
        if (expiryEditText != null) expiryEditText.setText("");
        if (categorySpinner != null) categorySpinner.setSelection(0);
        if (unitSpinner != null) unitSpinner.setSelection(0);
    }

    private String getTomorrowDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getNextWeekDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void populateCategoryField(String category) {
        if (categorySpinner != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(category)) {
                    categorySpinner.setSelection(i);
                    return;
                }
            }
        }
        if (categoryEditText != null) {
            categoryEditText.setText(category);
        }
    }

    private void addItem() {
        String name = getTextSafely(nameEditText);
        String category = getTextSafely(categoryEditText);
        if (category.isEmpty() && categorySpinner != null && categorySpinner.getSelectedItem() != null) {
            category = categorySpinner.getSelectedItem().toString();
        }
        String quantityStr = getTextSafely(quantityEditText);
        String unit = getTextSafely(unitEditText);
        if (unit.isEmpty() && unitSpinner != null && unitSpinner.getSelectedItem() != null) {
            unit = unitSpinner.getSelectedItem().toString();
        }
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


        Item item = new Item();
        item.setName(name);
        item.setCategory(category);
        item.setQuantity(quantity);
        item.setUnit(unit);
        item.setExpiryDate(expiry);

        db.collection("Pantries").document(pantryId).collection("items").add(item)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "✅ Item added successfully!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Failed to add: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}