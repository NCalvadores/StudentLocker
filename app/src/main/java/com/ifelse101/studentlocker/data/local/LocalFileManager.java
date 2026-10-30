package com.ifelse101.studentlocker.data.local;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ifelse101.studentlocker.model.FileModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles offline / local storage management for student locker files and folders.
 */
public class LocalFileManager {

    private static final String TAG = "LocalFileManager";
    private static final String LOCKER_DIR_NAME = "locker_files";
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String FALLBACK_FILE_NAME_PREFIX = "downloaded_file_";

    private final Context context;

    public LocalFileManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Gets the internal directory where locker files are stored offline,
     * creating it if needed.
     */
    @NonNull
    public File getLockerDir() {
        File lockerDir = new File(context.getFilesDir(), LOCKER_DIR_NAME);
        ensureDirectoryExists(lockerDir, "local locker directory");
        return lockerDir;
    }

    /**
     * Creates a local folder directory for offline files.
     */
    @NonNull
    public File createLocalFolder(@NonNull String folderName) {
        String sanitizedName = normalizeFolderName(folderName);
        if (sanitizedName.isEmpty()) {
            return getLockerDir();
        }

        File folderDir = new File(getLockerDir(), sanitizedName);
        ensureDirectoryExists(folderDir, "local folder directory: " + sanitizedName);
        return folderDir;
    }

    /**
     * Returns all local folder names created offline.
     */
    @NonNull
    public List<String> getLocalFolderNames() {
        File[] subDirs = getLockerDir().listFiles(File::isDirectory);
        if (subDirs == null) return new ArrayList<>();

        List<String> names = new ArrayList<>();
        for (File dir : subDirs) {
            names.add(dir.getName());
        }
        return names;
    }

    /**
     * Copies a file from a content Uri into local internal app storage for offline access.
     */
    @Nullable
    public File saveFileLocally(@NonNull Uri sourceUri, @NonNull String fileName) {
        return saveFileLocally(sourceUri, fileName, null);
    }

    /**
     * Copies a file from a content Uri into a specific local folder in internal app storage.
     */
    @Nullable
    public File saveFileLocally(@NonNull Uri sourceUri, @NonNull String fileName, @Nullable String folderName) {
        String targetName = normalizeFileName(fileName);
        File parentDir = (folderName != null && !folderName.trim().isEmpty())
                ? createLocalFolder(folderName)
                : getLockerDir();

        File targetFile = new File(parentDir, targetName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) return null;

            copyToFile(inputStream, targetFile);
            return targetFile;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file locally: " + targetName, e);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            return null;
        }
    }

    /**
     * Returns a list of all files saved locally in offline storage.
     */
    @NonNull
    public List<File> getLocalFiles() {
        List<File> result = new ArrayList<>();
        collectFilesRecursively(getLockerDir(), result);
        return result;
    }

    private void collectFilesRecursively(File directory, List<File> resultList) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                resultList.add(file);
            } else if (file.isDirectory()) {
                collectFilesRecursively(file, resultList);
            }
        }
    }

    /**
     * Deletes a local offline file by name.
     */
    public boolean deleteLocalFile(@NonNull String fileName) {
        File target = new File(getLockerDir(), fileName);
        if (target.exists()) return target.delete();

        for (File file : getLocalFiles()) {
            if (file.getName().equals(fileName)) {
                return file.delete();
            }
        }
        return false;
    }

    /**
     * Deletes a local folder and all files inside it.
     */
    public boolean deleteLocalFolder(@NonNull String folderName) {
        File folder = new File(getLockerDir(), folderName);
        if (!folder.exists()) return false;
        deleteContentsRecursively(folder);
        return folder.delete();
    }

    /**
     * Checks if a specific file exists locally for offline usage.
     */
    public boolean isFileAvailableOffline(@NonNull String fileName) {
        File target = new File(getLockerDir(), fileName);
        if (target.exists() && target.length() > 0) return true;

        for (File file : getLocalFiles()) {
            if (file.getName().equals(fileName) && file.length() > 0) {
                return true;
            }
        }
        return false;
    }

    private void ensureDirectoryExists(File directory, String description) {
        if (directory.isDirectory()) return;
        boolean created = directory.mkdirs();
        if (!created) {
            Log.w(TAG, "Failed to create " + description + ": " + directory.getAbsolutePath());
        }
    }

    private void deleteContentsRecursively(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                deleteContentsRecursively(child);
            }
            child.delete();
        }
    }

    private String normalizeFolderName(@NonNull String folderName) {
        String normalized = folderName == null ? "" : folderName.trim();
        return normalized == null ? "" : normalized;
    }

    private String normalizeFileName(@NonNull String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isEmpty()) {
            return FALLBACK_FILE_NAME_PREFIX + System.currentTimeMillis();
        }
        return normalized;
    }

    private void copyToFile(InputStream source, File target) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
        }
    }
}
