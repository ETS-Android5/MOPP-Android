package ee.ria.DigiDoc.android.utils;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.common.collect.ImmutableList;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.utils.files.FileStream;
import ee.ria.DigiDoc.common.FileUtil;
import ee.ria.DigiDoc.sign.DataFile;
import ee.ria.DigiDoc.sign.SignedContainer;

public final class IntentUtils {

    /**
     * Create an intent to choose multiple files of any type.
     *
     * @return Intent to use with {@link android.app.Activity#startActivityForResult(Intent, int)}.
     */
    public static Intent createGetContentIntent() {
        return Intent
                .createChooser(new Intent(Intent.ACTION_GET_CONTENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_REFERRER, R.string.application_name)
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), null);
    }

    /**
     * Parse intent returned from {@link #createGetContentIntent() get content intent} to
     * {@link FileStream} objects.
     *
     * Always returns a list, even if only one file was chosen.
     *
     * @param contentResolver Content resolver to get display name, type and input stream.
     * @param intent Intent returned from
     *               {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     * @return List of {@link FileStream file stream} objects.
     */
    public static ImmutableList<FileStream> parseGetContentIntent(ContentResolver contentResolver,
                                                                  Intent intent) {
        ImmutableList.Builder<FileStream> builder = ImmutableList.builder();

        ClipData clipData = intent.getClipData();
        Uri data = intent.getData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                builder.add(FileStream.create(contentResolver, uri, getFileSize(contentResolver,
                        Uri.parse(FileUtil.sanitizeString(uri.toString(), "")))));
            }
        } else if (data != null) {
            builder.add(FileStream.create(contentResolver, data, getFileSize(contentResolver,
                    Uri.parse(FileUtil.sanitizeString(data.toString(), "")))));
        }

        return builder.build();
    }

    /**
     * Create an intent to send local file to other apps.
     *
     * File path has to be shared with {@link FileProvider}.
     *
     * @param context Context to use for {@link FileProvider#getUriForFile(Context, String, File)}
     *                and to get authority string.
     * @param file File to send.
     * @param type Optional type for the content.
     * @return {@link Intent#ACTION_VIEW View intent} with content Uri of the file.
     */
    public static Intent createViewIntent(Context context, File file, @Nullable String type) {
        Uri uri = FileProvider.getUriForFile(context,
                context.getString(R.string.file_provider_authority), file);
        return Intent
                .createChooser(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null);
    }

    public static Intent createSendIntent(Context context, File file) {
        Uri uri = FileProvider.getUriForFile(context,
                context.getString(R.string.file_provider_authority), file);
        return Intent
                .createChooser(new Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .setType(SignedContainer.mimeType(file)), null);
    }

    public static Intent createSaveIntent(DataFile dataFile) {
        return Intent
                .createChooser(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, FileUtil.sanitizeString(dataFile.name(), ""))
                        .setType(dataFile.mimeType())
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION), null);
    }

    public static Intent createSaveIntent(File file, ContentResolver contentResolver) {
        int extensionIndex = file.getName().lastIndexOf(".");
        String extension = extensionIndex != -1 ? file.getName().substring(extensionIndex + 1) : "";
        String mimeType = !extension.isEmpty() ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : "application/octet-stream";
        return Intent
                .createChooser(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, FileUtil.sanitizeString(file.getName(), ""))
                        .setType(mimeType)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION), null);
    }

    public static Intent createBrowserIntent(Context context, int stringRes){
        String url = context.getResources().getString(stringRes);
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    }

    private static long getFileSize(ContentResolver contentResolver, Uri uri) {
        Cursor cursor = contentResolver.
                query(Uri.parse(FileUtil.sanitizeString(uri.toString(), "")),
                        null, null, null, null);
        long fileSize = 0;
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (cursor.moveToFirst() && !cursor.isNull(columnIndex)) {
                fileSize = cursor.getLong(columnIndex);
            }
            cursor.close();
            return fileSize;
        }
        return fileSize;
    }

    private IntentUtils() {}
}
