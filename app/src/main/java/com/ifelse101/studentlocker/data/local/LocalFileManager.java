package com.ifelse101.studentlocker.data.local;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

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

    @NonNull
    public File getLockerDir() {
        File lockerDir = new File(context.getFilesDir(), LOCKER_DIR_NAME);
        ensureDirectoryExists(lockerDir, "local locker directory");
        return lockerDir;
    }

    @NonNull
    public File createLocalFolder(@NonNull String folderName) {
        String sanitizedName = sanitizeName(folderName);
        if (sanitizedName.isEmpty()) {
            return getLockerDir();
        }

        File folderDir = new File(getLockerDir(), sanitizedName);
        ensureDirectoryExists(folderDir, "local folder directory: " + sanitizedName);
        return folderDir;
    }

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

    @Nullable
    public File saveFileLocally(@NonNull Uri sourceUri, @NonNull String fileName) {
        return saveFileLocally(sourceUri, fileName, null);
    }

    @Nullable
    public File saveFileLocally(@NonNull Uri sourceUri, @NonNull String fileName, @Nullable String folderName) {
        String sanitizedFileName = sanitizeName(fileName);
        if (sanitizedFileName.isEmpty()) {
            sanitizedFileName = FALLBACK_FILE_NAME_PREFIX + System.currentTimeMillis();
        }

        File parentDir = (folderName != null && !folderName.trim().isEmpty())
                ? createLocalFolder(folderName)
                : getLockerDir();

        File targetFile = getUniqueTargetFile(parentDir, sanitizedFileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) return null;

            copyToFile(inputStream, targetFile);
            return targetFile;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file locally: " + sanitizedFileName, e);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            return null;
        }
    }

    @NonNull
    public List<File> getLocalFiles() {
        List<File> result = new ArrayList<>();
        collectFilesRecursively(getLockerDir(), result);
        return result;
    }

    /**
     * Converts all local files on disk into FileModel objects for UI display.
     */
    @NonNull
    public List<FileModel> getLocalFileModels() {
        List<FileModel> models = new ArrayList<>();
        File lockerDir = getLockerDir();
        collectFileModelsRecursively(lockerDir, lockerDir, models);
        return models;
    }

    private void collectFileModelsRecursively(File baseDir, File currentDir, List<FileModel> models) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                models.add(toFileModel(baseDir, file));
            } else if (file.isDirectory()) {
                collectFileModelsRecursively(baseDir, file, models);
            }
        }
    }

    @NonNull
    public FileModel toFileModel(File baseDir, File file) {
        String category = FileModel.DEFAULT_CATEGORY;
        File parent = file.getParentFile();
        if (parent != null && !parent.equals(baseDir)) {
            category = parent.getName();
        }

        String mimeType = resolveMimeType(file.getName());

        FileModel model = new FileModel();
        model.setFileId("local_" + Math.abs(file.getAbsolutePath().hashCode()));
        model.setFileName(file.getName());
        model.setLocalPath(file.getAbsolutePath());
        model.setDownloadUrl("");
        model.setSizeBytes(file.length());
        model.setMimeType(mimeType);
        model.setUpdatedAt(file.lastModified());
        model.setSynced(false);
        model.setCategory(category);
        return model;
    }

    private String resolveMimeType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            String ext = fileName.substring(dotIndex + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
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
     * Deletes a local file by exact absolute path.
     */
    public boolean deleteLocalFileByPath(@NonNull String localPath) {
        if (localPath.trim().isEmpty()) return false;
        File file = new File(localPath);
        return file.exists() && file.delete();
    }

    /**
     * Deletes a local offline file by name within a specific folder/category.
     */
    public boolean deleteLocalFile(@NonNull String fileName, @Nullable String category) {
        File parentDir = (category != null && !category.trim().isEmpty())
                ? new File(getLockerDir(), sanitizeName(category))
                : getLockerDir();

        File target = new File(parentDir, sanitizeName(fileName));
        if (target.exists()) return target.delete();

        return false;
    }

    public boolean deleteLocalFolder(@NonNull String folderName) {
        String sanitizedFolder = sanitizeName(folderName);
        File folder = new File(getLockerDir(), sanitizedFolder);
        if (!folder.exists()) return false;
        deleteContentsRecursively(folder);
        return folder.delete();
    }

    public boolean isFileAvailableOffline(@NonNull String localPath) {
        if (localPath.trim().isEmpty()) return false;
        File file = new File(localPath);
        return file.exists() && file.length() > 0;
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

    /**
     * Sanitizes input strings against path traversal vulnerabilities.
     */
    private String sanitizeName(@NonNull String input) {
        if (input == null) return "";
        String clean = input.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(clean).getName();
    }

    /**
     * Prevents overwriting files with identical names by appending numbers.
     */
    private File getUniqueTargetFile(File parentDir, String fileName) {
        File file = new File(parentDir, fileName);
        if (!file.exists()) return file;

        String nameWithoutExt = fileName;
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(parentDir, nameWithoutExt + " (" + count + ")" + ext);
            count++;
        }
        return file;
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
