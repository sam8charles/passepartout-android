package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

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

    public static final String ACTION_MORNING    = "uk.co.creationeer.passepartout.ALARM";
    public static final String ACTION_BACKBURNER = "uk.co.creationeer.passepartout.BACKBURNER_ALARM";
    public static final String EXTRA_TASK_ID     = "task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            MainActivity.scheduleAlarm(context);
            return;
        }

        if (ACTION_BACKBURNER.equals(action)) {
            int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
            if (taskId > 0) {
                // For backburner, just set flag and start service with task id
                context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                       .edit().putBoolean("alarm_pending", true)
                       .putInt("alarm_task_id", taskId).apply();
                startAlarmService(context);
            }
            return;
        }

        // Morning alarm
        MainActivity.writeLog(context, "onReceive fired. Action: " + action);
        MainActivity.scheduleAlarm(context);
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
               .edit().putBoolean("alarm_pending", true)
               .putInt("alarm_task_id", -1).apply();
        startAlarmService(context);
        scheduleBackburnerAlarms(context);
    }

    private void startAlarmService(Context context) {
        Intent service = new Intent(context, AlarmService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }

    private void scheduleBackburnerAlarms(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String username = prefs.getString(MainActivity.KEY_USER, "shaun");
        String password = prefs.getString(MainActivity.KEY_PASS, "Flash_Robertson");

        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build();
        Request loginReq = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_login.php")
            .post(body)
            .build();

        client.newCall(loginReq).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                String cookie = null;
                String setCookie = response.header("Set-Cookie");
                if (setCookie != null && setCookie.contains("PHPSESSID=")) {
                    int s = setCookie.indexOf("PHPSESSID=");
                    int e = setCookie.indexOf(";", s);
                    cookie = e > 0 ? setCookie.substring(s, e) : setCookie.substring(s);
                }
                if (cookie == null) return;
                final String sessionCookie = cookie;

                Request bbReq = new Request.Builder()
                    .url(MainActivity.BASE_URL + "app_api.php?action=getbackburner")
                    .addHeader("Cookie", sessionCookie)
                    .build();

                client.newCall(bbReq).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {}
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String respBody = response.body().string();
                            JSONArray tasks = new JSONArray(respBody);
                            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                            long now = System.currentTimeMillis();

                            for (int i = 0; i < tasks.length(); i++) {
                                JSONObject task = tasks.getJSONObject(i);
                                int taskId = task.getInt("task_id");
                                String revisitDate = task.getString("revisit_date");

                                String[] parts = revisitDate.split("-");
                                if (parts.length != 3) continue;
                                Calendar cal = Calendar.getInstance();
                                cal.set(Integer.parseInt(parts[2]),
                                        Integer.parseInt(parts[1]) - 1,
                                        Integer.parseInt(parts[0]),
                                        12, 0, 0);
                                cal.set(Calendar.MILLISECOND, 0);
                                if (cal.getTimeInMillis() <= now) continue;

                                Intent bbIntent = new Intent(context, AlarmReceiver.class);
                                bbIntent.setAction(ACTION_BACKBURNER);
                                bbIntent.putExtra(EXTRA_TASK_ID, taskId);
                                PendingIntent pi = PendingIntent.getBroadcast(
                                    context, taskId, bbIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                am.setAlarmClock(new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi), pi);
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
    }
}
