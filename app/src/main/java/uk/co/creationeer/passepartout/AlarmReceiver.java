package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
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

public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_MORNING  = "uk.co.creationeer.passepartout.ALARM";
    public static final String ACTION_BACKBURNER = "uk.co.creationeer.passepartout.BACKBURNER_ALARM";
    public static final String EXTRA_TASK_ID   = "task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_BACKBURNER.equals(action)) {
            // Specific backburner task alarm — get the task_id and show it
            int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
            if (taskId > 0) {
                Intent taskIntent = new Intent(context, TaskAlarmActivity.class);
                taskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                taskIntent.putExtra(EXTRA_TASK_ID, taskId);
                context.startActivity(taskIntent);
            }
            return;
        }

        // Morning alarm (ACTION_MORNING or BOOT_COMPLETED)
        // Reschedule the morning alarm for tomorrow
        MainActivity.scheduleAlarm(context);

        // Launch the regular full-screen task activity
        Intent taskIntent = new Intent(context, TaskAlarmActivity.class);
        taskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(taskIntent);

        // Also check for backburner tasks coming off today and schedule alarms for them
        scheduleBackburnerAlarms(context);
    }

    /**
     * Log in to the web app, fetch tasks whose t_c_date is today,
     * and schedule one alarm per task at a random time between 11:00 and 16:00.
     */
    private void scheduleBackburnerAlarms(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String username = prefs.getString(MainActivity.KEY_USER, "shaun");
        String password = prefs.getString(MainActivity.KEY_PASS, "Flash_Robertson");

        OkHttpClient client = new OkHttpClient();

        RequestBody body = new FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build();

        Request loginRequest = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_login.php")
            .post(body)
            .build();

        client.newCall(loginRequest).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { /* silent fail */ }

            @Override public void onResponse(Call call, Response response) throws IOException {
                // Extract session cookie
                String sessionCookie = null;
                String cookie = response.header("Set-Cookie");
                if (cookie != null && cookie.contains("PHPSESSID=")) {
                    int start = cookie.indexOf("PHPSESSID=");
                    int end = cookie.indexOf(";", start);
                    sessionCookie = end > 0 ? cookie.substring(start, end) : cookie.substring(start);
                } else {
                    try {
                        String respBody = response.body().string();
                        JSONObject json = new JSONObject(respBody);
                        if (json.has("session")) {
                            sessionCookie = "PHPSESSID=" + json.getString("session");
                        }
                    } catch (Exception ignored) {}
                }

                if (sessionCookie == null) return;

                final String finalCookie = sessionCookie;

                Request apiRequest = new Request.Builder()
                    .url(MainActivity.BASE_URL + "app_api.php?action=getbackburnertasks")
                    .addHeader("Cookie", finalCookie)
                    .build();

                client.newCall(apiRequest).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) { /* silent fail */ }

                    @Override public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String respBody = response.body().string();
                            JSONArray tasks = new JSONArray(respBody);
                            Random random = new Random();
                            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

                            for (int i = 0; i < tasks.length(); i++) {
                                JSONObject task = tasks.getJSONObject(i);
                                int taskId = task.getInt("task_id");

                                // Random time between 11:00 and 16:00 (300 minutes window)
                                Calendar cal = Calendar.getInstance();
                                cal.set(Calendar.HOUR_OF_DAY, 11);
                                cal.set(Calendar.MINUTE, random.nextInt(300)); // 0..299 minutes = 11:00..15:59
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                // If we've already passed this time today (unlikely at 6am but safe), skip
                                if (cal.getTimeInMillis() <= System.currentTimeMillis()) continue;

                                Intent bbIntent = new Intent(context, AlarmReceiver.class);
                                bbIntent.setAction(ACTION_BACKBURNER);
                                bbIntent.putExtra(EXTRA_TASK_ID, taskId);

                                // Use taskId as requestCode so each task gets its own PendingIntent
                                PendingIntent pi = PendingIntent.getBroadcast(
                                    context,
                                    taskId,
                                    bbIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                );

                                try {
                                    alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        cal.getTimeInMillis(),
                                        pi
                                    );
                                } catch (SecurityException e) {
                                    alarmManager.setAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        cal.getTimeInMillis(),
                                        pi
                                    );
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
    }
}
