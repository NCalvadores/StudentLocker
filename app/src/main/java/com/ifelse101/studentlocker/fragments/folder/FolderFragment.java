package com.ifelse101.studentlocker.fragments.folder;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import com.ifelse101.studentlocker.R;
import com.ifelse101.studentlocker.data.cloud.FirebaseSyncManager;
import com.ifelse101.studentlocker.data.local.LocalFileManager;
import com.ifelse101.studentlocker.model.FileModel;
import com.ifelse101.studentlocker.model.FolderModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FolderFragment extends Fragment {

    private static final String TAG = "FolderFragment";
    private static final String DEFAULT_FOLDER_MESSAGE = "No folders created yet.\nTap + to create a new folder.";

    private RecyclerView recyclerViewFolders;
    private TextView tvEmptyState;
    private FloatingActionButton fabCreateFolder;

    private FolderAdapter folderAdapter;
    private LocalFileManager localFileManager;
    private FirebaseSyncManager firebaseSyncManager;

    private ListenerRegistration folderListener;
    private ListenerRegistration fileListener;

    private final List<FolderModel> currentFolders = new ArrayList<>();
    private final List<FileModel> currentFiles = new ArrayList<>();

    public FolderFragment() {
        super(R.layout.fragment_folder);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        initManagers();
        setupRecyclerView();
        setupCreateFolderButton();
        startRealtimeSync();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtimeSync();
        recyclerViewFolders = null;
        tvEmptyState = null;
        fabCreateFolder = null;
    }

    private void bindViews(@NonNull View view) {
        recyclerViewFolders = view.findViewById(R.id.recyclerViewFolders);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        fabCreateFolder = view.findViewById(R.id.fabCreateFolder);
    }

    private void initManagers() {
        localFileManager = new LocalFileManager(requireContext());
        firebaseSyncManager = new FirebaseSyncManager();
    }

    private void setupRecyclerView() {
        folderAdapter = new FolderAdapter();
        recyclerViewFolders.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewFolders.setAdapter(folderAdapter);

        folderAdapter.setOnFolderClickListener(this::showFolderFilesDialog);
        folderAdapter.setOnFolderDeleteListener(this::confirmDeleteFolder);
    }

    private void setupCreateFolderButton() {
        fabCreateFolder.setOnClickListener(v -> showCreateFolderDialog());
    }

    private void startRealtimeSync() {
        renderFolders();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        folderListener = firebaseSyncManager.listenToFolders(new FirebaseSyncManager.FolderListCallback() {
            @Override
            public void onSuccess(List<FolderModel> folders) {
                if (!isAdded()) return;
                currentFolders.clear();
                currentFolders.addAll(folders);
                renderFolders();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(TAG, "Error listening to folders", e);
            }
        });

        fileListener = firebaseSyncManager.listenToCloudFiles(new FirebaseSyncManager.SyncListCallback() {
            @Override
            public void onSuccess(List<FileModel> files) {
                if (!isAdded()) return;
                currentFiles.clear();
                currentFiles.addAll(files);
                renderFolders();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(TAG, "Error listening to files", e);
            }
        });
    }

    private void stopRealtimeSync() {
        if (folderListener != null) {
            folderListener.remove();
            folderListener = null;
        }
        if (fileListener != null) {
            fileListener.remove();
            fileListener = null;
        }
    }

    private void renderFolders() {
        Map<String, Integer> fileCounts = countFilesByCategory();
        List<FolderModel> displayList = new ArrayList<>();

        // Default folder shown first
        displayList.add(createFolderModel(
                "default_general",
                FileModel.DEFAULT_CATEGORY,
                0,
                fileCounts.getOrDefault(FileModel.DEFAULT_CATEGORY, 0)
        ));

        for (FolderModel folder : currentFolders) {
            String folderName = FolderModel.normalizeName(folder.getName());
            if (folderName.isEmpty() || folderName.equalsIgnoreCase(FileModel.DEFAULT_CATEGORY)) {
                continue;
            }

            displayList.add(createFolderModel(
                    folder.getFolderId(),
                    folderName,
                    folder.getCreatedAt(),
                    fileCounts.getOrDefault(folderName, 0)
            ));
        }

        for (String localFolder : localFileManager.getLocalFolderNames()) {
            String normalizedLocalName = FolderModel.normalizeName(localFolder);
            if (normalizedLocalName.isEmpty()) {
                continue;
            }

            boolean alreadyIncluded = false;
            for (FolderModel existing : displayList) {
                if (existing.getName().equalsIgnoreCase(normalizedLocalName)) {
                    alreadyIncluded = true;
                    break;
                }
            }

            if (!alreadyIncluded) {
                displayList.add(createFolderModel(
                        null,
                        normalizedLocalName,
                        System.currentTimeMillis(),
                        fileCounts.getOrDefault(normalizedLocalName, 0)
                ));
            }
        }

        if (displayList.isEmpty()) {
            showEmptyState(DEFAULT_FOLDER_MESSAGE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        recyclerViewFolders.setVisibility(View.VISIBLE);
        folderAdapter.setFolders(displayList);
    }

    private Map<String, Integer> countFilesByCategory() {
        Map<String, Integer> fileCounts = new HashMap<>();
        List<FileModel> allFiles = getAllFilesMerged();
        for (FileModel file : allFiles) {
            String category = FileModel.DEFAULT_CATEGORY;
            if (file.getCategory() != null && !file.getCategory().trim().isEmpty()) {
                category = file.getCategory().trim();
            }
            fileCounts.put(category, fileCounts.getOrDefault(category, 0) + 1);
        }
        return fileCounts;
    }

    private List<FileModel> getAllFilesMerged() {
        List<FileModel> localFiles = localFileManager.getLocalFileModels();
        Map<String, FileModel> map = new LinkedHashMap<>();

        for (FileModel cloudFile : currentFiles) {
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

    private FolderModel createFolderModel(@Nullable String id, String name, long createdAt, int documentCount) {
        return new FolderModel(id, name, createdAt, documentCount);
    }

    private void showEmptyState(String message) {
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
        recyclerViewFolders.setVisibility(View.GONE);
    }

    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("e.g. Certificates, Receipts, Exams");
        input.setPadding(32, 16, 32, 16);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create New Folder")
                .setMessage("Enter folder name:")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String folderName = FolderModel.normalizeName(input.getText().toString());
                    if (!folderName.isEmpty()) {
                        createFolder(folderName);
                    } else {
                        Toast.makeText(requireContext(), "Folder name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createFolder(String folderName) {
        localFileManager.createLocalFolder(folderName);

        FolderModel folderModel = new FolderModel(null, folderName, System.currentTimeMillis(), 0);

        firebaseSyncManager.syncFolderToCloud(folderModel, new FirebaseSyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Folder '" + folderName + "' created successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Folder '" + folderName + "' created locally.", Toast.LENGTH_SHORT).show();
            }
        });

        renderFolders();
    }

    private void confirmDeleteFolder(FolderModel folder) {
        String folderName = FolderModel.normalizeName(folder.getName());
        if (FileModel.DEFAULT_CATEGORY.equalsIgnoreCase(folderName)) {
            Toast.makeText(requireContext(), "Cannot delete default General folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Folder")
                .setMessage("Are you sure you want to delete '" + folderName + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    localFileManager.deleteLocalFolder(folderName);
                    if (folder.getFolderId() != null) {
                        firebaseSyncManager.deleteFolder(folder.getFolderId(), new FirebaseSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Folder deleted.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Folder deleted locally.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    renderFolders();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFolderFilesDialog(FolderModel folder) {
        List<String> folderFileNames = new ArrayList<>();
        String targetCategory = FolderModel.normalizeName(folder.getName());

        for (FileModel file : getAllFilesMerged()) {
            String category = file.getCategory() != null ? file.getCategory().trim() : FileModel.DEFAULT_CATEGORY;
            if (targetCategory.equalsIgnoreCase(category)) {
                String fileName = file.getFileName();
                folderFileNames.add(fileName != null ? fileName : "Unnamed File");
            }
        }

        String[] fileArray = folderFileNames.isEmpty()
                ? new String[]{"No documents inside this folder yet."}
                : folderFileNames.toArray(new String[0]);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Folder: " + targetCategory)
                .setItems(fileArray, null)
                .setPositiveButton("OK", null)
                .show();
    }
}
