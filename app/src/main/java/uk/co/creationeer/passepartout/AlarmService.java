package uk.co.creationeer.passepartout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.Calendar;

public class AlarmService extends Service {

    private static final String CHANNEL_ID = "passepartout_service";
    private static final int    NOTIF_ID   = 2001;

    private BroadcastReceiver screenReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) ||
                    Intent.ACTION_USER_PRESENT.equals(i.getAction())) {
                    checkAndLaunch();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);

        // Check immediately on start
        checkAndLaunch();

        return START_STICKY;
    }

    private void checkAndLaunch() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        // Check if decision already made today
        String todayKey = getTodayKey();
        if (prefs.getBoolean("decided_" + todayKey, false)) return;

        // Check if it's past 12:00
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) < 12) return;

        // It's after 12:00 and no decision made today — launch task screen
        MainActivity.writeLog(this, "checkAndLaunch: launching task screen for " + todayKey);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "passepartout:alarm");
        wl.acquire(3000);

        Intent launch = new Intent(this, TaskAlarmActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
    }

    public static String getTodayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH)+1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Passepartout", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Passepartout")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setAutoCancel(false)
            .build();
    }
}
