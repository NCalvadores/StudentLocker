package com.ifelse101.studentlocker.fragments.files;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.ifelse101.studentlocker.R;
import com.ifelse101.studentlocker.data.cloud.FileUploadManager;
import com.ifelse101.studentlocker.data.cloud.FirebaseSyncManager;
import com.ifelse101.studentlocker.data.local.LocalFileManager;
import com.ifelse101.studentlocker.model.FileModel;
import com.ifelse101.studentlocker.util.FileOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilesFragment extends Fragment {

    private static final String TAG = "FilesFragment";
    private static final String ANY_FILE_TYPE = "*/*";
    private static final String CONTENT_SCHEME = "content";
    private static final String DEFAULT_FILE_NAME = "file_";

    private RecyclerView recyclerView;
    private View layoutEmptyState;
    private TextView tvEmptyState;
    private MaterialButton btnEmptyUpload;
    private MaterialButton btnSyncNow;

    private FileAdapter fileAdapter;
    private LocalFileManager localFileManager;
    private FileUploadManager fileUploadManager;
    private FirebaseSyncManager firebaseSyncManager;
    private ListenerRegistration syncListener;

    private String activeCategory = FileModel.DEFAULT_CATEGORY;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleFileSelected
    );

    public FilesFragment() {
        super(R.layout.fragment_files);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        initManagers();
        setupRecyclerView();
        setupButtons(view);
        startRealtimeSync();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtimeSync();
        recyclerView = null;
        layoutEmptyState = null;
        tvEmptyState = null;
        btnEmptyUpload = null;
        btnSyncNow = null;
    }

    private void bindViews(@NonNull View view) {
        recyclerView = view.findViewById(R.id.recyclerViewFiles);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        btnEmptyUpload = view.findViewById(R.id.btnEmptyUpload);
        btnSyncNow = view.findViewById(R.id.btnSyncNow);
    }

    private void initManagers() {
        localFileManager = new LocalFileManager(requireContext());
        fileUploadManager = new FileUploadManager(requireContext());
        firebaseSyncManager = new FirebaseSyncManager();
    }

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter();
        fileAdapter.setOnFileActionListener(new FileAdapter.OnFileActionListener() {
            @Override
            public void onFileClick(FileModel file) {
                FileOpenHelper.open(FilesFragment.this, file);
            }

            @Override
            public void onFileRename(FileModel file) {
                showRenameFileDialog(file);
            }

            @Override
            public void onFileDelete(FileModel file) {
                showDeleteFileDialog(file);
            }

            @Override
            public void onFileSync(FileModel file) {
                syncSingleFile(file);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(fileAdapter);
    }

    private void setupButtons(@NonNull View rootView) {
        rootView.findViewById(R.id.fabUploadFile)
                .setOnClickListener(v -> filePickerLauncher.launch(ANY_FILE_TYPE));

        if (btnEmptyUpload != null) {
            btnEmptyUpload.setOnClickListener(v -> filePickerLauncher.launch(ANY_FILE_TYPE));
        }

        if (btnSyncNow != null) {
            btnSyncNow.setOnClickListener(v -> syncAllEligibleFiles());
        }
    }

    // ------------------------------------------------------------------
    // Realtime sync & Local Merging
    // ------------------------------------------------------------------

    private void startRealtimeSync() {
        loadAndMergeFiles(null);

        if (!isSignedIn()) {
            return;
        }

        syncListener = firebaseSyncManager.listenToCloudFiles(new FirebaseSyncManager.SyncListCallback() {
            @Override
            public void onSuccess(List<FileModel> files) {
                if (!isAdded()) return;
                loadAndMergeFiles(files);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(TAG, "Error listening to cloud files", e);
                loadAndMergeFiles(null);
            }
        });
    }

    private void loadAndMergeFiles(@Nullable List<FileModel> cloudFiles) {
        List<FileModel> localFiles = localFileManager.getLocalFileModels();
        Map<String, FileModel> mergedMap = new LinkedHashMap<>();

        // Add cloud files
        if (cloudFiles != null) {
            for (FileModel cloudFile : cloudFiles) {
                String key = cloudFile.getFileName();
                mergedMap.put(key, cloudFile);
            }
        }

        // Add/merge local disk files
        for (FileModel localFile : localFiles) {
            String key = localFile.getFileName();
            if (mergedMap.containsKey(key)) {
                FileModel existing = mergedMap.get(key);
                if (existing != null && (existing.getLocalPath() == null || existing.getLocalPath().isEmpty())) {
                    existing.setLocalPath(localFile.getLocalPath());
                }
            } else {
                mergedMap.put(key, localFile);
            }
        }

        renderFiles(new ArrayList<>(mergedMap.values()));
    }

    private void stopRealtimeSync() {
        if (syncListener == null) return;
        syncListener.remove();
        syncListener = null;
    }

    private void renderFiles(List<FileModel> files) {
        if (files == null || files.isEmpty()) {
            showEmptyState();
            return;
        }
        showFileList(files);
    }

    private void showEmptyState() {
        if (recyclerView == null) return;
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else if (tvEmptyState != null) {
            tvEmptyState.setText(R.string.no_files_found);
            tvEmptyState.setVisibility(View.VISIBLE);
        }
        recyclerView.setVisibility(View.GONE);
    }

    private void showFileList(List<FileModel> files) {
        if (recyclerView == null) return;
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(View.GONE);
        } else if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE);
        }
        recyclerView.setVisibility(View.VISIBLE);
        fileAdapter.setFiles(files);
    }

    // ------------------------------------------------------------------
    // File Options: Save (Optional Sync), Rename, Delete, Sync All
    // ------------------------------------------------------------------

    private void handleFileSelected(@Nullable Uri uri) {
        if (uri == null) return;

        SelectedFile selectedFile = readSelectedFile(uri);

        File localCopy = localFileManager.saveFileLocally(selectedFile.uri, selectedFile.name, selectedFile.category);

        if (localCopy != null && localCopy.exists()) {
            showToast("Saved " + selectedFile.name + " locally in '" + selectedFile.category + "'");
        } else {
            showToast("Could not save file locally.");
        }

        loadAndMergeFiles(null);
    }

    private void showDeleteFileDialog(FileModel file) {
        String fileName = file.getFileName() != null ? file.getFileName() : "this file";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete '" + fileName + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (file.getLocalPath() != null && !file.getLocalPath().isEmpty()) {
                        localFileManager.deleteLocalFileByPath(file.getLocalPath());
                    }
                    localFileManager.deleteLocalFile(fileName, file.getCategory());

                    if (file.getFileId() != null && !file.getFileId().startsWith("local_")) {
                        firebaseSyncManager.deleteCloudMetadata(file.getFileId(), new FirebaseSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                showToast("File deleted.");
                                loadAndMergeFiles(null);
                            }

                            @Override
                            public void onError(Exception e) {
                                if (!isAdded()) return;
                                showToast("Local file deleted.");
                                loadAndMergeFiles(null);
                            }
                        });
                    } else {
                        showToast("File deleted.");
                        loadAndMergeFiles(null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameFileDialog(FileModel file) {
        String currentName = file.getFileName() != null ? file.getFileName() : "";

        EditText input = new EditText(requireContext());
        input.setText(currentName);
        if (!currentName.isEmpty()) {
            input.setSelection(currentName.length());
        }
        input.setPadding(32, 24, 32, 24);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename File")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.equals(currentName)) {
                        return;
                    }

                    if (file.getLocalPath() != null && !file.getLocalPath().isEmpty()) {
                        File renamedFile = localFileManager.renameLocalFile(file.getLocalPath(), newName, file.getCategory());
                        if (renamedFile != null) {
                            file.setLocalPath(renamedFile.getAbsolutePath());
                        }
                    }
                    file.setFileName(newName);
                    file.setUpdatedAt(System.currentTimeMillis());

                    if (file.getFileId() != null && !file.getFileId().startsWith("local_")) {
                        firebaseSyncManager.syncMetadataToCloud(file, new FirebaseSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                showToast("File renamed & synced.");
                                loadAndMergeFiles(null);
                            }

                            @Override
                            public void onError(Exception e) {
                                if (!isAdded()) return;
                                showToast("File renamed locally.");
                                loadAndMergeFiles(null);
                            }
                        });
                    } else {
                        showToast("File renamed to '" + newName + "'");
                        loadAndMergeFiles(null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void syncSingleFile(FileModel file) {
        if (!isSignedIn()) {
            showToast("Please sign in to sync files to the cloud.");
            return;
        }

        String cat = file.getCategory();
        boolean isGeneral = cat == null || cat.trim().isEmpty() || FileModel.DEFAULT_CATEGORY.equalsIgnoreCase(cat.trim());
        if (isGeneral) {
            showToast("Files in 'General' category stay local-only.");
            return;
        }

        if (file.isSynced()) {
            showToast("File is already synced.");
            return;
        }

        showToast("Uploading '" + file.getFileName() + "' to cloud...");
        uploadSingleFileToCloud(file);
    }

    private void syncAllEligibleFiles() {
        if (!isSignedIn()) {
            showToast("Please sign in to sync your files.");
            return;
        }

        showToast("Syncing cloud storage...");

        firebaseSyncManager.fetchCloudFiles(new FirebaseSyncManager.SyncListCallback() {
            @Override
            public void onSuccess(List<FileModel> cloudFiles) {
                if (!isAdded()) return;

                List<FileModel> localFiles = localFileManager.getLocalFileModels();
                int syncCount = 0;
                int generalCount = 0;

                for (FileModel localFile : localFiles) {
                    String cat = localFile.getCategory();
                    boolean isGeneral = cat == null || cat.trim().isEmpty() || FileModel.DEFAULT_CATEGORY.equalsIgnoreCase(cat.trim());

                    if (isGeneral) {
                        generalCount++;
                        continue;
                    }

                    boolean alreadySynced = false;
                    for (FileModel cloudFile : cloudFiles) {
                        if (cloudFile.getFileName() != null && cloudFile.getFileName().equalsIgnoreCase(localFile.getFileName())) {
                            alreadySynced = true;
                            break;
                        }
                    }

                    if (!alreadySynced) {
                        syncCount++;
                        uploadSingleFileToCloud(localFile);
                    }
                }

                loadAndMergeFiles(cloudFiles);

                if (syncCount > 0) {
                    showToast("Syncing " + syncCount + " files to cloud. (General files remain local)");
                } else if (generalCount > 0) {
                    showToast("Synced! (Files in 'General' category stay local)");
                } else {
                    showToast("All eligible files are up to date!");
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                showToast("Sync failed: " + e.getMessage());
            }
        });
    }

    private void uploadSingleFileToCloud(FileModel file) {
        if (file.getLocalPath() == null || file.getLocalPath().isEmpty()) return;
        File localFile = new File(file.getLocalPath());
        if (!localFile.exists()) return;

        Uri uri = Uri.fromFile(localFile);
        fileUploadManager.uploadFile(uri, file.getFileName(), file.getCategory(), new FileUploadManager.UploadCallback() {
            @Override
            public void onProgress(double percent) { }

            @Override
            public void onSuccess(String downloadUrl) {
                if (!isAdded()) return;
                file.setDownloadUrl(downloadUrl);
                file.setSynced(true);
                file.setUpdatedAt(System.currentTimeMillis());

                firebaseSyncManager.syncMetadataToCloud(file, new FirebaseSyncManager.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        showToast("Synced '" + file.getFileName() + "' to cloud.");
                        loadAndMergeFiles(null);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded()) return;
                        showToast("Metadata sync error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                showToast("Upload failed for '" + file.getFileName() + "': " + e.getMessage());
            }
        });
    }

    private SelectedFile readSelectedFile(Uri uri) {
        return new SelectedFile(
                uri,
                getFileNameFromUri(uri),
                getFileSizeFromUri(uri),
                requireContext().getContentResolver().getType(uri),
                activeCategory
        );
    }

    // ------------------------------------------------------------------
    // Uri file info accessors
    // ------------------------------------------------------------------

    private String getFileNameFromUri(Uri uri) {
        String displayName = queryDisplayName(uri);
        if (displayName != null) return displayName;

        String pathName = lastPathSegment(uri.getPath());
        if (pathName != null) return pathName;

        return DEFAULT_FILE_NAME + System.currentTimeMillis();
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        if (!isContentUri(uri)) return null;

        try (Cursor cursor = queryOpenableColumns(uri)) {
            if (cursor == null || !cursor.moveToFirst()) return null;

            int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            return columnIndex != -1 ? cursor.getString(columnIndex) : null;
        } catch (Exception e) {
            Log.e(TAG, "Error querying file name", e);
            return null;
        }
    }

    private long getFileSizeFromUri(Uri uri) {
        if (!isContentUri(uri)) return 0;

        try (Cursor cursor = queryOpenableColumns(uri)) {
            if (cursor == null || !cursor.moveToFirst()) return 0;

            int columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            return columnIndex != -1 ? cursor.getLong(columnIndex) : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error querying file size", e);
            return 0;
        }
    }

    @Nullable
    private Cursor queryOpenableColumns(Uri uri) {
        return requireContext().getContentResolver().query(uri, null, null, null, null);
    }

    private boolean isContentUri(Uri uri) {
        return CONTENT_SCHEME.equals(uri.getScheme());
    }

    @Nullable
    private String lastPathSegment(@Nullable String path) {
        if (path == null) return null;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private boolean isSignedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /** Snapshot of everything we know about the file the user just picked. */
    private static final class SelectedFile {
        final Uri uri;
        final String name;
        final long sizeBytes;
        @Nullable final String mimeType;
        final String category;

        SelectedFile(Uri uri, String name, long sizeBytes, @Nullable String mimeType, String category) {
            this.uri = uri;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.mimeType = mimeType;
            this.category = category;
        }
    }
}
