package uk.co.creationeer.passepartout;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

    private static final String CHANNEL_ID       = "passepartout_alarm";
    private static final int    NOTIF_ID_MORNING  = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_BACKBURNER.equals(action)) {
            int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
            if (taskId > 0) {
                showAlarmNotification(context, taskId, NOTIF_ID_MORNING + taskId);
            }
            return;
        }

        // Morning alarm — reschedule for tomorrow, set flag, launch when phone next unlocked
        MainActivity.writeLog(context, "onReceive fired. Action: " + action);
        MainActivity.scheduleAlarm(context);
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
               .edit().putBoolean("alarm_pending", true).apply();
        scheduleBackburnerAlarms(context);
        // Also try direct launch in case phone is already awake/unlocked
        try {
            Intent launch = new Intent(context, TaskAlarmActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
        } catch (Exception ignored) {}
    }

    /**
     * Show a full-screen intent notification that launches TaskAlarmActivity.
     * On locked/sleeping devices this appears as a full-screen activity.
     * On unlocked devices it appears as a heads-up notification.
     * taskId == -1 means random task (morning alarm); taskId > 0 means specific task.
     */
    private void showAlarmNotification(Context context, int taskId, int notifId) {
        ensureChannel(context);

        Intent activityIntent = new Intent(context, TaskAlarmActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (taskId > 0) {
            activityIntent.putExtra(EXTRA_TASK_ID, taskId);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPi = PendingIntent.getActivity(context, notifId, activityIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_MAX);
        }

        builder.setSmallIcon(R.drawable.ic_launcher)
               .setContentTitle("Passepartout")
               .setContentText("Be bothered — take time to think")
               .setAutoCancel(true)
               .setFullScreenIntent(fullScreenPi, true)
               .setContentIntent(fullScreenPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Passepartout Alarm",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Morning task alarm");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setBypassDnd(true);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
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

        Request loginRequest = new Request.Builder()
            .url(MainActivity.BASE_URL + "app_login.php")
            .post(body)
            .build();

        client.newCall(loginRequest).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { /* silent fail */ }

            @Override public void onResponse(Call call, Response response) throws IOException {
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

                                Calendar cal = Calendar.getInstance();
                                cal.set(Calendar.HOUR_OF_DAY, 11);
                                cal.set(Calendar.MINUTE, random.nextInt(300));
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                if (cal.getTimeInMillis() <= System.currentTimeMillis()) continue;

                                Intent bbIntent = new Intent(context, AlarmReceiver.class);
                                bbIntent.setAction(ACTION_BACKBURNER);
                                bbIntent.putExtra(EXTRA_TASK_ID, taskId);

                                PendingIntent pi = PendingIntent.getBroadcast(
                                    context, taskId, bbIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                );

                                try {
                                    alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                                } catch (SecurityException e) {
                                    alarmManager.setAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
    }
}
