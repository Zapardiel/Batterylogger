package com.honeywell.batterylogger;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_WRITE_STORAGE = 112;
    private View view;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {       // Only for Android Marshmallow or higher
            if (!checkPermission()) {                               // Checks if the App needs a permission
                handler = new Handler();                            // Handler launchs a callBack after 50ms runnig the app
                handler.postDelayed(askPermissions, 50);
            } else {
                runService(this);
                finish();
            }
        } else {
            runService(this);
            finish();
        }
    }

    private void runService(Context context) {
        Intent serviceLauncher = new Intent(context, BatLog.class);
        context.startService(serviceLauncher);
    }

   //region ANDROID 6, REQUEST PERMISSIONS

    // Checks that the Permission is not granted yet.
    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }
    // askPermissions is launched just one time after onCreate, because it cannot be called on that method.
    private Runnable askPermissions = new Runnable() {
        @Override
        public void run() {
            requestPermission();
        }
    };

    // Ask for Permission REQUEST_WRITE_STORAGE
    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
    }

    // Callback that is called when the user clicks on Deny or Allow
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length > 0) {

                    boolean writeAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (writeAccepted) {
                        runService(MainActivity.this);
                        finish();
                        Log.e("BatLog", "Permissions Granted");
                    } else {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            // Is it just Denied but not checked "Never Ask Again"?  --> Ask again
                            showMessageOKCancel("If you don't allow access to disk, the App won't work!. Press ALLOW",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            restartApp();
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(MainActivity.this, "If you don't GRANT permission, it won't work.", Toast.LENGTH_SHORT).show();
                                            finish();
                                        }
                                    });
                            return;
                        } else {
                            // It was checked "Never Ask Again!"
                            Toast.makeText(MainActivity.this, "Sorry! Go to Apps->Permissions", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                }
                break;
        }
    }
//endregion

    //Show Dialog
    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener koListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", koListener)
                .create()
                .show();
    }
    //Restart the App
    private void restartApp() {
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

}
