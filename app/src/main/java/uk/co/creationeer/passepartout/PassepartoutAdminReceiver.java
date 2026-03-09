package uk.co.creationeer.passepartout;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class PassepartoutAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {}
    @Override
    public void onDisabled(Context context, Intent intent) {}
}
