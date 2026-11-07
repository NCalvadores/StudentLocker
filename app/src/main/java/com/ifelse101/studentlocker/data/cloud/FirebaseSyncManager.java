package com.ifelse101.studentlocker.data.cloud;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.ifelse101.studentlocker.model.FileModel;
import com.ifelse101.studentlocker.model.FolderModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles syncing file metadata and folders between offline storage and Firebase Firestore.
 */
public class FirebaseSyncManager {

    private static final String TAG = "FirebaseSyncManager";

    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_FILES = "files";
    private static final String COLLECTION_FOLDERS = "folders";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String NOT_LOGGED_IN_MESSAGE =
            "User must be logged in to access cloud database sync.";

    private final FirebaseFirestore db;

    public interface ErrorCallback {
        void onError(Exception e);
    }

    public interface SyncCallback extends ErrorCallback {
        void onSuccess();
    }

    public interface SyncListCallback extends ErrorCallback {
        void onSuccess(List<FileModel> files);
    }

    public interface FolderListCallback extends ErrorCallback {
        void onSuccess(List<FolderModel> folders);
    }

    public FirebaseSyncManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    // ------------------------------------------------------------------
    // Files API
    // ------------------------------------------------------------------

    /**
     * Saves or updates a file metadata record in Firestore.
     */
    public void syncMetadataToCloud(@NonNull FileModel fileModel, @NonNull SyncCallback callback) {
        if (!hasValidFileSource(fileModel)) {
            Exception invalidState = new IllegalStateException(
                    "Cannot sync file metadata without a real local file or download URL.");
            reportFailure("Blocked invalid metadata sync", invalidState, callback);
            return;
        }

        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FILES, callback);
        if (collection == null) return;

        String documentId = resolveFileDocumentId(collection, fileModel);
        fileModel.setFileId(documentId);
        fileModel.setSynced(true);

        collection.document(documentId)
                .set(fileModel)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e ->
                        reportFailure("Failed to sync file metadata to Firestore", e, callback));
    }

    private boolean hasValidFileSource(@NonNull FileModel fileModel) {
        String fileName = fileModel.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        String localPath = fileModel.getLocalPath();
        if (localPath != null && !localPath.trim().isEmpty()) {
            java.io.File localFile = new java.io.File(localPath);
            if (localFile.exists() && localFile.isFile()) {
                return true;
            }
        }

        String downloadUrl = fileModel.getDownloadUrl();
        return downloadUrl != null && !downloadUrl.trim().isEmpty();
    }

    /**
     * Fetches all synced file records from Firestore for the active user, newest first.
     */
    public void fetchCloudFiles(@NonNull SyncListCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FILES, callback);
        if (collection == null) return;

        collection.orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(toFileModels(snapshot)))
                .addOnFailureListener(e ->
                        reportFailure("Failed to fetch files from Firestore", e, callback));
    }

    /**
     * Listens in real time to file changes in Firestore, newest first.
     */
    @Nullable
    public ListenerRegistration listenToCloudFiles(@NonNull SyncListCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FILES, callback);
        if (collection == null) return null;

        return collection.orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        reportFailure("Realtime sync listener error", error, callback);
                        return;
                    }
                    if (snapshot == null) return;

                    callback.onSuccess(toFileModels(snapshot));
                });
    }

    /**
     * Deletes a file record from Firestore by file ID.
     */
    public void deleteCloudMetadata(@NonNull String fileId, @NonNull SyncCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FILES, callback);
        if (collection == null) return;

        collection.document(fileId)
                .delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e ->
                        reportFailure("Failed to delete file metadata from Firestore", e, callback));
    }

    // ------------------------------------------------------------------
    // Folders API
    // ------------------------------------------------------------------

    /**
     * Saves or updates a folder record in Firestore.
     */
    public void syncFolderToCloud(@NonNull FolderModel folderModel, @NonNull SyncCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FOLDERS, callback);
        if (collection == null) return;

        String documentId = resolveFolderDocumentId(collection, folderModel);
        folderModel.setFolderId(documentId);

        collection.document(documentId)
                .set(folderModel)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e ->
                        reportFailure("Failed to sync folder to Firestore", e, callback));
    }

    /**
     * Fetches all folders from Firestore for the active user.
     */
    public void fetchFolders(@NonNull FolderListCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FOLDERS, callback);
        if (collection == null) return;

        collection.orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(toFolderModels(snapshot)))
                .addOnFailureListener(e ->
                        reportFailure("Failed to fetch folders from Firestore", e, callback));
    }

    /**
     * Listens in real time to folder changes in Firestore.
     */
    @Nullable
    public ListenerRegistration listenToFolders(@NonNull FolderListCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FOLDERS, callback);
        if (collection == null) return null;

        return collection.orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        reportFailure("Realtime folder listener error", error, callback);
                        return;
                    }
                    if (snapshot == null) return;

                    callback.onSuccess(toFolderModels(snapshot));
                });
    }

    /**
     * Deletes a folder record from Firestore by folder ID.
     */
    public void deleteFolder(@NonNull String folderId, @NonNull SyncCallback callback) {
        CollectionReference collection = getUserCollectionOrReportError(COLLECTION_FOLDERS, callback);
        if (collection == null) return;

        collection.document(folderId)
                .delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e ->
                        reportFailure("Failed to delete folder from Firestore", e, callback));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Nullable
    private CollectionReference getUserCollectionOrReportError(String subCollection, @NonNull ErrorCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError(new IllegalStateException(NOT_LOGGED_IN_MESSAGE));
            return null;
        }
        return db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .collection(subCollection);
    }

    private String resolveFileDocumentId(CollectionReference collection, FileModel fileModel) {
        String existingId = fileModel.getFileId();
        boolean hasId = existingId != null && !existingId.isEmpty();
        return hasId ? existingId : collection.document().getId(); //same logic as bottom one
    }

    private String resolveFolderDocumentId(CollectionReference collection, FolderModel folderModel) {
        String existingId = folderModel.getFolderId();
        boolean hasId = existingId != null && !existingId.isEmpty(); //checks if file was uploaded before (true)
        return hasId ? existingId : collection.document().getId(); //if true it reuses id, if false it creates a new one
    }

    private List<FileModel> toFileModels(QuerySnapshot snapshot) {
        List<FileModel> files = new ArrayList<>();
        for (DocumentSnapshot document : snapshot) {
            FileModel file = document.toObject(FileModel.class);
            if (file != null && hasValidFileSource(file)) { //same logic as other one
                files.add(file);
            }
        }
        return files;
    }

    private List<FolderModel> toFolderModels(QuerySnapshot snapshot) {
        List<FolderModel> folders = new ArrayList<>();
        for (DocumentSnapshot document : snapshot) {
            FolderModel folder = document.toObject(FolderModel.class); //same logic as other one
            if (folder != null) {
                folders.add(folder);
            }
        }
        return folders;
    }

    private void reportFailure(String logMessage, Exception error, ErrorCallback callback) {
        Log.e(TAG, logMessage, error);
        callback.onError(error);
    }
}
