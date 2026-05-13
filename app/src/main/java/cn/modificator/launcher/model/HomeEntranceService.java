package cn.modificator.launcher.model;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import cn.modificator.launcher.Launcher;
import cn.modificator.launcher.R;

/**
 * 常驻通知服务：在 Home 键无法重映射的设备上，通过通知栏入口返回 Launcher。
 * 仅在 {@code Launcher} 检测到特定设备时由前台启动；本服务不再自重启，
 * 避免触发 Android 12+ 的后台启动限制。
 */
public class HomeEntranceService extends Service {

  private static final String CHANNEL_ID = "back_to_launcher";
  private static final int NOTIFICATION_ID = 1;

  @Override
  public void onCreate() {
    super.onCreate();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null) {
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_click_back_launcher),
            NotificationManager.IMPORTANCE_LOW));
      }
    }

    Intent launchIntent = new Intent(this, Launcher.class)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    PendingIntent contentIntent = PendingIntent.getActivity(
        this, 10, launchIntent, PendingIntent.FLAG_IMMUTABLE);

    Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        ? new Notification.Builder(this, CHANNEL_ID)
        : new Notification.Builder(this);

    Notification notification = builder
        .setWhen(System.currentTimeMillis())
        .setContentTitle(getString(R.string.notification_click_back_launcher))
        .setContentIntent(contentIntent)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .build();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(NOTIFICATION_ID, notification);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
