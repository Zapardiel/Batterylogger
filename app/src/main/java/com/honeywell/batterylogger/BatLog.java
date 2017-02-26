package com.honeywell.batterylogger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;


/**
 * Created by E438447 on 2/8/2017.
 */
public class BatLog extends Service {

    // int Values
    private int CHECK_BAT_INTERVAL = 60000;
    private int CHECK_SCANNER_INTERVAL = 10000;
    private int CHECK_NET_INTERVAL = 10000;

    // String Values
    private String inet_add = "www.yahoo.es";

    // Flags
    PowerManager pm;
    private double batteryLevel;
    private boolean chkScanner = false;
    private boolean chkNet = false;
    private boolean chkScanner_Exclusive = false;

    // Handlers
    private Handler handler_scanner;
    private Handler handler_bat;
    private Handler handler_net;

    private static final String ACTION_BARCODE_DATA = "com.honeywell.sample.action.BARCODE_DATA";
    private static final String ACTION_CLAIM_SCANNER = "com.honeywell.aidc.action.ACTION_CLAIM_SCANNER";
    private static final String ACTION_RELEASE_SCANNER = "com.honeywell.aidc.action.ACTION_RELEASE_SCANNER";
    private static final String EXTRA_SCANNER = "com.honeywell.aidc.extra.EXTRA_SCANNER";
    private static final String EXTRA_PROFILE = "com.honeywell.aidc.extra.EXTRA_PROFILE";
    private static final String EXTRA_PROPERTIES = "com.honeywell.aidc.extra.EXTRA_PROPERTIES";

    //region SERVICE STATUS
    // Knows Service Lifecycle
    private static BatLog instance = null;

    public static boolean isInstanceCreated() {
        return instance != null;
    }
    //endregion

    //region BROADCAST RECEIVER
    // Gets system Intent ACTION_BATTERY_CHANGED
    private BroadcastReceiver batInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent batteryIntent) {
            int rawlevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            int chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

            if (rawlevel >= 0 && scale > 0) {
                batteryLevel = (rawlevel * 100) / scale;
            }

            String batStatus=isCharging?(acCharge?"Charging AC":"Charging Usb"):"Discharging";
            Log.e("BatLog", batteryLevel + "%, " + batStatus);
            appendLog("Bat=" + String.format("%.00f", batteryLevel) + "%, " + batStatus);
        }
    };

    // Gets system Intent android.hardware.usb.action.USB_STATE
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent usbIntent) {
            try {
                Date curDate = new Date();
                SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");

                boolean bConnected = usbIntent.getExtras().getBoolean("connected");
                if (bConnected) {
                    appendLog("USB Cable Plugged");
                    File logFile = new File("sdcard","BatLog_" + format_date.format(curDate) + ".txt");
                    Uri uri = Uri.fromFile(logFile);
                    Intent scanFileIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    sendBroadcast(scanFileIntent);
                }else{
                    appendLog("USB Cable Unplugged");
                }

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
                appendLog("Barcode Read: " + intent.getStringExtra("data"));

                // schedule next scanner check
                handler_scanner.postDelayed(runScannerRunnable_ON, CHECK_SCANNER_INTERVAL);
            }
        }
    };
    //endregion

    //region RUNNABLES
    private Runnable runBatteryStatusRunnable = new Runnable() {
        @Override
        public void run() {
            // Schedule next battery check
            handler_bat.postDelayed(runBatteryStatusRunnable, CHECK_BAT_INTERVAL);
            appendLog("Bat=" + String.format("%.00f", batteryLevel) + "% cached");
        }
    };

    private Runnable runScannerRunnable_ON = new Runnable() {
        @Override
        public void run() {
            // Scanner trigger
            SimulateScanKey(true);

            // Turns OFF the scanner after 1sec
            handler_scanner.postDelayed(runScannerRunnable_OFF, 1000);
        }
    };

    private Runnable runScannerRunnable_OFF = new Runnable() {
        @Override
        public void run() {
            // scanner trigger release
            SimulateScanKey(false);

            // schedule next scanner check
            handler_scanner.postDelayed(runScannerRunnable_ON, CHECK_SCANNER_INTERVAL);
        }
    };

    private Runnable runNetRunnable = new Runnable() {
        @Override
        public void run() {
            // Do network check
            appendLog("Ping: " + parsePingResults(ping(inet_add)));
            handler_net.postDelayed(runNetRunnable, CHECK_NET_INTERVAL);
        }
    };
    //endregion

    //region Overidable Methods
    @Override
    public void onCreate() {
        // Flag to know that the service is running
        instance = this;

        // Loads settings from Ini File
        try {
            iniFile ini = new iniFile("/sdcard/BatLog.ini");
            CHECK_BAT_INTERVAL = ini.getInt("DEFAULT", "BAT_INTERVAL", 60000);

            chkScanner = ini.getBoolean("SCANNER", "SCANNER_ENABLED", false);
            CHECK_SCANNER_INTERVAL = ini.getInt("SCANNER", "SCANNER_INTERVAL", 1000);
            chkScanner_Exclusive = ini.getBoolean("SCANNER", "SCANNER_EXCLUSIVE", false);

            chkNet = ini.getBoolean("NETWORK", "NET_ENABLED", false);
            CHECK_NET_INTERVAL = ini.getInt("NETWORK", "NET_INTERVAL", 1000);
            inet_add = ini.getString("NETWORK", "NET_ADDR", "www.yahoo.es");

        } catch (IOException ex) {
            Log.e("BatLog", "Ini File does not exists");
            appendLog("Ini File does not exists");
        }

        // Scanner Handler
        if (chkScanner) {
            handler_scanner = new Handler();
            handler_scanner.postDelayed(runScannerRunnable_ON, CHECK_SCANNER_INTERVAL);
            registerReceiver(barcodeDataReceiver, new IntentFilter(ACTION_BARCODE_DATA));
        }

        // Ping Handler
        if (chkNet) {
            appendLog("Ping: " + parsePingResults(ping(inet_add)));
            handler_net = new Handler();
            handler_net.postDelayed(runNetRunnable, CHECK_NET_INTERVAL);
        }

        // Battery Handler
        handler_bat = new Handler();
        handler_bat.postDelayed(runBatteryStatusRunnable, CHECK_BAT_INTERVAL);
        registerReceiver(batInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(batInfoReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        registerReceiver(batInfoReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));

        // USB detection
        registerReceiver(usbReceiver, new IntentFilter("android.hardware.usb.action.USB_STATE"));

        // Claims scanner service if it's requested
        if (chkScanner_Exclusive) claimScanner();
    }

    @Override
    public void onDestroy() {
        // flag to quote service status
        instance = null;

        // unregister Broadcast Receivers
        try {
            unregisterReceiver(batInfoReceiver);
            unregisterReceiver(usbReceiver);
            unregisterReceiver(barcodeDataReceiver);
        } catch (Exception ex) {
            Log.e("BatLog", "Exception Destroying Service (Unregistering Broadcasts): " + ex.getMessage());
        }
        // removeCallBacks
        try {
            handler_bat.removeCallbacks(runBatteryStatusRunnable);
            handler_scanner.removeCallbacks(runScannerRunnable_ON);
            handler_scanner.removeCallbacks(runScannerRunnable_OFF);
            handler_net.removeCallbacks(runNetRunnable);
        } catch (Exception ex) {
            Log.e("BatLog", "Exception Destroying Service (Removing Handlers): " + ex.getMessage());
        }
        // release scanner
        SimulateScanKey(false);
        if (chkScanner_Exclusive) releaseScanner();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    //endregion

    //region LOG FILES
    public void appendLog(String info) {
        Date curDate = new Date();
        SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat format_hour = new SimpleDateFormat("hh:mm:ss");
        String DateToStr = format_hour.format(curDate);
        File logFile = new File("sdcard","BatLog_" + format_date.format(curDate) + ".txt");

        if (!logFile.exists()) {
            // delete old files BatLog_xxx.txt
            deleteOldFiles(5);

            // create a new file BatLog_yyyyMMdd.txt
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(BatLog.this, "Impossible to write a LOG File!!", Toast.LENGTH_LONG).show();
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(DateToStr + ": " + info);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    class Pair implements Comparable {
        public long t;
        public File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    };

    private void deleteOldFiles(int maxFiles)
    {
        File dir = new File("sdcard");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("BatLog_(.*).txt");
            }
        });

        Pair[] pairs = new Pair[files.length];
        for (int i = 0; i < files.length; i++)
            pairs[i] = new Pair(files[i]);

        // Sort them by timestamp.
        Arrays.sort(pairs);

        // Take the sorted pairs and extract only the file part, discarding the timestamp.
        if (files.length>maxFiles) {
            for ( int i = 0; i < files.length - maxFiles; i++) {
                pairs[i].f.delete();
            }
        }
    }

    //endregion

    //region PING
    public String ping(String url) {
        String str = "";
        try {
            Process process = Runtime.getRuntime().exec(
                    "/system/bin/ping -c 1 " + url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            int i;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((i = reader.read(buffer)) > 0)
                output.append(buffer, 0, i);
            reader.close();

            // body.append(output.toString()+"\n");
            str = output.toString();
            // Log.d(TAG, str);
        } catch (Exception e) {
            appendLog("Exception: " + e.getMessage());
        }
        return str;
    }

    private String parsePingResults(String pingResults) {
        try {
            int ptime = pingResults.indexOf("time=");
            int pmill = pingResults.indexOf("ms");
            if ((pingResults.indexOf("unknown") >= 0) || ((pmill > ptime) && (ptime > 0) && (pmill > 0))) {
                return pingResults.substring(ptime, pmill + 2);
            } else {
                return "No Answer from Host";
            }
        } catch (Exception ex) {
            appendLog("Exception parsing Ping Statistics");
        }
        return "No Answer from Host";
    }
    //endregion

    //region SCANNER
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
        Log.e("BatLog", "Released Scanner");
    }

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
    //endregion
}
