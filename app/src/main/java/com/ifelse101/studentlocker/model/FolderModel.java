package com.ifelse101.studentlocker.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Metadata for a folder stored locally and synced to Firestore.
 */
public class FolderModel {

    private String folderId;
    private String name;
    private long createdAt;
    private int documentCount;

    // Required empty constructor for Firestore
    public FolderModel() {
    }

    public FolderModel(String folderId, String name, long createdAt, int documentCount) {
        this.folderId = folderId;
        this.name = normalizeName(name);
        this.createdAt = createdAt;
        this.documentCount = documentCount;
    }

    public static String normalizeName(@Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = normalizeName(name);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderModel that = (FolderModel) o;
        return createdAt == that.createdAt
                && documentCount == that.documentCount
                && Objects.equals(folderId, that.folderId)
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId, name, createdAt, documentCount);
    }
}
