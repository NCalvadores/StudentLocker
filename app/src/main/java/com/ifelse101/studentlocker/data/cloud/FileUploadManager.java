package com.ifelse101.studentlocker.data.cloud;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.ifelse101.studentlocker.model.FileModel;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles uploading files directly to the user's personal Google Drive account
 * inside dedicated category subfolders under 'StudentLocker' ($0 developer cost / no billing needed).
 */
public class FileUploadManager {

    private static final String TAG = "FileUploadManager";

    private static final String APP_NAME = "StudentLocker";
    private static final String ROOT_FOLDER_NAME = "StudentLocker";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String DRIVE_SPACE = "drive";
    private static final String FIELDS_ID = "id";
    private static final String FIELDS_UPLOADED_FILE = "id, name, webViewLink, webContentLink";
    private static final String NOT_SIGNED_IN_MESSAGE =
            "User must be signed in with Google to upload files.";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onProgress(double percent);
        void onSuccess(String downloadUrl);
        void onError(Exception e);
    }

    public FileUploadManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Uploads a file to the default category in Google Drive storage.
     */
    public void uploadFile(@NonNull Uri fileUri, @NonNull String fileName, @NonNull UploadCallback callback) {
        uploadFile(fileUri, fileName, FileModel.DEFAULT_CATEGORY, callback);
    }

    /**
     * Uploads a file to a custom category subfolder on Google Drive.
     */
    public void uploadFile(@NonNull Uri fileUri, @NonNull String fileName,
                           @Nullable String category, @NonNull UploadCallback callback) {
        String accountEmail = getSignedInEmail();
        if (accountEmail == null) {
            callback.onError(new IllegalStateException(NOT_SIGNED_IN_MESSAGE));
            return;
        }

        String targetCategory = normalizeCategory(category);
        executor.execute(() -> runUpload(accountEmail, fileUri, fileName, targetCategory, callback));
    }

    // Upload flow (runs on the background executor)
    private void runUpload(String accountEmail, Uri fileUri, String fileName,
                           String category, UploadCallback callback) {
        try {
            Drive drive = buildDriveService(accountEmail);

            String rootFolderId = getOrCreateFolder(drive, ROOT_FOLDER_NAME, null);
            String categoryFolderId = getOrCreateFolder(drive, category, rootFolderId);

            String link = uploadToFolder(drive, fileUri, fileName, categoryFolderId);
            mainHandler.post(() -> callback.onSuccess(link));
        } catch (UserRecoverableAuthIOException e) {
            Log.e(TAG, "Google Drive OAuth permission required", e);
            Intent consentIntent = e.getIntent();
            if (consentIntent != null) {
                consentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(consentIntent);
                } catch (Exception startException) {
                    Log.e(TAG, "Unable to launch Drive permission activity", startException);
                }
            }
            mainHandler.post(() -> callback.onError(new Exception(
                    "Google Drive permission required. Please grant Drive access.", e)));
        } catch (Exception e) {
            Log.e(TAG, "Error uploading file to Google Drive subfolder", e);
            mainHandler.post(() -> callback.onError(e));
        }
    }

    private Drive buildDriveService(String accountEmail) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccountName(accountEmail);

        return new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                .setApplicationName(APP_NAME)
                .build();
    }

    private String uploadToFolder(Drive drive, Uri fileUri, String fileName, String folderId) throws Exception {
        File metadata = new File()
                .setName(fileName)
                .setParents(Collections.singletonList(folderId));

        try (InputStream inputStream = openInputStream(fileUri)) {
            InputStreamContent content = new InputStreamContent(resolveMimeType(fileUri), inputStream);

            File uploaded = drive.files().create(metadata, content)
                    .setFields(FIELDS_UPLOADED_FILE)
                    .execute();

            return resolveLink(uploaded);
        }
    }

    // Drive folders

    private String getOrCreateFolder(Drive drive, String name, @Nullable String parentId) throws Exception {
        String existingId = findFolderId(drive, name, parentId);
        return existingId != null ? existingId : createFolder(drive, name, parentId);
    }

    @Nullable
    private String findFolderId(Drive drive, String name, @Nullable String parentId) throws Exception {
        FileList result = drive.files().list()
                .setQ(buildFolderQuery(name, parentId))
                .setSpaces(DRIVE_SPACE)
                .execute();

        List<File> folders = result.getFiles();
        if (folders == null || folders.isEmpty()) return null;

        return folders.get(0).getId();
    }

    private String createFolder(Drive drive, String name, @Nullable String parentId) throws Exception {
        File metadata = new File()
                .setName(name)
                .setMimeType(FOLDER_MIME_TYPE);

        if (parentId != null) {
            metadata.setParents(Collections.singletonList(parentId));
        }

        return drive.files().create(metadata)
                .setFields(FIELDS_ID)
                .execute()
                .getId();
    }

    private String buildFolderQuery(String name, @Nullable String parentId) {
        String query = "mimeType='" + FOLDER_MIME_TYPE + "'"
                + " and name='" + escapeQueryValue(name) + "'"
                + " and trashed=false";

        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }
        return query;
    }

    private String escapeQueryValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Nullable
    private String getSignedInEmail() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        return currentUser != null ? currentUser.getEmail() : null;
    }

    private String normalizeCategory(@Nullable String category) {
        boolean isBlank = category == null || category.trim().isEmpty();
        return isBlank ? FileModel.DEFAULT_CATEGORY : category.trim();
    }

    private InputStream openInputStream(Uri fileUri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
        if (inputStream == null) {
            throw new IllegalArgumentException("Cannot open file stream for " + fileUri);
        }
        return inputStream;
    }

    private String resolveMimeType(Uri fileUri) {
        String mimeType = context.getContentResolver().getType(fileUri);
        boolean isMissing = mimeType == null || mimeType.isEmpty();
        return isMissing ? DEFAULT_MIME_TYPE : mimeType;
    }

    private String resolveLink(File uploadedFile) {
        if (uploadedFile.getWebViewLink() != null) return uploadedFile.getWebViewLink();
        if (uploadedFile.getWebContentLink() != null) return uploadedFile.getWebContentLink();
        return "https://drive.google.com/file/d/" + uploadedFile.getId() + "/view";
    }
}
