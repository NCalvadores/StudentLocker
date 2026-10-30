package com.ifelse101.studentlocker.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.ifelse101.studentlocker.model.FileModel;

import java.io.File;

public final class FileOpenHelper {

    private FileOpenHelper() {
    }

    public static void open(@NonNull Fragment fragment, @NonNull FileModel file) {
        Context context = fragment.requireContext();
        if (tryOpenLocalFile(context, file)) return;
        if (tryOpenRemoteFile(context, file)) return;

        Toast.makeText(context, "No file available to open.", Toast.LENGTH_SHORT).show();
    }

    private static boolean tryOpenLocalFile(@NonNull Context context, @NonNull FileModel file) {
        String localPath = file.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            return false;
        }

        File localFile = new File(localPath);
        if (!localFile.exists() || !localFile.isFile()) {
            return false;
        }

        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                localFile
        );

        String mimeType = resolveMimeType(file, localFile);
        return launchViewer(context, uri, mimeType);
    }

    private static boolean tryOpenRemoteFile(@NonNull Context context, @NonNull FileModel file) {
        String downloadUrl = file.getDownloadUrl();
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            return false;
        }

        Uri uri = Uri.parse(downloadUrl);
        String mimeType = resolveMimeType(file, null);
        return launchViewer(context, uri, mimeType);
    }

    private static boolean launchViewer(@NonNull Context context, @NonNull Uri uri, @NonNull String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(intent, "Open file");
        try {
            context.startActivity(chooser);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static String resolveMimeType(@NonNull FileModel file, File localFile) {
        String explicitMime = file.getMimeType();
        if (explicitMime != null && !explicitMime.trim().isEmpty()) {
            return explicitMime;
        }

        String fileName = file.getFileName();
        if (fileName != null && !fileName.trim().isEmpty()) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (!extension.isEmpty()) {
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (type != null) {
                    return type;
                }
            }
        }

        if (localFile != null) {
            String extension = localFile.getName();
            int dotIndex = extension.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < extension.length() - 1) {
                String subtype = extension.substring(dotIndex + 1).toLowerCase();
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(subtype);
                if (type != null) {
                    return type;
                }
            }
        }

        return "*/*";
    }
}
