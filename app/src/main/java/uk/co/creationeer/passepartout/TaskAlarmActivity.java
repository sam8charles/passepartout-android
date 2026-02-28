package uk.co.creationeer.passepartout;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TaskAlarmActivity extends AppCompatActivity {

    private OkHttpClient client = new OkHttpClient();
    private JSONObject currentTask;
    private String sessionCookie;
    private TextView projectText;
    private TextView taskText;
    private TextView statusText;
    private Button doneBtn;
    private Button radarBtn;
    private Button deleteBtn;
    private Button editBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Wake screen and show over lock screen
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
        statusText  = findViewById(R.id.loading_text);
        doneBtn     = findViewById(R.id.btn_done);
        radarBtn    = findViewById(R.id.btn_radar);
        deleteBtn   = findViewById(R.id.btn_delete);
        editBtn     = findViewById(R.id.btn_edit);

        setButtonsEnabled(false);
        statusText.setText("Connecting...");

        // Login first, then load task
        login();

        doneBtn.setOnClickListener(v -> performAction("done"));
        radarBtn.setOnClickListener(v -> performAction("radar"));
        deleteBtn.setOnClickListener(v -> confirmAndDelete());
        editBtn.setOnClickListener(v -> openEdit());
    }

    @Override
    public void onBackPressed() {
        // Cannot back out — must make a decision
    }

    private void login() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String username = prefs.getString(MainActivity.KEY_USER, "shaun");
        String password = prefs.getString(MainActivity.KEY_PASS, "1234");

        RequestBody body = new FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build();

        Request request = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_login.php")
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> statusText.setText("No internet connection.\nCannot load task."));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body().string();
                // Extract session cookie
                String cookie = response.header("Set-Cookie");
                if (cookie != null && cookie.contains("PHPSESSID=")) {
                    int start = cookie.indexOf("PHPSESSID=");
                    int end   = cookie.indexOf(";", start);
                    sessionCookie = end > 0 ? cookie.substring(start, end) : cookie.substring(start);
                } else {
                    // Try from JSON session field
                    try {
                        JSONObject json = new JSONObject(respBody);
                        if (json.has("session")) {
                            sessionCookie = "PHPSESSID=" + json.getString("session");
                        }
                    } catch (Exception ignored) {}
                }

                if (sessionCookie == null) {
                    runOnUiThread(() -> statusText.setText("Login failed.\nCheck credentials."));
                    return;
                }

                loadTask();
            }
        });
    }

    private void loadTask() {
        runOnUiThread(() -> statusText.setText("Loading task..."));

        Request request = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_api.php?action=getrandomtask")
            .addHeader("Cookie", sessionCookie)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> statusText.setText("Could not load task.\nCheck internet."));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.has("error")) {
                            statusText.setText("Error: " + json.getString("error"));
                            return;
                        }
                        currentTask = json;
                        projectText.setText(json.getString("project_name"));
                        taskText.setText(json.getString("description"));

                        // Set radar button label based on current state
                        boolean onRadar = json.optBoolean("on_radar", false);
                        radarBtn.setText(onRadar ? "📡 Remove from Radar" : "📡 Add to Radar");

                        statusText.setText("What are you doing about this?");
                        setButtonsEnabled(true);
                    } catch (Exception e) {
                        statusText.setText("Parse error: " + body.substring(0, Math.min(body.length(), 80)));
                    }
                });
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        doneBtn.setEnabled(enabled);
        radarBtn.setEnabled(enabled);
        deleteBtn.setEnabled(enabled);
        editBtn.setEnabled(enabled);
    }

    private void performAction(String action) {
        if (currentTask == null) return;
        setButtonsEnabled(false);
        statusText.setText("Saving...");

        try {
            int taskId = currentTask.getInt("task_id");
            Request request = new Request.Builder()
                .url(MainActivity.BASE_URL + "app_api.php?action=" + action + "&task_id=" + taskId)
                .addHeader("Cookie", sessionCookie)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(TaskAlarmActivity.this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
                        setButtonsEnabled(true);
                        statusText.setText("What are you doing about this?");
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(body);
                            if (action.equals("radar")) {
                                // Update button label and reload
                                boolean nowOnRadar = json.optBoolean("on_radar", false);
                                radarBtn.setText(nowOnRadar ? "📡 Remove from Radar" : "📡 Add to Radar");
                                setButtonsEnabled(true);
                                statusText.setText("What are you doing about this?");
                            } else {
                                finish();
                            }
                        } catch (Exception e) {
                            finish();
                        }
                    });
                }
            });
        } catch (Exception e) {
            setButtonsEnabled(true);
        }
    }

    private void confirmAndDelete() {
        // Show confirmation before deleting
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete task?")
            .setMessage("This will permanently remove the task.")
            .setPositiveButton("Delete", (d, w) -> performAction("drop"))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openEdit() {
        if (currentTask == null) return;
        try {
            int taskId = currentTask.getInt("task_id");
            // Open the task in the browser for editing
            String url = MainActivity.BASE_URL + "showproject.php?taskid=" + taskId;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open task", Toast.LENGTH_SHORT).show();
        }
    }
}
