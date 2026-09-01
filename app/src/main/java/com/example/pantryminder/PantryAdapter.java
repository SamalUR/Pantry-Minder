package com.example.pantryminder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PantryAdapter extends RecyclerView.Adapter<PantryAdapter.ViewHolder> {

    private List<Pantry> pantryList;
    private OnPantryClickListener listener;

    public PantryAdapter(List<Pantry> pantryList, OnPantryClickListener listener) {
        this.pantryList = pantryList;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Pantry pantry = pantryList.get(position);
        holder.textView.setText(pantry.getName());
        holder.itemView.setOnClickListener(v -> listener.onPantryClick(pantry));
    }

    @Override
    public int getItemCount() {
        return pantryList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }

    public interface OnPantryClickListener {
        void onPantryClick(Pantry pantry);
    }
}