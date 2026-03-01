package uk.co.creationeer.passepartout;

import android.app.DatePickerDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Calendar;
import java.util.Random;
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
    private boolean decisionMade = false;

    private TextView projectText;
    private TextView taskText;
    private TextView towardsText;
    private TextView statusText;
    private Button doneBtn;
    private Button todoSoonBtn;
    private Button todoLaterBtn;
    private Button nextStepBtn;
    private Button editBtn;
    private Button deleteBtn;
    private LinearLayout nextStepInputLayout;
    private EditText nextStepInput;
    private Button nextStepSaveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        projectText        = findViewById(R.id.project_text);
        taskText           = findViewById(R.id.task_text);
        towardsText        = findViewById(R.id.towards_text);
        statusText         = findViewById(R.id.loading_text);
        doneBtn            = findViewById(R.id.btn_done);
        todoSoonBtn        = findViewById(R.id.btn_todosoon);
        todoLaterBtn       = findViewById(R.id.btn_todolater);
        nextStepBtn        = findViewById(R.id.btn_nextstep);
        editBtn            = findViewById(R.id.btn_edit);
        deleteBtn          = findViewById(R.id.btn_delete);
        nextStepInputLayout = findViewById(R.id.nextstep_input_layout);
        nextStepInput      = findViewById(R.id.nextstep_input);
        nextStepSaveBtn    = findViewById(R.id.nextstep_save_btn);

        randomiseButtonPosition();

        setButtonsEnabled(false);
        statusText.setText("Connecting...");

        login();

        doneBtn.setOnClickListener(v -> performAction("done", null));
        editBtn.setOnClickListener(v -> openEdit());
        deleteBtn.setOnClickListener(v -> confirmAndDelete());

        todoSoonBtn.setOnClickListener(v -> performAction("todosoon", null));

        todoLaterBtn.setOnClickListener(v -> showDatePicker());

        nextStepBtn.setOnClickListener(v -> {
            if (nextStepInputLayout.getVisibility() == View.GONE) {
                nextStepInputLayout.setVisibility(View.VISIBLE);
                nextStepInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(nextStepInput, InputMethodManager.SHOW_IMPLICIT);
            } else {
                nextStepInputLayout.setVisibility(View.GONE);
            }
        });

        nextStepSaveBtn.setOnClickListener(v -> saveNextStep());
    }

    @Override
    public void onBackPressed() { /* Cannot back out */ }

    @Override
    protected void onStop() {
        super.onStop();
        if (!decisionMade && currentTask != null) {
            Intent intent = new Intent(this, TaskAlarmActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getApplicationContext().startActivity(intent);
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        // Default to 1 week from now
        cal.add(Calendar.DAY_OF_MONTH, 7);
        DatePickerDialog picker = new DatePickerDialog(
            this,
            (view, year, month, day) -> {
                String date = String.format("%02d-%02d-%04d", day, month + 1, year);
                performAction("todolater", date);
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        );
        picker.setTitle("Revisit this task from...");
        picker.getDatePicker().setMinDate(System.currentTimeMillis() + 86400000L); // min tomorrow
        picker.show();
    }

    private void randomiseButtonPosition() {
        LinearLayout root = findViewById(R.id.root_layout);
        LinearLayout buttonBlock = findViewById(R.id.button_block);
        if (root == null || buttonBlock == null) return;

        // 0 = all below (default), 1 = all above, 2 = split
        int choice = new Random().nextInt(3);

        if (choice == 0) {
            // Default: buttons already at bottom, nothing to do
            return;
        }

        if (choice == 1) {
            // Move entire button block to just after the PASSEPARTOUT title
            root.removeView(buttonBlock);
            root.addView(buttonBlock, 1);
            return;
        }

        // choice == 2: split - rows 1 and 2 above, row 3 below
        if (buttonBlock.getChildCount() < 3) return;

        LinearLayout topBlock = new LinearLayout(this);
        topBlock.setOrientation(LinearLayout.VERTICAL);
        android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        topBlock.setLayoutParams(lp);
        topBlock.setPadding(40, 16, 40, 0);

        android.view.View row0 = buttonBlock.getChildAt(0);
        android.view.View row1 = buttonBlock.getChildAt(1);
        buttonBlock.removeView(row0);
        buttonBlock.removeView(row1);
        topBlock.addView(row0);
        topBlock.addView(row1);

        root.addView(topBlock, 1);
    }

    private void login() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String username = prefs.getString(MainActivity.KEY_USER, "shaun");
        String password = prefs.getString(MainActivity.KEY_PASS, "Flash_Robertson");

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
                runOnUiThread(() -> statusText.setText("No internet connection."));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body().string();
                String cookie = response.header("Set-Cookie");
                if (cookie != null && cookie.contains("PHPSESSID=")) {
                    int start = cookie.indexOf("PHPSESSID=");
                    int end   = cookie.indexOf(";", start);
                    sessionCookie = end > 0 ? cookie.substring(start, end) : cookie.substring(start);
                } else {
                    try {
                        JSONObject json = new JSONObject(respBody);
                        if (json.has("session")) {
                            sessionCookie = "PHPSESSID=" + json.getString("session");
                        }
                    } catch (Exception ignored) {}
                }
                if (sessionCookie == null) {
                    runOnUiThread(() -> statusText.setText("Login failed."));
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
                runOnUiThread(() -> statusText.setText("Could not load task."));
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

                        String parentDesc = json.optString("parent_description", "");
                        if (!parentDesc.isEmpty()) {
                            towardsText.setText("towards: " + parentDesc);
                            towardsText.setVisibility(View.VISIBLE);
                        } else {
                            towardsText.setVisibility(View.GONE);
                        }

                        // Set Todo Soon / Todo Later button states
                        boolean onTodoSoon = json.optBoolean("on_radar", false);
                        updateTodoButtons(onTodoSoon);

                        statusText.setText("Be bothered - take time to think");
                        setButtonsEnabled(true);
                    } catch (Exception e) {
                        statusText.setText("Error: " + body.substring(0, Math.min(body.length(), 80)));
                    }
                });
            }
        });
    }

    private void updateTodoButtons(boolean onTodoSoon) {
        if (onTodoSoon) {
            todoSoonBtn.setAlpha(0.4f);
            todoSoonBtn.setEnabled(false);
            todoLaterBtn.setAlpha(1.0f);
            todoLaterBtn.setEnabled(true);
        } else {
            todoSoonBtn.setAlpha(1.0f);
            todoSoonBtn.setEnabled(true);
            todoLaterBtn.setAlpha(0.4f);
            todoLaterBtn.setEnabled(true); // always allow Todo Later
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        doneBtn.setEnabled(enabled);
        todoSoonBtn.setEnabled(enabled);
        todoLaterBtn.setEnabled(enabled);
        nextStepBtn.setEnabled(enabled);
        editBtn.setEnabled(enabled);
        deleteBtn.setEnabled(enabled);
    }

    private void performAction(String action, String date) {
        if (currentTask == null) return;
        setButtonsEnabled(false);
        statusText.setText("Saving...");

        try {
            int taskId = currentTask.getInt("task_id");
            String url = MainActivity.BASE_URL + "app_api.php?action=" + action + "&task_id=" + taskId;
            if (date != null) url += "&date=" + Uri.encode(date);

            Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", sessionCookie)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(TaskAlarmActivity.this, "Failed. Try again.", Toast.LENGTH_SHORT).show();
                        setButtonsEnabled(true);
                        statusText.setText("Be bothered - take time to think");
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        decisionMade = true;
                        finish();
                    });
                }
            });
        } catch (Exception e) {
            setButtonsEnabled(true);
        }
    }

    private void saveNextStep() {
        String description = nextStepInput.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Please describe the next step", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentTask == null) return;
        nextStepSaveBtn.setEnabled(false);

        try {
            int taskId = currentTask.getInt("task_id");
            String url = MainActivity.BASE_URL + "app_api.php?action=nextstep&task_id=" + taskId
                + "&description=" + Uri.encode(description);

            Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", sessionCookie)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(TaskAlarmActivity.this, "Failed. Try again.", Toast.LENGTH_SHORT).show();
                        nextStepSaveBtn.setEnabled(true);
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> { decisionMade = true; finish(); });
                }
            });
        } catch (Exception e) {
            nextStepSaveBtn.setEnabled(true);
        }
    }

    private void confirmAndDelete() {
        new AlertDialog.Builder(this)
            .setTitle("Delete task?")
            .setMessage("This will permanently remove the task.")
            .setPositiveButton("Delete", (d, w) -> performAction("drop", null))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openEdit() {
        if (currentTask == null) return;
        try {
            int taskId    = currentTask.getInt("task_id");
            int projectId = currentTask.getInt("project_id");
            String url = MainActivity.BASE_URL + "showproject.php?id=" + projectId + "#task" + taskId;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            decisionMade = true;
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open task", Toast.LENGTH_SHORT).show();
        }
    }
}
