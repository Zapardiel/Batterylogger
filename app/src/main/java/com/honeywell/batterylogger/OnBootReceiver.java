package com.honeywell.batterylogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by E438447 on 2/8/2017.
 */
public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceLauncher = new Intent(context, BatLog.class);
            context.startService(serviceLauncher);
        }
    }
}
