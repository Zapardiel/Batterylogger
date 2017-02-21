package com.honeywell.batterylogger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
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

    private static final int CHECK_BATTERY_INTERVAL = 60000;
    private double batteryLevel;
    private Handler handler;
    PowerManager pm;

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
                    Toast.makeText(BatLog.this, "USB CONNECTED", Toast.LENGTH_SHORT).show();

                    File logFile = new File("sdcard/BatLog.txt");
                    Uri uri = Uri.fromFile(logFile);
                    Intent scanFileIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    sendBroadcast(scanFileIntent);
                }
                Log.e("BatLog", "USB: CONNECTED");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };

    private Runnable checkBatteryStatusRunnable = new Runnable() {
        @Override
        public void run() {
            //DO WHATEVER YOU WANT WITH LATEST BATTERY LEVEL STORED IN batteryLevel HERE...

            // schedule next battery check
            handler.postDelayed(checkBatteryStatusRunnable, CHECK_BATTERY_INTERVAL);
            Log.e("BatLog", batteryLevel + "% cached");
            appendLog(": Bat=" + String.format("%.00f", batteryLevel) + "% cached");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm = (PowerManager) BatLog.this.getSystemService(Context.POWER_SERVICE);
                if (pm.isDeviceIdleMode()) appendLog(": DOZE Mode!!");
                Log.e("BatLog",": DOZE Mode!!");
            }
        }
    };

    @Override
    public void onCreate() {
        handler = new Handler();
        handler.postDelayed(checkBatteryStatusRunnable, CHECK_BATTERY_INTERVAL);
        registerReceiver(batInfoReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        registerReceiver(usbReceiver,
                new IntentFilter("android.hardware.usb.action.USB_STATE"));


    }

    @Override
    public void onDestroy() {
        unregisterReceiver(batInfoReceiver);
        handler.removeCallbacks(checkBatteryStatusRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
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
