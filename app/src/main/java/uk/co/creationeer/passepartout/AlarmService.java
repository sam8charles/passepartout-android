package uk.co.creationeer.passepartout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

/**
 * Foreground service that runs from alarm time until the user makes a decision.
 * Listens for SCREEN_ON and USER_PRESENT and launches TaskAlarmActivity each time.
 */
public class AlarmService extends Service {

    private static final String CHANNEL_ID = "passepartout_service";
    private static final int    NOTIF_ID   = 2001;

    private BroadcastReceiver screenReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        // Register dynamic receiver for SCREEN_ON and USER_PRESENT
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                String action = i.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action) ||
                    Intent.ACTION_USER_PRESENT.equals(action)) {
                    launchTaskScreen();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);

        // Launch immediately in case screen is already on
        launchTaskScreen();

        return START_STICKY;
    }

    private void launchTaskScreen() {
        Intent launch = new Intent(this, TaskAlarmActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Passepartout", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Passepartout")
            .setContentText("Waiting for your decision...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setAutoCancel(false)
            .build();
    }
}
