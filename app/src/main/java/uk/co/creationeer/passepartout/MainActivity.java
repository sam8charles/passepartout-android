package uk.co.creationeer.passepartout;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final String PREFS    = "passepartout";
    static final String KEY_USER = "username";
    static final String KEY_PASS = "password";
    static final String BASE_URL = "https://creationeer.co.uk/goforit/";

    private static final int REQUEST_ADMIN = 1;

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

        // Request device admin if not already granted
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, PassepartoutAdminReceiver.class);
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Passepartout needs this to keep the task screen visible until you make a decision.");
            startActivityForResult(intent, REQUEST_ADMIN);
        }

        // Start permanent foreground service
        startAlarmService(this);

        statusText.setText("✓ Running. Task screen appears after 12:00 each day.");

        // Show task screen directly
        findViewById(R.id.test_btn).setOnClickListener(v ->
            startActivity(new Intent(this, TaskAlarmActivity.class)));

        // Force today's decision screen (for testing)
        findViewById(R.id.test_10min_btn).setOnClickListener(v -> {
            // Clear today's decision so it shows again
            prefs.edit().remove("decided_" + AlarmService.getTodayKey()).apply();
            startActivity(new Intent(this, TaskAlarmActivity.class));
            statusText.setText("Today's decision reset — task screen launched.");
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

    public static void startAlarmService(Context context) {
        Intent service = new Intent(context, AlarmService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
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
