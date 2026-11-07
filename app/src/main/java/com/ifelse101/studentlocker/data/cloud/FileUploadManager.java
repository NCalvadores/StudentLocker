package com.ifelse101.studentlocker.data.cloud;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.ifelse101.studentlocker.model.FileModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles uploading files directly to Cloudflare R2 (S3-compatible object storage)
 * organized into user folders and custom category subfolders under 'users/{uid}/{category}/'.
 */
public class FileUploadManager {

    private static final String TAG = "FileUploadManager";

    // Cloudflare R2 Credentials & Configuration
    // Replace placeholders with your actual Cloudflare R2 details
    public static String R2_ACCOUNT_ID = "YOUR_CLOUDFLARE_ACCOUNT_ID";
    public static String R2_ACCESS_KEY_ID = "YOUR_R2_ACCESS_KEY_ID";
    public static String R2_SECRET_ACCESS_KEY = "YOUR_R2_SECRET_ACCESS_KEY";
    public static String R2_BUCKET_NAME = "student-locker";

    /**
     * Optional public domain for direct file access (e.g., "https://pub-xxx.r2.dev" or "https://storage.yourdomain.com").
     * If left blank or null, S3 presigned URLs valid for 7 days will be generated automatically.
     */
    public static String R2_PUBLIC_DOMAIN = "";

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String NOT_SIGNED_IN_MESSAGE =
            "User must be signed in to upload files.";

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
     * Uploads a file to the default category in Cloudflare R2 storage.
     */
    public void uploadFile(@NonNull Uri fileUri, @NonNull String fileName, @NonNull UploadCallback callback) {
        uploadFile(fileUri, fileName, FileModel.DEFAULT_CATEGORY, callback);
    }

    /**
     * Uploads a file to a custom category folder on Cloudflare R2.
     */
    public void uploadFile(@NonNull Uri fileUri, @NonNull String fileName,
                           @Nullable String category, @NonNull UploadCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError(new IllegalStateException(NOT_SIGNED_IN_MESSAGE));
            return;
        }

        String userId = currentUser.getUid();
        String targetCategory = normalizeCategory(category);
        executor.execute(() -> {
            try {
                String downloadUrl = runR2Upload(fileUri, fileName, targetCategory, userId);
                mainHandler.post(() -> callback.onSuccess(downloadUrl));
            } catch (Exception e) {
                Log.e(TAG, "Error uploading file to Cloudflare R2 bucket", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private String runR2Upload(Uri fileUri, String fileName, String category, String userId) throws Exception {
        if ("YOUR_CLOUDFLARE_ACCOUNT_ID".equals(R2_ACCOUNT_ID) || "YOUR_R2_ACCESS_KEY_ID".equals(R2_ACCESS_KEY_ID)) {
            Log.w(TAG, "Cloudflare R2 credentials are set to placeholder values. Please configure R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, and R2_BUCKET_NAME.");
        }

        AWSCredentials credentials = new BasicAWSCredentials(R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY);
        AmazonS3Client s3Client = new AmazonS3Client(credentials);

        String endpoint = "https://" + R2_ACCOUNT_ID + ".r2.cloudflarestorage.com";
        s3Client.setEndpoint(endpoint);

        // Path structure: StudentLocker/<user_id>/<category>/<timestamp>_<fileName>
        String s3Key = "StudentLocker/" + userId + "/" + category + "/" + System.currentTimeMillis() + "_" + fileName;

        File tempFile = createTempFileFromUri(fileUri, fileName);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolveMimeType(fileUri));
            metadata.setContentLength(tempFile.length());

            PutObjectRequest putRequest = new PutObjectRequest(R2_BUCKET_NAME, s3Key, tempFile);
            putRequest.setMetadata(metadata);

            s3Client.putObject(putRequest);

            return resolveR2DownloadUrl(s3Client, s3Key);
        } finally {
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Failed to delete temp file: " + tempFile.getAbsolutePath());
                }
            }
        }
    }

    private String resolveR2DownloadUrl(AmazonS3Client s3Client, String s3Key) {
        if (R2_PUBLIC_DOMAIN != null && !R2_PUBLIC_DOMAIN.trim().isEmpty()) {
            String base = R2_PUBLIC_DOMAIN.trim();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "/" + s3Key;
        }

        Date expiration = new Date();
        long expTimeMillis = expiration.getTime() + (7L * 24 * 60 * 60 * 1000); // Presigned URL valid for 7 days
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(R2_BUCKET_NAME, s3Key)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(expiration);

        return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }

    private File createTempFileFromUri(Uri uri, String fileName) throws Exception {
        File tempFile = File.createTempFile("r2_upload_", "_" + fileName, context.getCacheDir());
        try (InputStream inputStream = openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
        return tempFile;
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
}
