package uk.co.creationeer.passepartout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Reschedule for tomorrow
        MainActivity.scheduleAlarm(context);

        // Launch the full-screen task activity
        Intent taskIntent = new Intent(context, TaskAlarmActivity.class);
        taskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        taskIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(taskIntent);
    }
}
