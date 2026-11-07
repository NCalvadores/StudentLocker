package com.ifelse101.studentlocker.fragments.home;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
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

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import com.ifelse101.studentlocker.R;
import com.ifelse101.studentlocker.model.FileModel;
import com.ifelse101.studentlocker.data.cloud.FileUploadManager;
import com.ifelse101.studentlocker.util.FileOpenHelper;
import com.ifelse101.studentlocker.data.local.LocalFileManager;
import com.ifelse101.studentlocker.model.FolderModel;
import com.ifelse101.studentlocker.data.cloud.FirebaseSyncManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final long TOTAL_STORAGE_BYTES = 15L * 1024 * 1024 * 1024; // 15 GB Quota

    private ShapeableImageView imgHomeAvatar;
    private TextView tvName;
    private TextView tvSyncStatus;
    private MaterialButton btnSyncNow;

    private TextView tvStorageRatio;
    private LinearProgressIndicator progressStorage;
    private TextView tvStorageFree;

    private MaterialCardView cardUpload;
    private MaterialCardView cardNewFolder;

    private TextView tvNoRecentFiles;
    private RecyclerView rvRecentFiles;
    private TextView tvNoCategories;
    private RecyclerView rvCategories;

    private LocalFileManager localFileManager;
    private FileUploadManager fileUploadManager;
    private FirebaseSyncManager firebaseSyncManager;

    private ListenerRegistration syncListener;
    private ListenerRegistration folderListener;

    private RecentFileAdapter recentFileAdapter;
    private CategoryAdapter categoryAdapter;

    private String activeCategory = FileModel.DEFAULT_CATEGORY;
    private final List<FolderModel> savedFolders = new ArrayList<>();
    private List<FileModel> cachedFiles = new ArrayList<>();

    private final ActivityResultLauncher<String> uploadFile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleFileSelected
    );

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        initManagersAndAdapters();
        loadUserData();
        setupListeners();
        startRealtimeSync();
    }

    private void bindViews(View view) {
        imgHomeAvatar = view.findViewById(R.id.imgHomeAvatar);
        tvName = view.findViewById(R.id.tvUserName);
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus);
        btnSyncNow = view.findViewById(R.id.btnSyncNow);

        tvStorageRatio = view.findViewById(R.id.tvStorageRatio);
        progressStorage = view.findViewById(R.id.progressStorage);
        tvStorageFree = view.findViewById(R.id.tvStorageFree);

        cardUpload = view.findViewById(R.id.cardUpload);
        cardNewFolder = view.findViewById(R.id.cardNewFolder);

        tvNoRecentFiles = view.findViewById(R.id.tvNoRecentFiles);
        rvRecentFiles = view.findViewById(R.id.rvRecentFiles);
        tvNoCategories = view.findViewById(R.id.tvNoCategories);
        rvCategories = view.findViewById(R.id.rvCategories);
    }

    private void initManagersAndAdapters() {
        localFileManager = new LocalFileManager(requireContext());
        fileUploadManager = new FileUploadManager(requireContext());
        firebaseSyncManager = new FirebaseSyncManager();

        recentFileAdapter = new RecentFileAdapter();
        rvRecentFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentFiles.setAdapter(recentFileAdapter);
        recentFileAdapter.setOnFileClickListener(file -> {
            FileOpenHelper.open(this, file);
        });

        categoryAdapter = new CategoryAdapter();
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);
        categoryAdapter.setOnCategoryClickListener(category -> {
            activeCategory = category.getName();
            Toast.makeText(requireContext(), "Category '" + category.getName() + "' selected for uploads.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String name = currentUser.getDisplayName();
            tvName.setText(name != null && !name.isEmpty() ? name : getString(R.string.default_user_name));

            Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .fallback(R.drawable.ic_person)
                    .into(imgHomeAvatar);
        } else {
            tvName.setText(R.string.default_user_name);
            imgHomeAvatar.setImageResource(R.drawable.ic_person);
            tvSyncStatus.setText(R.string.not_signed_in);
        }
    }

    private void setupListeners() {
        if (btnSyncNow != null) {
            btnSyncNow.setOnClickListener(v -> syncCloudStorage());
        }

        if (cardUpload != null) {
            cardUpload.setOnClickListener(v -> uploadFile.launch("*/*"));
        }

        if (cardNewFolder != null) {
            cardNewFolder.setOnClickListener(v -> showCreateFolderDialog());
        }
    }

    private void startRealtimeSync() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            tvSyncStatus.setText(R.string.not_signed_in);
            renderDashboardData(Collections.emptyList());
            return;
        }

        syncListener = firebaseSyncManager.listenToCloudFiles(new FirebaseSyncManager.SyncListCallback() {
            @Override
            public void onSuccess(List<FileModel> files) {
                if (!isAdded()) return;
                cachedFiles = files;
                renderDashboardData(files);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvSyncStatus.setText(R.string.sync_error);
                Log.e(TAG, "Failed to listen to cloud files", e);
                renderDashboardData(Collections.emptyList());
            }
        });

        folderListener = firebaseSyncManager.listenToFolders(new FirebaseSyncManager.FolderListCallback() {
            @Override
            public void onSuccess(List<FolderModel> folders) {
                if (!isAdded()) return;
                savedFolders.clear();
                savedFolders.addAll(folders);
                renderDashboardData(cachedFiles);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(TAG, "Failed to listen to folders", e);
            }
        });
    }

    private void stopRealtimeSync() {
        if (syncListener != null) {
            syncListener.remove();
            syncListener = null;
        }
        if (folderListener != null) {
            folderListener.remove();
            folderListener = null;
        }
    }

    private void renderDashboardData(@NonNull List<FileModel> cloudFiles) {
        List<FileModel> allFiles = mergeLocalAndCloudFiles(cloudFiles);

        // 1. Calculate and update Storage Overview
        long totalBytes = 0;
        for (FileModel f : allFiles) {
            totalBytes += f.getSizeBytes();
        }

        String usedStr = Formatter.formatShortFileSize(requireContext(), totalBytes);
        String quotaStr = Formatter.formatShortFileSize(requireContext(), TOTAL_STORAGE_BYTES);
        tvStorageRatio.setText(getString(R.string.storage_ratio_format, usedStr, quotaStr));

        int progress = (int) Math.min(100, (totalBytes * 100) / TOTAL_STORAGE_BYTES);
        progressStorage.setProgress(progress);

        long freeBytes = Math.max(0, TOTAL_STORAGE_BYTES - totalBytes);
        String freeStr = Formatter.formatShortFileSize(requireContext(), freeBytes);
        tvStorageFree.setText(getString(R.string.storage_free_format, freeStr));

        // 2. Sync status label
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            tvSyncStatus.setText(getString(R.string.cloud_synced));
        } else {
            tvSyncStatus.setText(R.string.not_signed_in);
        }

        // 3. Render Recent Documents (most recent first, top 5)
        if (allFiles.isEmpty()) {
            tvNoRecentFiles.setVisibility(View.VISIBLE);
            rvRecentFiles.setVisibility(View.GONE);
            recentFileAdapter.setFiles(Collections.emptyList());
        } else {
            tvNoRecentFiles.setVisibility(View.GONE);
            rvRecentFiles.setVisibility(View.VISIBLE);

            List<FileModel> sortedFiles = new ArrayList<>(allFiles);
            sortedFiles.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));

            List<FileModel> recentFiles = sortedFiles.subList(0, Math.min(sortedFiles.size(), 5));
            recentFileAdapter.setFiles(recentFiles);
        }

        // 4. Render Document Categories (including user-created folders)
        Map<String, Integer> categoryCounts = getStringIntegerMap(allFiles);

        if (categoryCounts.isEmpty()) {
            tvNoCategories.setVisibility(View.VISIBLE);
            rvCategories.setVisibility(View.GONE);
            categoryAdapter.setCategories(Collections.emptyList());
        } else {
            tvNoCategories.setVisibility(View.GONE);
            rvCategories.setVisibility(View.VISIBLE);

            List<CategoryModel> categories = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                categories.add(new CategoryModel(entry.getKey(), entry.getValue()));
            }
            categoryAdapter.setCategories(categories);
        }
    }

    private List<FileModel> mergeLocalAndCloudFiles(@NonNull List<FileModel> cloudFiles) {
        List<FileModel> localFiles = localFileManager.getLocalFileModels();
        Map<String, FileModel> map = new LinkedHashMap<>();

        for (FileModel cloudFile : cloudFiles) {
            map.put(cloudFile.getFileName(), cloudFile);
        }

        for (FileModel localFile : localFiles) {
            String key = localFile.getFileName();
            if (map.containsKey(key)) {
                FileModel existing = map.get(key);
                if (existing != null && (existing.getLocalPath() == null || existing.getLocalPath().isEmpty())) {
                    existing.setLocalPath(localFile.getLocalPath());
                }
            } else {
                map.put(key, localFile);
            }
        }

        return new ArrayList<>(map.values());
    }

    @NonNull
    private Map<String, Integer> getStringIntegerMap(@NonNull List<FileModel> files) {
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        // Add default category
        categoryCounts.put(FileModel.DEFAULT_CATEGORY, 0);

        // Add user-created folders from Firestore / local storage
        for (FolderModel folder : savedFolders) {
            if (folder.getName() != null && !folder.getName().trim().isEmpty()) {
                categoryCounts.put(folder.getName().trim(), 0);
            }
        }
        for (String localFolderName : localFileManager.getLocalFolderNames()) {
            categoryCounts.putIfAbsent(localFolderName, 0);
        }

        // Count documents per category
        for (FileModel f : files) {
            String cat = f.getCategory() != null && !f.getCategory().trim().isEmpty() ? f.getCategory().trim() : FileModel.DEFAULT_CATEGORY;
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);
        }
        return categoryCounts;
    }

    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("e.g. ID Documents, Certificates, Receipts");
        input.setPadding(32, 16, 32, 16);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create New Category / Folder")
                .setMessage("Enter category name for your documents:")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        activeCategory = folderName;
                        localFileManager.createLocalFolder(folderName);

                        FolderModel folderModel = new FolderModel(
                                null,
                                folderName,
                                System.currentTimeMillis(),
                                0
                        );

                        firebaseSyncManager.syncFolderToCloud(folderModel, new FirebaseSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Folder '" + folderName + "' created & synced!", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Folder '" + folderName + "' created locally.", Toast.LENGTH_SHORT).show();
                            }
                        });

                        renderDashboardData(cachedFiles);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void syncCloudStorage() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please sign in to sync.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvSyncStatus.setText("Syncing...");
        Toast.makeText(requireContext(), "Syncing cloud storage...", Toast.LENGTH_SHORT).show();
        firebaseSyncManager.fetchCloudFiles(new FirebaseSyncManager.SyncListCallback() {
            @Override
            public void onSuccess(List<FileModel> files) {
                if (!isAdded()) return;
                cachedFiles = files;
                renderDashboardData(files);
                Toast.makeText(requireContext(), "Synced " + files.size() + " documents from Cloud.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvSyncStatus.setText("Sync failed");
                Toast.makeText(requireContext(), "Sync failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleFileSelected(@Nullable Uri uri) {
        if (uri == null) return;

        String fileName = getFileNameFromUri(uri);
        long fileSize = getFileSizeFromUri(uri);

        Toast.makeText(requireContext(), "Saving " + fileName + " to '" + activeCategory + "'...", Toast.LENGTH_SHORT).show();

        File localFile = localFileManager.saveFileLocally(uri, fileName, activeCategory);
        if (fileSize <= 0 && localFile != null && localFile.exists()) {
            fileSize = localFile.length();
        }

        if (localFile == null || !localFile.exists()) {
            Toast.makeText(requireContext(), "Could not save the file locally. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        renderDashboardData(cachedFiles);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Saved locally in '" + activeCategory + "'. Sign in to upload to cloud.", Toast.LENGTH_SHORT).show();
            return;
        }

        final long finalFileSize = fileSize;
        fileUploadManager.uploadFile(uri, fileName, activeCategory, new FileUploadManager.UploadCallback() {
            @Override
            public void onProgress(double percent) {
                // Upload progress
            }

            @Override
            public void onSuccess(String downloadUrl) {
                if (!isAdded()) return;
                persistCloudMetadata(fileName, localFile, downloadUrl, finalFileSize, uri, true);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                persistCloudMetadata(fileName, localFile, "", finalFileSize, uri, false);
                Toast.makeText(requireContext(),
                        "Saved locally in '" + activeCategory + "'. Cloud storage upload failed.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void persistCloudMetadata(String fileName,
                                     File localFile,
                                     String downloadUrl,
                                     long finalFileSize,
                                     Uri sourceUri,
                                     boolean uploadSucceeded) {
        if (localFile == null || !localFile.exists()) {
            return;
        }

        FileModel fileModel = new FileModel();
        fileModel.setFileName(fileName);
        fileModel.setLocalPath(localFile.getAbsolutePath());
        fileModel.setDownloadUrl(downloadUrl != null ? downloadUrl : "");
        fileModel.setSizeBytes(finalFileSize);
        fileModel.setMimeType(requireContext().getContentResolver().getType(sourceUri));
        fileModel.setCategory(activeCategory);
        fileModel.setUpdatedAt(System.currentTimeMillis());

        firebaseSyncManager.syncMetadataToCloud(fileModel, new FirebaseSyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                String message = uploadSucceeded
                        ? "Document uploaded to '" + activeCategory + "' & synced successfully!"
                        : "File saved locally in '" + activeCategory + "'.";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "File saved locally. Sync will retry later.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        name = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying file name", e);
            }
        }
        if (name == null) {
            name = uri.getPath();
            if (name != null) {
                int cut = name.lastIndexOf('/');
                if (cut != -1) {
                    name = name.substring(cut + 1);
                }
            }
        }
        return name != null ? name : "file_" + System.currentTimeMillis();
    }

    private long getFileSizeFromUri(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx != -1) {
                        return cursor.getLong(idx);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying file size", e);
            }
        }
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtimeSync();

        imgHomeAvatar = null;
        tvName = null;
        tvSyncStatus = null;
        btnSyncNow = null;

        tvStorageRatio = null;
        progressStorage = null;
        tvStorageFree = null;

        cardUpload = null;
        cardNewFolder = null;

        tvNoRecentFiles = null;
        rvRecentFiles = null;
        tvNoCategories = null;
        rvCategories = null;
    }
}
