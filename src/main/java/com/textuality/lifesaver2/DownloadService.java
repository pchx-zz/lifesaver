package com.textuality.lifesaver2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony.Sms;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.SortableMetadataField;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.drive.query.SortOrder;
import com.google.android.gms.drive.query.SortableField;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class DownloadService extends IntentService {

    private Notifier mNotifier;
    private static final Map<String, List<String>> MEDIA_TYPES_LIST = new HashMap<String, List<String>>();
    private static final ContentValues DUMMY_TEMPLATE;

    static {
        MEDIA_TYPES_LIST.put("Accept", Arrays.asList("application/json"));
        DUMMY_TEMPLATE = new ContentValues();
        DUMMY_TEMPLATE.put("status", -1);
        DUMMY_TEMPLATE.put("read", "1");
        DUMMY_TEMPLATE.put("type", 2);
        DUMMY_TEMPLATE.put("date", 1330452144403L);
    }

    public DownloadService() {
        super("LifeSaver Downloader");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mNotifier = new Notifier(this);
        mNotifier.notifyRestore(getString(R.string.restoring), false);

        GoogleApiClient googleApiClient = LifeSaver.newGoogleApiClient(getApplicationContext(), null);
        if (!googleApiClient.isConnected()) {
            error("Error connecting to Google Drive.");
            return;
        }

        // 1. set up
        Columns callColumns = ColumnsFactory.calls(this);
        Columns messageColumns = ColumnsFactory.messages(this);

        Map<String, Boolean> loggedCalls = Columns.loadKeys(this, Columns.callsProvider(), callColumns);
        Map<String, Boolean> loggedMessages = Columns.loadKeys(this, Columns.messagesProvider(), messageColumns);

        // 2. Fetch calls
        String callsBody = getDriveFile(googleApiClient, LifeSaver.CALLS_KIND);
        if (callsBody == null) {
            error("Error downloading calls backup file from Google Drive.");
            return;
        }

        // 3. load calls we haven't already seen
        Restored callsRestored = restore(callsBody, loggedCalls, callColumns, Columns.callsProvider(), null, LifeSaver.CALLS_KIND);
        mNotifier.notifyRestore(getString(R.string.restored) + callsRestored.stored + "/" + 
                callsRestored.downloaded + 
                getString(R.string.calls), false);

        // 4. Fetch messages
        String messagesBody = getDriveFile(googleApiClient, LifeSaver.MESSAGES_KIND);
        if (messagesBody == null) {
            error("Error downloading messages backup file from Google Drive.");
            return;
        }

        // 5. load messages we haven't already seen
        Uri messagesProvider = Columns.messagesProvider();

        Restored messagesRestored = restore(messagesBody, loggedMessages, messageColumns, messagesProvider, "thread_id", LifeSaver.MESSAGES_KIND);
        mNotifier.notifyRestore(getString(R.string.restored) + callsRestored.stored + "/" + callsRestored.downloaded + 
                getString(R.string.calls) + ", " +
                messagesRestored.stored + "/" + messagesRestored.downloaded + 
                getString(R.string.messages), true);

        // 6. Now, run through all the messages we just restored, add a bogus message for each address, and then delete it.
        //    This forces the MmsSmsProvider to recalculate the timestamp
        ContentValues dummyValues = new ContentValues(DUMMY_TEMPLATE);
        dummyValues.put("body", "LifeSaver dummy message at " + System.currentTimeMillis());
        HashMap<String, String> patchedAddresses = new HashMap<String, String>();
        ContentResolver cr = getContentResolver();
        for (int i = 0; i < messagesRestored.toRestore.length(); i++) {
            JSONObject json = (JSONObject) messagesRestored.toRestore.optJSONObject(i);
            String address = json.optString("address");
            if (address != null && patchedAddresses.get(address) == null) {
                dummyValues.put("address", address);
                Uri dummyUri = cr.insert(messagesProvider, dummyValues);
                cr.delete(dummyUri, null, null);
                patchedAddresses.put(address, address);
            } 
        }

        Intent done = new Intent(this, Done.class);
        done.putExtra("isRestore", true);
        done.putExtra("result", getString(R.string.restored) + callsRestored.stored + "/" + callsRestored.downloaded + 
                getString(R.string.calls) + ", " +
                messagesRestored.stored + "/" + messagesRestored.downloaded + 
                getString(R.string.messages) + ".");
        done.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(done);
        
        stopSelf();
    }

    private ByteArrayOutputStream readFully(InputStream inputStream)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
          baos.write(buffer, 0, length);
        }
        return baos;
    }

    protected String getDriveFile(GoogleApiClient googleApiClient, String kind) {
        DriveFolder root = Drive.DriveApi.getAppFolder(googleApiClient);
        PendingResult<DriveApi.MetadataBufferResult> queryResult =
                root.queryChildren(googleApiClient, new Query.Builder()
                        .addFilter(Filters.eq(SearchableField.TITLE, LifeSaver.getBackupFileName(kind)))
                        .setSortOrder(new SortOrder.Builder()
                                .addSortDescending(SortableField.MODIFIED_DATE)
                                .build())
                    .build());
        Metadata fileMetadata = queryResult.await().getMetadataBuffer().get(0);
        DriveFile file = Drive.DriveApi.getFile(googleApiClient, fileMetadata.getDriveId());

        DriveApi.DriveContentsResult fileContents = null;
        try {
            fileContents = file.open(googleApiClient, DriveFile.MODE_READ_ONLY, null).await();

            InputStream stream = fileContents.getDriveContents().getInputStream();
            try {
                return readFully(stream).toString();
            } catch (IOException e) {
                return null;
            }
        }
        finally {
            fileContents.getDriveContents().discard(googleApiClient);
        }
    }

    private Restored restore(String response, Map<String, Boolean> logged, Columns columns,
            Uri provider, String zeroField, String callsOrMessages) {
        Restored restored = new Restored();
        ContentResolver cr = getContentResolver();
        try {  
            JSONObject bodyJSON = new JSONObject(response);
            JSONArray toRestore = restored.toRestore = bodyJSON.getJSONArray(callsOrMessages);
            restored.downloaded = toRestore.length();

            for (int i = 0; i < toRestore.length(); i++) {
                JSONObject json = (JSONObject) toRestore.get(i);
                String key = columns.jsonToKey(json);
                if (logged.get(key) == null) {
                    ContentValues cv = columns.jsonToContentValues(json);
                    if (zeroField != null)
                        cv.put(zeroField, 0);
                    cr.insert(provider, cv);
                    restored.stored++;
                }
            }
        } catch (JSONException e) {
            error("JSON exception " + e.getLocalizedMessage());
        }
        return restored;
    }

    private void error(String message) {
        mNotifier.notifyRestore(getString(R.string.ouch) + message, true);


        // If KitKat, prompt to restore default SMS app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent i = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
            i.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, LifeSaver.mDefaultSmsApp);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(i);
        }

        stopSelf();
    }

    private class Restored {
        public int downloaded = 0;
        public int stored = 0;
        public JSONArray toRestore = null;
    }
}
