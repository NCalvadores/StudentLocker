package com.ifelse101.studentlocker.model;

import androidx.annotation.Nullable;

/**
 * Metadata for a file stored locally and synced to Firestore.
 * Firestore maps fields through the public getters/setters, so keep them in sync with the cloud keys.
 */
public class FileModel {

    public static final String DEFAULT_CATEGORY = "General";

    private String fileId;
    private String fileName;
    private String localPath;
    private String downloadUrl;
    private long sizeBytes;
    private String mimeType;
    private long updatedAt;
    private boolean synced;
    private String category = DEFAULT_CATEGORY;

    // Required empty constructor for Firestore
    public FileModel() {
    }

    public FileModel(String fileId, String fileName, String localPath, String downloadUrl,
                     long sizeBytes, String mimeType, long updatedAt, boolean synced, String category) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.localPath = localPath;
        this.downloadUrl = downloadUrl;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.updatedAt = updatedAt;
        this.synced = synced;
        this.category = normalizeCategory(category);
    }

    public static String normalizeCategory(@Nullable String category) {
        if (category == null || category.trim().isEmpty()) {
            return DEFAULT_CATEGORY;
        }
        return category.trim();
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = normalizeCategory(category); }
}