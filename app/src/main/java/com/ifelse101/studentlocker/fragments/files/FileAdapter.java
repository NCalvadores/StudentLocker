package com.ifelse101.studentlocker.fragments.files;

import android.text.format.Formatter;
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
import com.ifelse101.studentlocker.model.FileModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FileAdapter extends ListAdapter<FileModel, FileAdapter.FileViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(FileModel file);
    }

    private static final String UNNAMED_FILE = "Unnamed File";
    private static final String LABEL_SYNCED = "Synced";
    private static final String LABEL_LOCAL = "Local";
    private static final String DETAILS_SEPARATOR = " • ";
    private static final String FOLDER_ICON = "📁 ";

    @Nullable
    private OnFileClickListener clickListener;

    private static final DiffUtil.ItemCallback<FileModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FileModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull FileModel oldItem, @NonNull FileModel newItem) {
                    return Objects.equals(oldItem.getFileId(), newItem.getFileId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull FileModel oldItem, @NonNull FileModel newItem) {
                    return Objects.equals(oldItem.getFileName(), newItem.getFileName())
                            && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                            && oldItem.getSizeBytes() == newItem.getSizeBytes()
                            && oldItem.isSynced() == newItem.isSynced();
                }
            };

    public FileAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnFileClickListener(@Nullable OnFileClickListener listener) {
        this.clickListener = listener;
    }

    public void setFiles(@Nullable List<FileModel> newFiles) {
        submitList(newFiles == null ? null : new ArrayList<>(newFiles));
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener);
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFileName;
        private final TextView tvFileDetails;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
        }

        void bind(@NonNull FileModel file, @Nullable OnFileClickListener clickListener) {
            tvFileName.setText(displayNameOf(file));
            tvFileDetails.setText(buildDetails(file));

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onFileClick(file);
                }
            });
        }

        private String displayNameOf(FileModel file) {
            String name = file.getFileName();
            return name != null && !name.trim().isEmpty() ? name : UNNAMED_FILE;
        }

        private String buildDetails(FileModel file) {
            String syncLabel = file.isSynced() ? LABEL_SYNCED : LABEL_LOCAL;
            String category = file.getCategory() != null ? file.getCategory() : FileModel.DEFAULT_CATEGORY;
            String size = Formatter.formatShortFileSize(itemView.getContext(), file.getSizeBytes());

            return FOLDER_ICON + category
                    + DETAILS_SEPARATOR + syncLabel
                    + DETAILS_SEPARATOR + size;
        }
    }
}