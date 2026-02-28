package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    static final String PREFS = "passepartout";
    static final String KEY_SESSION = "session_cookie";
    static final String BASE_URL = "https://creationeer.co.uk/goforit/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        EditText sessionInput = findViewById(R.id.session_input);
        TextView statusText = findViewById(R.id.status_text);
        Button saveBtn = findViewById(R.id.save_btn);
        Button testBtn = findViewById(R.id.test_btn);

        // Show current session if saved
        String savedSession = prefs.getString(KEY_SESSION, "");
        if (!savedSession.isEmpty()) {
            sessionInput.setText(savedSession);
            statusText.setText("✓ Session saved. Alarm set for 06:00 daily.");
        } else {
            statusText.setText("Enter your PHPSESSID to connect to Passepartout.");
        }

        saveBtn.setOnClickListener(v -> {
            String session = sessionInput.getText().toString().trim();
            if (session.isEmpty()) {
                Toast.makeText(this, "Please enter your session ID", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(KEY_SESSION, session).apply();
            scheduleAlarm(this);
            statusText.setText("✓ Saved! Alarm set for 06:00 daily.");
            Toast.makeText(this, "Saved and alarm scheduled", Toast.LENGTH_SHORT).show();
        });

        testBtn.setOnClickListener(v -> {
            // Fire the alarm screen immediately for testing
            String session = prefs.getString(KEY_SESSION, "");
            if (session.isEmpty()) {
                Toast.makeText(this, "Save your session ID first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, TaskAlarmActivity.class);
            startActivity(intent);
        });
    }

    public static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("uk.co.creationeer.passepartout.ALARM");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set for 06:00 today, or tomorrow if already past
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
            // Fallback for devices that restrict exact alarms
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        }
    }
}
