package ee.ria.DigiDoc.common;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class FileUtil {

    public static final String RESTRICTED_FILENAME_CHARACTERS_AS_STRING = "@%:^?[]\\'\"”’{}#&`\\\\~«»/´";
    public static final String RTL_CHARACTERS_AS_STRING = "" + '\u200E' + '\u200F' + '\u202E' + '\u202A' + '\u202B';
    public static final String RESTRICTED_FILENAME_CHARACTERS_AND_RTL_CHARACTERS_AS_STRING = RESTRICTED_FILENAME_CHARACTERS_AS_STRING + RTL_CHARACTERS_AS_STRING;
    private static final String ALLOWED_URL_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_,.:/%;+=@?&!()";

    /**
     * Check if file path is in cache directory
     *
     * @param file File to check
     * @return Boolean indicating if file is in the cache directory.
     */
    public static File getFileInDirectory(File file, File directory) throws IOException {
        if (file.getCanonicalPath().startsWith(directory.getCanonicalPath())) {
            return file;
        }

        throw new IOException("Invalid file path");
    }

    /**
     * Replace invalid string characters
     *
     * @param input Input to replace invalid characters
     * @param replacement Replacement to replace invalid characters with
     * @return String with valid characters
     */
    public static String sanitizeString(String input, String replacement) {
        if (input == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(input.length());

        if (!URLUtil.isValidUrl(input) && !isRawUrl(input)) {
            for (int offset = 0; offset < input.length(); offset++) {
                char c = input.charAt(offset);

                if (RESTRICTED_FILENAME_CHARACTERS_AND_RTL_CHARACTERS_AS_STRING.indexOf(c) != -1) {
                    sb.append(replacement);
                } else {
                    sb.append(c);
                }
            }
        } else if (!isRawUrl(input)) {
            return normalizeUri(Uri.parse(input)).toString();
        }

        return !sb.toString().equals("") ?
                FilenameUtils.getName(FilenameUtils.normalize(sb.toString())) :
                FilenameUtils.normalize(input);
    }

    public static Uri normalizeUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        String uriString = uri.toString();

        StringBuilder sb = new StringBuilder(uriString.length());

        for (int offset = 0; offset < uriString.length(); offset++) {
            int i = ALLOWED_URL_CHARACTERS.indexOf(uriString.charAt(offset));

            if (i == -1) {
                sb.append("");
            }
            else {
                // Coverity does not want to see usages of the original string
                sb.append(ALLOWED_URL_CHARACTERS.charAt(i));
            }
        }

        return Uri.parse(sb.toString());
    }

    public static Uri normalizePath(String filePath) {
        return Uri.parse(FilenameUtils.normalize(filePath));
    }

    public static boolean logsExist(File logsDirectory) {
        if (logsDirectory.exists()) {
            File[] files = logsDirectory.listFiles();
            return files != null && files.length > 0;
        }
        return false;
    }

    public static File combineLogFiles(File logsDirectory, String diagnosticsLogsFileName) throws IOException {
        if (logsExist(logsDirectory)) {
            File[] files = logsDirectory.listFiles() != null ? logsDirectory.listFiles() : new File[]{};
            File combinedLogFile = new File(logsDirectory + File.separator + diagnosticsLogsFileName);
            if (combinedLogFile.exists()) {
                Files.delete(combinedLogFile.toPath());
            }
            for (File file : files) {
                String header = "\n\n" + "===== File: " + file.getName() + " =====" + "\n\n";
                String fileString = header + FileUtils.readFileToString(file, Charset.defaultCharset());
                FileUtils.write(combinedLogFile, fileString, Charset.defaultCharset(), true);
            }
            return combinedLogFile;
        }
        throw new FileNotFoundException("Could not combine log files. Cannot find logs.");
    }

    public static File getLogsDirectory(Context context) {
        return new File(context.getFilesDir() + "/logs");
    }

    private static boolean isRawUrl(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }

        return url.startsWith("raw:");
    }
}
