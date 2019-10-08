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
        mainListItem.infoRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "INFO RIGHT CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
        mainListItem.editRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "EDIT RIGHT CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
        mainListItem.infoLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "INFO left CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
        mainListItem.editLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "EDIT left CLICKED @" + position, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return shoppingList.size();
    }

    public static class MainListItem extends RecyclerView.ViewHolder {

        protected TextView mealTV;
        protected ImageView infoRightButton;
        protected ImageView editRightButton;
        protected ImageView infoLeftButton;
        protected ImageView editLeftButton;

        protected MainListItem(View itemView) {
            super(itemView);
            mealTV = itemView.findViewById(R.id.meal_tv);
            infoRightButton = itemView.findViewById(R.id.info_button);
            editRightButton= itemView.findViewById(R.id.edit_button);
            infoLeftButton = itemView.findViewById(R.id.left_info_button);
            editLeftButton= itemView.findViewById(R.id.left_edit_button);
        }
    }
}
