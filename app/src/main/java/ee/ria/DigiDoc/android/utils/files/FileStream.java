package ee.ria.DigiDoc.android.utils.files;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.google.auto.value.AutoValue;
import com.google.common.io.ByteSource;

import java.io.File;

import ee.ria.DigiDoc.common.FileUtil;

import static com.google.common.io.Files.asByteSource;

@AutoValue
public abstract class FileStream {

    public abstract String displayName();

    public abstract ByteSource source();

    /**
     * Create FileStream from {@link ContentResolver} and content {@link Uri}.
     */
    public static FileStream create(ContentResolver contentResolver, Uri uri) {
        String displayName = uri.getLastPathSegment() == null ? "newFile" : FileUtil.sanitizeString(uri.getLastPathSegment(), '_');
        String sanitizedUri = FileUtil.sanitizeString(uri.toString(), '_');
        Cursor cursor = contentResolver.query(Uri.parse(sanitizedUri), new String[]{OpenableColumns.DISPLAY_NAME}, null,
                null, null);
        if (cursor != null) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                displayName = cursor.getString(0);
            }
            cursor.close();
        }

        return new AutoValue_FileStream(displayName,
                new ContentResolverUriSource(contentResolver, Uri.parse(sanitizedUri)));
    }

    /**
     * Create FileStream from {@link File}.
     */
    public static FileStream create(File file) {
        return new AutoValue_FileStream(file.getName(), asByteSource(file));
    }
}
