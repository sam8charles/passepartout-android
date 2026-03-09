package uk.co.creationeer.passepartout;

import android.app.DatePickerDialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
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
    private int specificTaskId = -1;

    private TextView projectText, taskText, towardsText, statusText;
    private Button doneBtn, todoSoonBtn, todoLaterBtn, nextStepBtn, editBtn, deleteBtn;
    private LinearLayout nextStepInputLayout;
    private EditText nextStepInput;
    private Button nextStepSaveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.activity_task_alarm);

        projectText         = findViewById(R.id.project_text);
        taskText            = findViewById(R.id.task_text);
        towardsText         = findViewById(R.id.towards_text);
        statusText          = findViewById(R.id.loading_text);
        doneBtn             = findViewById(R.id.btn_done);
        todoSoonBtn         = findViewById(R.id.btn_todosoon);
        todoLaterBtn        = findViewById(R.id.btn_todolater);
        nextStepBtn         = findViewById(R.id.btn_nextstep);
        editBtn             = findViewById(R.id.btn_edit);
        deleteBtn           = findViewById(R.id.btn_delete);
        nextStepInputLayout = findViewById(R.id.nextstep_input_layout);
        nextStepInput       = findViewById(R.id.nextstep_input);
        nextStepSaveBtn     = findViewById(R.id.nextstep_save_btn);

        specificTaskId = getIntent().getIntExtra(AlarmReceiver.ACTION_MORNING.length() > 0
            ? "task_id" : "task_id", -1);

        randomiseButtonOrder();
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
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(nextStepInput, InputMethodManager.SHOW_IMPLICIT);
            } else {
                nextStepInputLayout.setVisibility(View.GONE);
            }
        });
        nextStepSaveBtn.setOnClickListener(v -> saveNextStep());
    }

    @Override
    public void onBackPressed() { /* blocked until decision made */ }

    private void randomiseButtonOrder() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(doneBtn);
        buttons.add(todoSoonBtn);
        buttons.add(todoLaterBtn);
        buttons.add(nextStepBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        Collections.shuffle(buttons, new Random());

        LinearLayout buttonBlock = findViewById(R.id.button_block);
        if (buttonBlock == null) return;
        buttonBlock.removeAllViews();

        for (int i = 0; i < 6; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, 16);
            row.setLayoutParams(rowParams);

            Button b1 = buttons.get(i);
            Button b2 = buttons.get(i + 1);
            if (b1.getParent() != null) ((ViewGroup) b1.getParent()).removeView(b1);
            if (b2.getParent() != null) ((ViewGroup) b2.getParent()).removeView(b2);

            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p1.setMargins(0, 0, 8, 0);
            b1.setLayoutParams(p1);
            b2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(b1);
            row.addView(b2);
            buttonBlock.addView(row);
        }

        // Randomly place buttons above or below task text
        LinearLayout root = findViewById(R.id.root_layout);
        if (root != null && new Random().nextBoolean()) {
            root.removeView(buttonBlock);
            root.addView(buttonBlock, 1);
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        new DatePickerDialog(this, (view, year, month, day) -> {
            String date = String.format("%02d-%02d-%04d", day, month + 1, year);
            performAction("todolater", date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .show();
    }

    private void login() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        RequestBody body = new FormBody.Builder()
            .add("username", prefs.getString(MainActivity.KEY_USER, "shaun"))
            .add("password", prefs.getString(MainActivity.KEY_PASS, "Flash_Robertson"))
            .build();
        client.newCall(new Request.Builder().url(MainActivity.BASE_URL + "app_login.php").post(body).build())
            .enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> statusText.setText("No internet connection."));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body().string();
                    String cookie = response.header("Set-Cookie");
                    if (cookie != null && cookie.contains("PHPSESSID=")) {
                        int s = cookie.indexOf("PHPSESSID="), e = cookie.indexOf(";", s);
                        sessionCookie = e > 0 ? cookie.substring(s, e) : cookie.substring(s);
                    } else {
                        try {
                            JSONObject j = new JSONObject(respBody);
                            if (j.has("session")) sessionCookie = "PHPSESSID=" + j.getString("session");
                        } catch (Exception ignored) {}
                    }
                    if (sessionCookie == null) { runOnUiThread(() -> statusText.setText("Login failed.")); return; }
                    loadTask();
                }
            });
    }

    private void loadTask() {
        runOnUiThread(() -> statusText.setText("Loading task..."));
        String url = specificTaskId > 0
            ? MainActivity.BASE_URL + "app_api.php?action=gettask&task_id=" + specificTaskId
            : MainActivity.BASE_URL + "app_api.php?action=getrandomtask";
        client.newCall(new Request.Builder().url(url).addHeader("Cookie", sessionCookie).build())
            .enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> statusText.setText("Could not load task."));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(body);
                            if (json.has("error")) { statusText.setText("Error: " + json.getString("error")); return; }
                            currentTask = json;
                            projectText.setText(json.getString("project_name"));
                            taskText.setText(json.getString("description"));
                            String pd = json.optString("parent_description", "");
                            towardsText.setVisibility(pd.isEmpty() ? View.GONE : View.VISIBLE);
                            if (!pd.isEmpty()) towardsText.setText("towards: " + pd);
                            updateTodoButtons(json.optBoolean("on_radar", false));
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
        todoSoonBtn.setAlpha(onTodoSoon ? 0.4f : 1.0f);
        todoSoonBtn.setEnabled(!onTodoSoon);
        todoLaterBtn.setAlpha(1.0f);
        todoLaterBtn.setEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        doneBtn.setEnabled(enabled);
        todoSoonBtn.setEnabled(enabled && todoSoonBtn.getAlpha() > 0.5f);
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
            client.newCall(new Request.Builder().url(url).addHeader("Cookie", sessionCookie).build())
                .enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> { Toast.makeText(TaskAlarmActivity.this, "Failed. Try again.", Toast.LENGTH_SHORT).show(); setButtonsEnabled(true); statusText.setText("Be bothered - take time to think"); });
                    }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        runOnUiThread(() -> { decisionMade = true; dismiss(); });
                    }
                });
        } catch (Exception e) { setButtonsEnabled(true); }
    }

    private void saveNextStep() {
        String desc = nextStepInput.getText().toString().trim();
        if (desc.isEmpty()) { Toast.makeText(this, "Please describe the next step", Toast.LENGTH_SHORT).show(); return; }
        if (currentTask == null) return;
        nextStepSaveBtn.setEnabled(false);
        try {
            int taskId = currentTask.getInt("task_id");
            String url = MainActivity.BASE_URL + "app_api.php?action=nextstep&task_id=" + taskId + "&description=" + Uri.encode(desc);
            client.newCall(new Request.Builder().url(url).addHeader("Cookie", sessionCookie).build())
                .enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> { Toast.makeText(TaskAlarmActivity.this, "Failed.", Toast.LENGTH_SHORT).show(); nextStepSaveBtn.setEnabled(true); });
                    }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        runOnUiThread(() -> { decisionMade = true; dismiss(); });
                    }
                });
        } catch (Exception e) { nextStepSaveBtn.setEnabled(true); }
    }

    private void confirmAndDelete() {
        new AlertDialog.Builder(this)
            .setTitle("Delete task?").setMessage("This will permanently remove the task.")
            .setPositiveButton("Delete", (d, w) -> performAction("drop", null))
            .setNegativeButton("Cancel", null).show();
    }

    private void openEdit() {
        if (currentTask == null) return;
        try {
            int taskId = currentTask.getInt("task_id");
            int projectId = currentTask.getInt("project_id");
            String url = MainActivity.BASE_URL + "showproject.php?id=" + projectId + "&taskid=" + taskId + "#task" + taskId;
            decisionMade = true;
            dismiss();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) { Toast.makeText(this, "Could not open task", Toast.LENGTH_SHORT).show(); }
    }

    private void dismiss() {
        // Cancel the notification
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(AlarmReceiver.NOTIF_ID);
        finish();
    }
}
