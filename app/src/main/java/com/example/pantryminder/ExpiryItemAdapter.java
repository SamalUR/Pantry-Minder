package com.example.pantryminder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ExpiryItemAdapter extends RecyclerView.Adapter<ExpiryItemAdapter.ViewHolder> {

    private List<Item> itemList;

    public ExpiryItemAdapter(List<Item> itemList) {
        this.itemList = itemList;
    }

    public void updateList(List<Item> newList) {
        this.itemList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expiry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = itemList.get(position);
        holder.itemNameText.setText(item.getName());

        if (item.getExpiryDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String expiryStr = sdf.format(item.getExpiryDate().toDate());
            holder.itemExpiryText.setText("Expiry: " + expiryStr);
        } else {
            holder.itemExpiryText.setText("Expiry: N/A");
        }

        holder.itemCategoryText.setText("Category: " + item.getCategory());
        holder.itemQuantityText.setText("Quantity: " + item.getQuantity() + " " + item.getUnit());
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemNameText;
        TextView itemExpiryText;
        TextView itemCategoryText;
        TextView itemQuantityText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameText = itemView.findViewById(R.id.itemNameText);
            itemExpiryText = itemView.findViewById(R.id.itemExpiryText);
            itemCategoryText = itemView.findViewById(R.id.itemCategoryText);
            itemQuantityText = itemView.findViewById(R.id.itemQuantityText);
        }
    }
}