package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    static final String PREFS       = "passepartout";
    static final String KEY_USER    = "username";
    static final String KEY_PASS    = "password";
    static final String BASE_URL    = "https://creationeer.co.uk/goforit/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        TextView statusText = findViewById(R.id.status_text);
        Button testBtn      = findViewById(R.id.test_btn);

        // Always reset credentials to ensure correct values
        prefs.edit()
            .putString(KEY_USER, "shaun")
            .putString(KEY_PASS, "Flash_Robertson")
            .apply();
        scheduleAlarm(this);
        statusText.setText("✓ Ready. Alarm set for 06:00 daily.");

        testBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskAlarmActivity.class);
            startActivity(intent);
        });

        Button testNotifBtn = findViewById(R.id.test_notif_btn);
        testNotifBtn.setOnClickListener(v -> {
            // Simulate exactly what the real alarm does - send the broadcast
            Intent alarmIntent = new Intent(this, AlarmReceiver.class);
            alarmIntent.setAction(AlarmReceiver.ACTION_MORNING);
            sendBroadcast(alarmIntent);
            statusText.setText("Alarm broadcast sent - lock screen and check for notification");
        });
    }

    public static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("uk.co.creationeer.passepartout.ALARM");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        } catch (SecurityException e) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        }
    }
}
