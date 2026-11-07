package com.ifelse101.studentlocker.fragments.files;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private TextView tvEmptyState;

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
        setupUploadButton(view);
        startRealtimeSync();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtimeSync();
        recyclerView = null;
        tvEmptyState = null;
    }

    private void bindViews(@NonNull View view) {
        recyclerView = view.findViewById(R.id.recyclerViewFiles);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
    }

    private void initManagers() {
        localFileManager = new LocalFileManager(requireContext());
        fileUploadManager = new FileUploadManager(requireContext());
        firebaseSyncManager = new FirebaseSyncManager();
    }

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter();
        fileAdapter.setOnFileClickListener(file -> FileOpenHelper.open(this, file));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(fileAdapter);
    }

    private void setupUploadButton(@NonNull View rootView) {
        rootView.findViewById(R.id.fabUploadFile)
                .setOnClickListener(v -> filePickerLauncher.launch(ANY_FILE_TYPE));
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
        if (tvEmptyState == null || recyclerView == null) return;
        tvEmptyState.setText(R.string.no_files_found);
        tvEmptyState.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showFileList(List<FileModel> files) {
        if (tvEmptyState == null || recyclerView == null) return;
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        fileAdapter.setFiles(files);
    }

    // ------------------------------------------------------------------
    // Upload flow
    // ------------------------------------------------------------------

    private void handleFileSelected(@Nullable Uri uri) {
        if (uri == null) return;

        SelectedFile selectedFile = readSelectedFile(uri);
        showToast("Saving " + selectedFile.name + " locally...");

        File localCopy = localFileManager.saveFileLocally(selectedFile.uri, selectedFile.name, selectedFile.category);

        if (localCopy != null && localCopy.exists()) {
            showToast("Saved " + selectedFile.name + " locally in '" + selectedFile.category + "'");
            loadAndMergeFiles(null);
        }

        if (isSignedIn()) {
            uploadToCloud(selectedFile, localCopy);
        } else {
            showToast("Signed out. File stored offline.");
        }
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

    private void uploadToCloud(SelectedFile selectedFile, @Nullable File localCopy) {
        fileUploadManager.uploadFile(selectedFile.uri, selectedFile.name, selectedFile.category,
                new FileUploadManager.UploadCallback() {
                    @Override
                    public void onProgress(double percent) {
                        // Progress callback
                    }

                    @Override
                    public void onSuccess(String downloadUrl) {
                        if (!isAdded()) return;
                        syncMetadata(buildFileModel(selectedFile, localCopy, downloadUrl), selectedFile.category);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded()) return;
                        showToast("Saved locally. Cloud upload failed: " + e.getMessage());
                    }
                }
        );
    }

    private FileModel buildFileModel(SelectedFile selectedFile, @Nullable File localCopy, String downloadUrl) {
        FileModel model = new FileModel();
        model.setFileName(selectedFile.name);
        model.setLocalPath(localCopy != null ? localCopy.getAbsolutePath() : "");
        model.setDownloadUrl(downloadUrl);
        model.setSizeBytes(selectedFile.sizeBytes);
        model.setMimeType(selectedFile.mimeType);
        model.setCategory(selectedFile.category);
        model.setUpdatedAt(System.currentTimeMillis());
        return model;
    }

    private void syncMetadata(FileModel model, String category) {
        firebaseSyncManager.syncMetadataToCloud(model, new FirebaseSyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                showToast("File uploaded to '" + category + "' & synced successfully!");
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                showToast("File stored locally. Metadata sync will retry.");
            }
        });
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
