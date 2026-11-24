package it.giovaganzerli.planning;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants the installer read access to one fixed private APK, never to arbitrary paths. */
public class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File apk(Uri uri) throws FileNotFoundException {
        if (!"/update.apk".equals(uri.getPath()) || uri.getQuery() != null || uri.getFragment() != null) throw new FileNotFoundException();
        File file = new File(getContext().getFilesDir(), "updates/update.apk");
        if (!file.isFile()) throw new FileNotFoundException(); return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(apk(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        try {
            File file = apk(uri); String[] columns = projection == null ? new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE } : projection;
            MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? "E-PLANNING.apk" : OpenableColumns.SIZE.equals(columns[i]) ? file.length() : null;
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
