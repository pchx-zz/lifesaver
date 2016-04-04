package com.textuality.lifesaver2;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.SynchronousQueue;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

public class UploadService extends IntentService {

    private static final String TAG = "UploadService";
    private int mCallCount, mMessageCount;

    private Notifier mNotifier;
    private boolean mError = false;

    public UploadService() {
        super("LifeSaver Uploader");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mNotifier = new Notifier(this);

        GoogleApiClient googleApiClient = LifeSaver.newGoogleApiClient(getApplicationContext(), null);
        if (!googleApiClient.isConnected()) {
            error("Error connecting to Google Drive.");
            return;
        }

        Cursor calls = getContentResolver().query(Columns.callsProvider(), null,
                null, null, null);
        Cursor messages = getContentResolver().query(Columns.messagesProvider(),
                null, null, null, null);
        mCallCount = calls.getCount();
        mMessageCount = messages.getCount();
        mNotifier.notifySave(getString(R.string.saving) + 
                mCallCount + getString(R.string.calls_and) +  
                mMessageCount + getString(R.string.messages), false);

        saveDriveFile(googleApiClient, LifeSaver.CALLS_KIND, calls, ColumnsFactory.calls(this));
        mNotifier.notifySave(getString(R.string.saved) + 
                mCallCount + getString(R.string.calls_saving) +
                mMessageCount + getString(R.string.messages), false);

        saveDriveFile(googleApiClient, LifeSaver.MESSAGES_KIND, messages, ColumnsFactory.messages(this));

        if (!mError) {
            mNotifier.notifySave(getString(R.string.saved) +
                    mCallCount + getString(R.string.calls_and) +
                    mMessageCount + getString(R.string.messages), true);

            Intent done = new Intent(this, Done.class);
            done.putExtra("isRestore", false);
            done.putExtra("result", getString(R.string.saved) +
                    mCallCount + getString(R.string.calls_and) +
                    mMessageCount + getString(R.string.messages) + ".");
            done.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(done);
        }
    }

    private void saveDriveFile(final GoogleApiClient googleApiClient, final String key, final Cursor cursor, final Columns columns) {
        DriveApi.DriveContentsResult contentsResult =
                Drive.DriveApi.newDriveContents(googleApiClient).await();
        if (!contentsResult.getStatus().isSuccess()) {
            error("Error creating backup file on Google Drive.");
            return;
        }

        OutputStream stream = contentsResult.getDriveContents().getOutputStream();
        try {
            int count = cursor.getCount() - 1, sent = 0;
            stream.write(("{ \"" + key + "\" : [\n").getBytes());
            while (cursor.moveToNext()) {
                stream.write(columns.cursorToJSON(cursor).toString().getBytes());
                if (sent < count)
                    stream.write(",\n".getBytes());
                else
                    stream.write("\n".getBytes());
                sent++;
            }
            stream.write(("] }\n").getBytes());
            cursor.close();
        } catch (IOException e1) {
            Log.i(TAG, "Unable to write file contents.");
            error("Error writing backup file contents.");
            return;
        }

        DriveFolder root = Drive.DriveApi.getAppFolder(googleApiClient);
        MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                .setMimeType("application/json").setTitle(LifeSaver.getBackupFileName(key)).build();
        DriveFolder.DriveFileResult fileResult =
                root.createFile(googleApiClient, metadataChangeSet, contentsResult.getDriveContents()).await();
        if (!fileResult.getStatus().isSuccess()) {
            error("Error writing backup file to Google Drive.");
        }
    }

    private void error(String message) {
        mNotifier.notifySave(getString(R.string.upload_failed) + message, true);
        mError = true;
    }
}
