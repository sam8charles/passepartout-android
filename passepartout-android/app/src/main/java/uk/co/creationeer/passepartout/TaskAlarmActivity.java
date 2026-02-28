package uk.co.creationeer.passepartout;

import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TaskAlarmActivity extends AppCompatActivity {

    private OkHttpClient client = new OkHttpClient();
    private String sessionCookie;
    private JSONObject currentTask;
    private TextView projectText;
    private TextView taskText;
    private TextView loadingText;
    private Button doneBtn;
    private Button radarBtn;
    private Button snoozeBtn;
    private Button dropBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Wake the screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_task_alarm);

        projectText = findViewById(R.id.project_text);
        taskText    = findViewById(R.id.task_text);
        loadingText = findViewById(R.id.loading_text);
        doneBtn     = findViewById(R.id.btn_done);
        radarBtn    = findViewById(R.id.btn_radar);
        snoozeBtn   = findViewById(R.id.btn_snooze);
        dropBtn     = findViewById(R.id.btn_drop);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sessionCookie = "PHPSESSID=" + prefs.getString(MainActivity.KEY_SESSION, "");

        // Disable buttons until task loaded
        setButtonsEnabled(false);

        loadTask();

        doneBtn.setOnClickListener(v -> performAction("done"));
        radarBtn.setOnClickListener(v -> performAction("radar"));
        snoozeBtn.setOnClickListener(v -> finish()); // just dismiss - task stays
        dropBtn.setOnClickListener(v -> performAction("drop"));
    }

    @Override
    public void onBackPressed() {
        // Do nothing - cannot back out
    }

    private void loadTask() {
        loadingText.setText("Loading your task...");

        Request request = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_api.php?action=getrandomtask")
            .addHeader("Cookie", sessionCookie)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loadingText.setText("Could not connect. Check your internet.");
                    snoozeBtn.setEnabled(true); // allow dismiss on error
                });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.has("error")) {
                            loadingText.setText("Error: " + json.getString("error") + "\nCheck your session ID in settings.");
                            snoozeBtn.setEnabled(true);
                            return;
                        }
                        currentTask = json;
                        projectText.setText(json.getString("project_name"));
                        taskText.setText(json.getString("description"));
                        loadingText.setText("What are you doing about this?");
                        setButtonsEnabled(true);
                    } catch (Exception e) {
                        loadingText.setText("Error parsing response:\n" + body.substring(0, Math.min(body.length(), 100)));
                        snoozeBtn.setEnabled(true);
                    }
                });
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        doneBtn.setEnabled(enabled);
        radarBtn.setEnabled(enabled);
        snoozeBtn.setEnabled(enabled);
        dropBtn.setEnabled(enabled);
    }

    private void performAction(String action) {
        if (currentTask == null) return;
        setButtonsEnabled(false);

        try {
            int taskId = currentTask.getInt("task_id");
            String url = MainActivity.BASE_URL + "app_api.php?action=" + action + "&task_id=" + taskId;

            Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", sessionCookie)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(TaskAlarmActivity.this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
                        setButtonsEnabled(true);
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> finish());
                }
            });
        } catch (Exception e) {
            setButtonsEnabled(true);
        }
    }
}
