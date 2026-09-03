package com.example.pantryminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExpiryNotificationWorker extends Worker {
    private static final String CHANNEL_ID = "EXPIRY_NOTIFICATION_CHANNEL";
    private static final int NOTIFICATION_ID = 1;

    public ExpiryNotificationWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.failure();
        }
        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        List<Item> expiringItems = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        db.collection("Users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<String> pantryIds = (List<String>) documentSnapshot.get("pantries");
                    if (pantryIds != null && !pantryIds.isEmpty()) {
                        CountDownLatch pantryLatch = new CountDownLatch(pantryIds.size());
                        for (String pantryId : pantryIds) {
                            db.collection("Pantries").document(pantryId).collection("items").get()
                                    .addOnSuccessListener(querySnapshot -> {
                                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                            Item item = doc.toObject(Item.class);
                                            if (item != null && item.getExpiryDate() != null) {
                                                long expiryTime = item.getExpiryDate().toDate().getTime();
                                                long now = System.currentTimeMillis();
                                                long fiveDaysMillis = TimeUnit.DAYS.toMillis(5);

                                                // FIXED CONDITION:
                                                // ඉදිරි දින 5 ඇතුළත Expire වන සහ දැනටමත් Expire වී ඇති (ඊයේ/අද) සියලුම Items එකතු කර ගනී.
                                                if (expiryTime <= (now + fiveDaysMillis)) {
                                                    expiringItems.add(item);
                                                }
                                            }
                                        }
                                        pantryLatch.countDown();
                                    })
                                    .addOnFailureListener(e -> pantryLatch.countDown());
                        }
                        try {
                            pantryLatch.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {

                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return Result.failure();
        }

        if (!expiringItems.isEmpty()) {
            sendNotification(expiringItems);
        }

        return Result.success();
    }

    private void sendNotification(List<Item> expiringItems) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Expiry Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        StringBuilder content = new StringBuilder("The following items are expiring soon:\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (Item item : expiringItems) {
            content.append("• ")
                    .append(item.getName())
                    .append(" (")
                    .append(item.getQuantity())
                    .append(" ")
                    .append(item.getUnit())
                    .append(", Expiry: ")
                    .append(sdf.format(item.getExpiryDate().toDate()))
                    .append(")\n");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Resource error වළක්වා ගැනීමට Default Icon භාවිත කර ඇත
                .setContentTitle("Items Expiring Soon")
                .setContentText("You have " + expiringItems.size() + " item(s) expiring soon")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}