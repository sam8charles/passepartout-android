package uk.co.creationeer.passepartout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_MORNING = "uk.co.creationeer.passepartout.ALARM";
    static final String CHANNEL_ID = "passepartout_alarm";
    static final int NOTIF_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        MainActivity.writeLog(context, "onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            MainActivity.scheduleAlarm(context);
            return;
        }

        if (ACTION_MORNING.equals(action)) {
            MainActivity.scheduleAlarm(context); // reschedule for tomorrow
            showFullScreenNotification(context);
        }
    }

    private void showFullScreenNotification(Context context) {
        // Intent that launches TaskAlarmActivity
        Intent activityIntent = new Intent(context, TaskAlarmActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPi = PendingIntent.getActivity(context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create high importance channel
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Passepartout Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Daily task decision");
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setBypassDnd(true);
            nm.createNotificationChannel(channel);
        }

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Passepartout")
                .setContentText("Time to make a decision")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();
        } else {
            notification = new Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Passepartout")
                .setContentText("Time to make a decision")
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();
        }

        nm.notify(NOTIF_ID, notification);
        MainActivity.writeLog(context, "Full screen notification posted");
    }
}
