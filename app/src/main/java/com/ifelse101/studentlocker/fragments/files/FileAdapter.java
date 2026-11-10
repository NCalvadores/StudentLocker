package com.ifelse101.studentlocker.fragments.files;

import android.content.Context;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
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

    public interface OnFileActionListener {
        void onFileClick(FileModel file);
        void onFileRename(FileModel file);
        void onFileDelete(FileModel file);
        void onFileSync(FileModel file);
    }

    private static final String UNNAMED_FILE = "Unnamed File";
    private static final String DETAILS_SEPARATOR = " • ";
    private static final String FOLDER_ICON = "📁 ";

    private static final int MENU_OPEN = 1;
    private static final int MENU_RENAME = 2;
    private static final int MENU_SYNC = 3;
    private static final int MENU_DELETE = 4;

    @Nullable
    private OnFileActionListener actionListener;

    private static final DiffUtil.ItemCallback<FileModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FileModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull FileModel oldItem, @NonNull FileModel newItem) {
                    return Objects.equals(oldItem.getFileId(), newItem.getFileId())
                            || (oldItem.getFileName() != null && oldItem.getFileName().equals(newItem.getFileName()));
                }

                @Override
                public boolean areContentsTheSame(@NonNull FileModel oldItem, @NonNull FileModel newItem) {
                    return Objects.equals(oldItem.getFileName(), newItem.getFileName())
                            && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                            && oldItem.getSizeBytes() == newItem.getSizeBytes()
                            && oldItem.isSynced() == newItem.isSynced()
                            && oldItem.getUpdatedAt() == newItem.getUpdatedAt();
                }
            };

    public FileAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnFileClickListener(@Nullable OnFileClickListener listener) {
        if (listener == null) {
            this.actionListener = null;
        } else {
            this.actionListener = new OnFileActionListener() {
                @Override
                public void onFileClick(FileModel file) {
                    listener.onFileClick(file);
                }

                @Override
                public void onFileRename(FileModel file) {}

                @Override
                public void onFileDelete(FileModel file) {}

                @Override
                public void onFileSync(FileModel file) {}
            };
        }
    }

    public void setOnFileActionListener(@Nullable OnFileActionListener listener) {
        this.actionListener = listener;
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
        holder.bind(getItem(position), actionListener);
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFileName;
        private final TextView tvFileDetails;
        private final ImageView imgSyncStatus;
        private final TextView tvSyncStatus;
        private final ImageButton btnFileMenu;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
            imgSyncStatus = itemView.findViewById(R.id.imgSyncStatus);
            tvSyncStatus = itemView.findViewById(R.id.tvSyncStatus);
            btnFileMenu = itemView.findViewById(R.id.btnFileMenu);
        }

        void bind(@NonNull FileModel file, @Nullable OnFileActionListener listener) {
            Context context = itemView.getContext();

            tvFileName.setText(displayNameOf(file));
            tvFileDetails.setText(buildDetails(context, file));

            bindSyncState(context, file);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileClick(file);
                }
            });

            btnFileMenu.setOnClickListener(v -> showPopupMenu(v.getContext(), btnFileMenu, file, listener));
        }

        private void showPopupMenu(Context context, View anchor, FileModel file, @Nullable OnFileActionListener listener) {
            PopupMenu popup = new PopupMenu(context, anchor);
            Menu menu = popup.getMenu();

            menu.add(Menu.NONE, MENU_OPEN, 1, "Open");
            menu.add(Menu.NONE, MENU_RENAME, 2, "Rename");
            if (!file.isSynced()) {
                menu.add(Menu.NONE, MENU_SYNC, 3, "Sync to Cloud");
            }
            menu.add(Menu.NONE, MENU_DELETE, 4, "Delete");

            popup.setOnMenuItemClickListener(item -> {
                if (listener == null) return false;
                switch (item.getItemId()) {
                    case MENU_OPEN:
                        listener.onFileClick(file);
                        return true;
                    case MENU_RENAME:
                        listener.onFileRename(file);
                        return true;
                    case MENU_SYNC:
                        listener.onFileSync(file);
                        return true;
                    case MENU_DELETE:
                        listener.onFileDelete(file);
                        return true;
                    default:
                        return false;
                }
            });

            popup.show();
        }

        private void bindSyncState(Context context, FileModel file) {
            if (file.isSynced()) {
                imgSyncStatus.setImageResource(R.drawable.ic_cloud_done);
                tvSyncStatus.setText("Cloud Synced");
                int primaryColor = ContextCompat.getColor(context, R.color.black);
                tvSyncStatus.setTextColor(primaryColor);
                imgSyncStatus.setColorFilter(primaryColor);
            } else {
                imgSyncStatus.setImageResource(R.drawable.ic_cloud_off);
                tvSyncStatus.setText("Local Only");
                tvSyncStatus.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
                imgSyncStatus.setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray));
            }
        }

        private String displayNameOf(FileModel file) {
            String name = file.getFileName();
            return name != null && !name.trim().isEmpty() ? name : UNNAMED_FILE;
        }

        private String buildDetails(Context context, FileModel file) {
            String category = file.getCategory() != null ? file.getCategory() : FileModel.DEFAULT_CATEGORY;
            String size = Formatter.formatShortFileSize(context, file.getSizeBytes());

            String timeStr = "";
            if (file.getUpdatedAt() > 0) {
                CharSequence relative = DateUtils.getRelativeTimeSpanString(
                        file.getUpdatedAt(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                );
                timeStr = DETAILS_SEPARATOR + relative;
            }

            return FOLDER_ICON + category
                    + DETAILS_SEPARATOR + size
                    + timeStr;
        }
    }
}
