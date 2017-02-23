package com.honeywell.batterylogger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by E438447 on 2/8/2017.
 */
public class BatLog extends Service {

    private static final int CHECK_BAT_INTERVAL = 60000;
    private static final int CHECK_SCANNER_INTERVAL = 10000;
    private double batteryLevel;
    private Handler handler_scanner;
    private Handler handler_bat;
    PowerManager pm;

    private static final String ACTION_BARCODE_DATA = "com.honeywell.sample.action.BARCODE_DATA";
    private static final String ACTION_CLAIM_SCANNER = "com.honeywell.aidc.action.ACTION_CLAIM_SCANNER";
    private static final String ACTION_RELEASE_SCANNER = "com.honeywell.aidc.action.ACTION_RELEASE_SCANNER";
    private static final String EXTRA_SCANNER = "com.honeywell.aidc.extra.EXTRA_SCANNER";
    private static final String EXTRA_PROFILE = "com.honeywell.aidc.extra.EXTRA_PROFILE";
    private static final String EXTRA_PROPERTIES = "com.honeywell.aidc.extra.EXTRA_PROPERTIES";

    // Gets system Intent ACTION_BATTERY_CHANGED
    private BroadcastReceiver batInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent batteryIntent) {
            int rawlevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (rawlevel >= 0 && scale > 0) {
                batteryLevel = (rawlevel * 100) / scale;
            }

            Log.e("BatLog", batteryLevel + "%");
            appendLog(": Bat=" + String.format("%.00f", batteryLevel) + "%");
        }
    };

    // Gets system Intent android.hardware.usb.action.USB_STATE
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent usbIntent) {

            try {
                boolean bConnected = usbIntent.getExtras().getBoolean("connected");
                if (bConnected) {
                    File logFile = new File("sdcard/BatLog.txt");
                    Uri uri = Uri.fromFile(logFile);
                    Intent scanFileIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    sendBroadcast(scanFileIntent);
                }
                Log.e("BatLog", "USB Connected");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };

    private BroadcastReceiver barcodeDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BARCODE_DATA.equals(intent.getAction())) {
                SimulateScanKey(false);         // Release Scanner Buttons
                Log.e("BatLog", "Barcode Read");

                // schedule next scanner check
                handler_scanner.postDelayed(runScannerRunnable, CHECK_SCANNER_INTERVAL);

                Log.e("BatLog","Waiting runScanner...");
            }
        }
    };

    private Runnable checkBatteryStatusRunnable = new Runnable() {
        @Override
        public void run() {
            // DO WHATEVER YOU WANT, EVERY "CHECK_BAT_INTERVAL"

            // schedule next battery check
            handler_bat.postDelayed(checkBatteryStatusRunnable, CHECK_BAT_INTERVAL);
            Log.e("BatLog", batteryLevel + "% cached");
            appendLog(": Bat=" + String.format("%.00f", batteryLevel) + "% cached");

            // Checks DOZE Mode. **NoT WoRkInG**
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                pm = (PowerManager) BatLog.this.getSystemService(Context.POWER_SERVICE);
//                if (pm.isDeviceIdleMode()) appendLog(": DOZE Mode!!");
//                Log.e("BatLog","DOZE Mode!!");
//            }
        }
    };

    private Runnable runScannerRunnable = new Runnable() {
        @Override
        public void run() {
            // DO WHATEVER YOU WANT, EVERY "CHECK_SCANNER_INTERVAL"
            Log.e("BatLog","Runnable for Scanner");
            SimulateScanKey(true);
        }
    };

    void SimulateScanKey(boolean KeyDown) {
        KeyEvent SendKeyEvent;
        Intent sendIntentDown = new Intent("com.honeywell.intent.action.SCAN_BUTTON");
        if (KeyDown) {
            SendKeyEvent = new KeyEvent(0, 0);

        } else {
            SendKeyEvent = new KeyEvent(1, 0);
        }
        sendIntentDown.putExtra("android.intent.extra.KEY_EVENT", SendKeyEvent);
        this.sendBroadcast(sendIntentDown);
    }

    private void claimScanner() {
        Bundle properties = new Bundle();
        properties.putBoolean("DPR_DATA_INTENT", true);
        properties.putString("DPR_DATA_INTENT_ACTION", ACTION_BARCODE_DATA);
        sendBroadcast(new Intent(ACTION_CLAIM_SCANNER)
                        .putExtra(EXTRA_SCANNER, "dcs.scanner.imager")
                        .putExtra(EXTRA_PROFILE, "Default")
                        .putExtra(EXTRA_PROPERTIES, properties)
        );
        Log.e("BatLog", "Scanner Claimed");
    }
    private void releaseScanner() {
        sendBroadcast(new Intent(ACTION_RELEASE_SCANNER));
        Log.e("BatLog","Released Scanner");
    }

    @Override
    public void onCreate() {
        handler_bat = new Handler();
        handler_scanner = new Handler();
        handler_bat.postDelayed(checkBatteryStatusRunnable, CHECK_BAT_INTERVAL);
        handler_scanner.postDelayed(runScannerRunnable, CHECK_SCANNER_INTERVAL);

        registerReceiver(batInfoReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        registerReceiver(usbReceiver,
                new IntentFilter("android.hardware.usb.action.USB_STATE"));

        registerReceiver(barcodeDataReceiver, new IntentFilter(ACTION_BARCODE_DATA));

        claimScanner();
    }




    @Override
    public void onDestroy() {
        unregisterReceiver(batInfoReceiver);
        handler_bat.removeCallbacks(checkBatteryStatusRunnable);
        handler_scanner.removeCallbacks(checkBatteryStatusRunnable);
        releaseScanner();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private String bytesToHexString(byte[] arr) {
        String s = "[]";
        if (arr != null) {
            s = "[";
            for (int i = 0; i < arr.length; i++) {
                s += "0x" + Integer.toHexString(arr[i]) + ", ";
            }
            s = s.substring(0, s.length() - 2) + "]";
        }
        return s;
    }


    public void appendLog(String batLevel) {
        Date curDate = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String DateToStr = format.format(curDate);
        File logFile = new File("sdcard/BatLog.txt");

        if (logFile.exists()) {
            Calendar time = Calendar.getInstance();
            time.add(Calendar.DAY_OF_YEAR, -1);

            //I store the required attributes here and delete them
            Date lastModified = new Date(logFile.lastModified());
            if (lastModified.before(time.getTime())) {
                //file is older than a week
                File to = new File("sdcard", "BatLog_" + format.format(lastModified) + ".txt");
                logFile.renameTo(to);
                try {
                    logFile.createNewFile();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else {
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(DateToStr + batLevel);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
