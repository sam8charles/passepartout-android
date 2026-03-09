package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final String PREFS    = "passepartout";
    static final String KEY_USER = "username";
    static final String KEY_PASS = "password";
    static final String BASE_URL = "https://creationeer.co.uk/goforit/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        TextView statusText = findViewById(R.id.status_text);

        prefs.edit()
            .putString(KEY_USER, "shaun")
            .putString(KEY_PASS, "Flash_Robertson")
            .apply();

        Calendar next = scheduleAlarm(this);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM HH:mm", Locale.UK);
        statusText.setText("✓ Ready. Next alarm: " + sdf.format(next.getTime()));

        // Show task screen directly
        findViewById(R.id.test_btn).setOnClickListener(v ->
            startActivity(new Intent(this, TaskAlarmActivity.class)));

        // Test: fire alarm in 10 seconds
        findViewById(R.id.test_10min_btn).setOnClickListener(v -> {
            Calendar in10 = Calendar.getInstance();
            in10.add(Calendar.SECOND, 10);
            scheduleOneOff(this, in10);
            statusText.setText("Test alarm fires in 10 seconds. Lock your screen now.");
            writeLog(this, "Test alarm scheduled for " + in10.getTime());
        });

        // View log
        findViewById(R.id.view_log_btn).setOnClickListener(v -> {
            try {
                File log = new File(getExternalFilesDir(null), "alarm_log.txt");
                if (!log.exists()) { statusText.setText("No log yet"); return; }
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(log));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                statusText.setText(sb.length() > 0 ? sb.toString() : "Log is empty");
            } catch (Exception e) {
                statusText.setText("Error: " + e.getMessage());
            }
        });
    }

    public static Calendar scheduleAlarm(Context context) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        setAlarm(context, calendar, 0);
        writeLog(context, "scheduleAlarm called. Next: " + calendar.getTime());
        return calendar;
    }

    public static void scheduleOneOff(Context context, Calendar when) {
        setAlarm(context, when, 1);
    }

    private static void setAlarm(Context context, Calendar when, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_MORNING);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
    }

    public static void writeLog(Context context, String message) {
        try {
            File log = new File(context.getExternalFilesDir(null), "alarm_log.txt");
            FileWriter fw = new FileWriter(log, true);
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(new Date());
            fw.write(ts + " " + message + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
