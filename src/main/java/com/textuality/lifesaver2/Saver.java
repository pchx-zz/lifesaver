/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.textuality.lifesaver2;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import com.google.android.gms.common.api.GoogleApiClient;

public class Saver extends Activity {

    private WebView mReadout;

    public static final String mHead = "<style>body {color: #fff; background: #000}\n" +
            "a { text-decoration: none; font-weight: bold; color: #f88;}</style>";

    @Override
    protected void onCreate(Bundle mumble) {
        super.onCreate(mumble);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.save);

        mReadout = (WebView) findViewById(R.id.saveData);
        mReadout.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mReadout.setBackgroundColor(0xff000000);
        mReadout.loadData(
                "<html><head>" + mHead + "</head><body><p>" +
                        str(R.string.preparing_save) +
                        "</p></body></html>",
                        "text/html", "utf-8");

        new PrepareUpload().execute();
    }


    class PrepareUpload extends AsyncTask<Void, String, String> {

        private int mCallCount;
        private int mMessageCount;

        @Override
        protected String doInBackground(Void... params) {
            Cursor calls = getContentResolver().query(Columns.callsProvider(), null,
                    null, null, null);
            mCallCount = calls.getCount();
            Cursor messages = getContentResolver().query(Columns.messagesProvider(),
                    null, null, null, null);
            mMessageCount = messages.getCount();
            (new Notifier(Saver.this)).notifySave(str(R.string.saving) + mCallCount +
                    str(R.string.calls_and) + mMessageCount +
                    str(R.string.messages), false);
            return null;
        }
        @Override
        protected void onPostExecute(String authToken) {
            String body = "<p>" + str(R.string.your_life) +
                        mCallCount + str(R.string.calls_and) +
                        mMessageCount + str(R.string.messages) +
                        ".  " +
                        str(R.string.upload_started) +
                        "</p><p>" + str(R.string.stand_by) + "</p>";

            String page = "<html><head>" + mHead + "</head><body>" + body + "<pStand by...</p></body></html>";
            mReadout.loadData(page, "text/html", "utf-8");

            Intent intent = new Intent(Saver.this, UploadService.class);
            startService(intent);
        }
    }

    private String str(int id) {
        return Saver.this.getString(id);
    }
}
