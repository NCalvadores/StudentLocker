package com.ifelse101.studentlocker.fragments.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ifelse101.studentlocker.R;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends ListAdapter<CategoryModel, CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryModel category);
    }

    private static final DiffUtil.ItemCallback<CategoryModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryModel oldItem, @NonNull CategoryModel newItem) {
                    return oldItem.getName().equalsIgnoreCase(newItem.getName());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryModel oldItem, @NonNull CategoryModel newItem) {
                    return oldItem.equals(newItem);
                }
            };

    @Nullable
    private OnCategoryClickListener listener;

    public CategoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnCategoryClickListener(@Nullable OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(@Nullable List<CategoryModel> categories) {
        submitList(categories == null ? null : new ArrayList<>(categories));
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCategoryName;
        private final TextView tvCategoryDocCount;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryDocCount = itemView.findViewById(R.id.tvCategoryDocCount);
        }

        void bind(CategoryModel category, @Nullable OnCategoryClickListener listener) {
            tvCategoryName.setText(category.getName());
            int count = category.getDocumentCount();
            String countText = count + (count == 1 ? " document" : " documents");
            tvCategoryDocCount.setText(countText);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
