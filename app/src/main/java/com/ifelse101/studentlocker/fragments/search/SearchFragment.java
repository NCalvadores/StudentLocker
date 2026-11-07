package com.ifelse101.studentlocker.fragments.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import com.ifelse101.studentlocker.R;
import com.ifelse101.studentlocker.data.local.LocalFileManager;
import com.ifelse101.studentlocker.fragments.files.FileAdapter;
import com.ifelse101.studentlocker.model.FileModel;
import com.ifelse101.studentlocker.data.cloud.FirebaseSyncManager;
import com.ifelse101.studentlocker.util.FileOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchFragment extends Fragment {

    private TextInputEditText etSearchQuery;
    private RecyclerView rvSearchResults;
    private TextView tvEmptySearchResults;

    private FileAdapter fileAdapter;
    private LocalFileManager localFileManager;
    private FirebaseSyncManager firebaseSyncManager;
    private ListenerRegistration syncListener;

    private final List<FileModel> allFiles = new ArrayList<>();

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearchQuery = view.findViewById(R.id.etSearchQuery);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        tvEmptySearchResults = view.findViewById(R.id.tvEmptySearchResults);

        localFileManager = new LocalFileManager(requireContext());
        firebaseSyncManager = new FirebaseSyncManager();
        fileAdapter = new FileAdapter();
        fileAdapter.setOnFileClickListener(file -> FileOpenHelper.open(this, file));

        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSearchResults.setAdapter(fileAdapter);

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFiles(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        startRealtimeSync();
    }

    private void startRealtimeSync() {
        loadAndMergeFiles(null);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
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
                loadAndMergeFiles(null);
            }
        });
    }

    private void loadAndMergeFiles(@Nullable List<FileModel> cloudFiles) {
        List<FileModel> localFiles = localFileManager.getLocalFileModels();
        Map<String, FileModel> map = new LinkedHashMap<>();

        if (cloudFiles != null) {
            for (FileModel cloudFile : cloudFiles) {
                map.put(cloudFile.getFileName(), cloudFile);
            }
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

        allFiles.clear();
        allFiles.addAll(map.values());

        String currentQuery = etSearchQuery.getText() != null ? etSearchQuery.getText().toString() : "";
        filterFiles(currentQuery);
    }

    private void filterFiles(String query) {
        String trimmed = query.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            fileAdapter.setFiles(allFiles);
            if (allFiles.isEmpty()) {
                showEmptyState("No files found.");
            } else {
                tvEmptySearchResults.setVisibility(View.GONE);
                rvSearchResults.setVisibility(View.VISIBLE);
            }
            return;
        }

        List<FileModel> filtered = new ArrayList<>();
        for (FileModel file : allFiles) {
            String name = file.getFileName() != null ? file.getFileName().toLowerCase() : "";
            String category = file.getCategory() != null ? file.getCategory().toLowerCase() : "";
            if (name.contains(trimmed) || category.contains(trimmed)) {
                filtered.add(file);
            }
        }

        if (filtered.isEmpty()) {
            showEmptyState("No matching documents found for '" + query + "'");
        } else {
            tvEmptySearchResults.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
            fileAdapter.setFiles(filtered);
        }
    }

    private void showEmptyState(String message) {
        tvEmptySearchResults.setText(message);
        tvEmptySearchResults.setVisibility(View.VISIBLE);
        rvSearchResults.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (syncListener != null) {
            syncListener.remove();
            syncListener = null;
        }
        etSearchQuery = null;
        rvSearchResults = null;
        tvEmptySearchResults = null;
    }
}
