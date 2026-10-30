package com.ifelse101.studentlocker.fragments.home;

import android.text.format.DateUtils;
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

public class RecentFileAdapter extends ListAdapter<FileModel, RecentFileAdapter.RecentViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(FileModel file);
    }

    private static final String UNNAMED_FILE = "Unnamed File";
    private static final String DETAILS_SEPARATOR = " • ";

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
                            && oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                            && oldItem.isSynced() == newItem.isSynced();
                }
            };

    @Nullable
    private OnFileClickListener listener;

    public RecentFileAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnFileClickListener(@Nullable OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setFiles(@Nullable List<FileModel> newFiles) {
        submitList(newFiles == null ? null : new ArrayList<>(newFiles));
    }

    @NonNull
    @Override
    public RecentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_file, parent, false);
        return new RecentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class RecentViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFileName;
        private final TextView tvFileDetails;

        RecentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
        }

        void bind(FileModel file, @Nullable OnFileClickListener listener) {
            String name = file.getFileName();
            tvFileName.setText(name != null ? name : UNNAMED_FILE);

            String syncStatus = file.isSynced() ? "Cloud Synced" : "Local Only";
            String sizeStr = Formatter.formatShortFileSize(itemView.getContext(), file.getSizeBytes());

            String timeStr = "";
            if (file.getUpdatedAt() > 0) {
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                        file.getUpdatedAt(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                );
                timeStr = DETAILS_SEPARATOR + relativeTime;
            }

            String details = syncStatus + DETAILS_SEPARATOR + sizeStr + timeStr;
            tvFileDetails.setText(details);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileClick(file);
                }
            });
        }
    }
}
