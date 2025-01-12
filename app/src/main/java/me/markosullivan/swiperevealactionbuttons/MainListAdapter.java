package me.markosullivan.swiperevealactionbuttons;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Created by Mark O'Sullivan on 25th February 2018.
 */

public class MainListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<String> shoppingList;

    public MainListAdapter(List<String> shoppingList) {
        this.shoppingList = shoppingList;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_main, parent, false);
        return new MainListItem(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
        MainListItem mainListItem = (MainListItem) holder;
        mainListItem.mealTV.setText(shoppingList.get(position));
        mainListItem.infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "INFO CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
        mainListItem.editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "EDIT CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return shoppingList.size();
    }

    public static class MainListItem extends RecyclerView.ViewHolder {

        protected TextView mealTV;
        protected ImageView infoButton;
        protected ImageView editButton;

        protected MainListItem(View itemView) {
            super(itemView);
            mealTV = itemView.findViewById(R.id.meal_tv);
            infoButton = itemView.findViewById(R.id.info_button);
            editButton= itemView.findViewById(R.id.edit_button);
        }
    }
}
