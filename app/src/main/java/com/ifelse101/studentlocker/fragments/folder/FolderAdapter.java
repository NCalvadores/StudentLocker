package com.ifelse101.studentlocker.fragments.folder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ifelse101.studentlocker.R;
import com.ifelse101.studentlocker.model.FolderModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FolderAdapter extends ListAdapter<FolderModel, FolderAdapter.FolderViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(FolderModel folder);
    }

    public interface OnFolderDeleteListener {
        void onFolderDelete(FolderModel folder);
    }

    private static final DiffUtil.ItemCallback<FolderModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FolderModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull FolderModel oldItem, @NonNull FolderModel newItem) {
                    String oldId = oldItem.getFolderId();
                    String newId = newItem.getFolderId();
                    if (oldId != null && newId != null && !oldId.equals(newId)) {
                        return false;
                    }
                    return Objects.equals(oldId, newId)
                            || Objects.equals(
                            FolderModel.normalizeName(oldItem.getName()),
                            FolderModel.normalizeName(newItem.getName())
                    );
                }

                @Override
                public boolean areContentsTheSame(@NonNull FolderModel oldItem, @NonNull FolderModel newItem) {
                    return Objects.equals(oldItem.getFolderId(), newItem.getFolderId())
                            && Objects.equals(FolderModel.normalizeName(oldItem.getName()), FolderModel.normalizeName(newItem.getName()))
                            && oldItem.getCreatedAt() == newItem.getCreatedAt()
                            && oldItem.getDocumentCount() == newItem.getDocumentCount();
                }
            };

    @Nullable
    private OnFolderClickListener clickListener;
    @Nullable
    private OnFolderDeleteListener deleteListener;

    public FolderAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnFolderClickListener(@Nullable OnFolderClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setOnFolderDeleteListener(@Nullable OnFolderDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setFolders(@Nullable List<FolderModel> folders) {
        submitList(folders == null ? null : new ArrayList<>(folders));
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, deleteListener);
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFolderName;
        private final TextView tvFolderDocCount;
        private final ImageButton btnDeleteFolder;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.tvFolderName);
            tvFolderDocCount = itemView.findViewById(R.id.tvFolderDocCount);
            btnDeleteFolder = itemView.findViewById(R.id.btnDeleteFolder);
        }

        void bind(@NonNull FolderModel folder,
                 @Nullable OnFolderClickListener clickListener,
                 @Nullable OnFolderDeleteListener deleteListener) {
            tvFolderName.setText(FolderModel.normalizeName(folder.getName()));

            int count = folder.getDocumentCount();
            String docText = count + (count == 1 ? " document" : " documents");
            tvFolderDocCount.setText(docText);

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onFolderClick(folder);
                }
            });

            btnDeleteFolder.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onFolderDelete(folder);
                }
            });
        }
    }
}
