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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony.Sms;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

public class LifeSaver extends Activity implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
    private static final int REQUEST_DRIVE_RESOLUTION = 9000;
    public static final String BACKUP_FILE_PREFIX = "LifeSaver-";
    public static final String BACKUP_FILE_EXTENSION = ".json";
    public static final String CALLS_KIND = "Calls";
    public static final String MESSAGES_KIND = "Messages";

    private TextView mSaveText, mRestoreText;
    private ImageView mSaveBuoy, mRestoreBuoy;
    private final static long DURATION = 1000L;
    private Intent mNextStep;
    private GoogleApiClient mGoogleApiClient;
    public static String mDefaultSmsApp;

    public static final String TAG = "LIFESAVER2";

    @Override
    public void onCreate(Bundle mumble) {
        super.onCreate(mumble);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main);

        mSaveBuoy = (ImageView) findViewById(R.id.topBuoy);
        mRestoreBuoy = (ImageView) findViewById(R.id.bottomBuoy);
        mSaveText = (TextView) findViewById(R.id.topText);
        mRestoreText = (TextView) findViewById(R.id.bottomText);

        findViewById(R.id.mainTop).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mNextStep = new Intent(LifeSaver.this, Saver.class);
                saveAnimation();
            }
        });
        findViewById(R.id.mainBottom).setOnClickListener( new OnClickListener() {
            public void onClick(View v) {
                mNextStep = new Intent(LifeSaver.this, Restorer.class);
                restoreAnimation();
            }
        });

        // Force the user to connect to Google Drive successfully before proceeding.
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        fixSmsForKitKat();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void fixSmsForKitKat() {
        // If KitKat, prompt to become default SMS app if we aren't already.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            mDefaultSmsApp = Sms.getDefaultSmsPackage(this); 
            if (!mDefaultSmsApp.equals(this.getPackageName())) {
                Intent intent = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, this.getPackageName());
                this.startActivity(intent);
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // they may have been blanked by the transfer animation
        mSaveBuoy.setVisibility(View.VISIBLE);
        mRestoreBuoy.setVisibility(View.VISIBLE);
        mSaveText.setVisibility(View.VISIBLE);
        mRestoreText.setVisibility(View.VISIBLE);
    }

    private AnimationSet roll(ImageView buoy, boolean left) {
        AnimationSet roll = new AnimationSet(false);
        float degrees = 360F;
        float target = (float) buoy.getWidth();
        boolean landscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        if (left) {
            if (!landscape) {
                degrees = -degrees;
                target = -target;
            }
        } else {
            if (landscape) {
                degrees = -degrees;
                target = -target;
            }
        }
        RotateAnimation spin = new RotateAnimation(0, degrees,
                buoy.getWidth() / 2, buoy.getHeight() / 2);
        spin.setDuration(DURATION);
        TranslateAnimation move = new TranslateAnimation(0F, target, 0F, 0F);
        move.setDuration(DURATION);
        roll.addAnimation(spin);
        roll.addAnimation(move);
        return roll;
    }

    private AlphaAnimation fade() {
        AlphaAnimation a = new AlphaAnimation(1.0F, 0.0F);
        a.setDuration(DURATION);
        return a;
    }

    AnimationListener toNextStep = new AnimationListener() {
        public void onAnimationStart(Animation animation) {
        }
        public void onAnimationRepeat(Animation animation) {
        }
        public void onAnimationEnd(Animation animation) {
            startActivity(mNextStep);

            // so that the next view slides in smoothly
            mSaveBuoy.setVisibility(View.GONE);
            mSaveText.setVisibility(View.GONE);
            mRestoreBuoy.setVisibility(View.GONE);
            mRestoreText.setVisibility(View.GONE);
        }
    };

    private void restoreAnimation() {
        Animation roll = roll(mRestoreBuoy, true);
        Animation fade = fade();
        mSaveBuoy.startAnimation(fade);
        mSaveText.startAnimation(fade);
        mRestoreText.startAnimation(fade);
        mRestoreBuoy.startAnimation(roll);

        fade.setAnimationListener(toNextStep);
    }

    private void saveAnimation() {
        Animation roll = roll(mSaveBuoy, false);
        Animation fade = fade();
        mSaveBuoy.startAnimation(roll);
        mSaveText.startAnimation(fade);
        mRestoreText.startAnimation(fade);
        mRestoreBuoy.startAnimation(fade);

        fade.setAnimationListener(toNextStep);
    }

    public static Intent comeBack(Context context) {
        Intent intent = new Intent(context, LifeSaver.class);
        return intent;
    }

    public static String getBackupFileName(String key) {
        return BACKUP_FILE_PREFIX + key + BACKUP_FILE_EXTENSION;
    }

    public static GoogleApiClient newGoogleApiClient(final Context context, final Activity activity) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_APPFOLDER)
                .build();
        googleApiClient.blockingConnect();
        return googleApiClient;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DRIVE_RESOLUTION && resultCode == RESULT_OK) {
            // Resolved, try connecting to Drive again.
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Error connecting to Google Drive API: " + connectionResult.toString());
        if (!connectionResult.hasResolution()) {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
            return;
        }

        try {
            connectionResult.startResolutionForResult(this, REQUEST_DRIVE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Error resolving Google Drive connection error.");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to Google Drive API; LifeSaver is ready.");
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }
}
