package cn.modificator.launcher.model;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.Launcher;
import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;

/**
 * 全螢幕懸浮 Home 按鈕。
 *
 * <p>動機：Supernote 把右側滑條手勢 hardwire 到原廠 SupernoteLauncher 的
 * component name，禁用後該手勢失效，部分系統頁面也無 nav 可回桌面。
 * 本服務用 SYSTEM_ALERT_WINDOW 加一顆可拖拽的小圓鈕，任何畫面下點擊
 * 即發 ACTION_MAIN+CATEGORY_HOME，回到當前 HOME launcher（即本 app）。
 *
 * <p>權限：
 * <ul>
 *   <li>SYSTEM_ALERT_WINDOW（Android 6+ 需經 {@link Settings#canDrawOverlays}）</li>
 *   <li>FOREGROUND_SERVICE_SPECIAL_USE（Android 14+）</li>
 * </ul>
 *
 * <p>位置持久化：拖拽結束時把 x/y 寫回 Config，下次啟動還原。
 */
public final class FloatingHomeService extends Service {

  private static final String TAG = "FloatingHome";
  private static final String CHANNEL_ID = "floating_home";
  private static final int NOTIF_ID = 0x484F4D45; // "HOME"
  /** 拖拽閾值：移動超過 dp 視為拖拽，否則視為點擊。 */
  private static final int DRAG_THRESHOLD_DP = 6;

  private WindowManager windowManager;
  private View overlay;
  private WindowManager.LayoutParams params;
  private Config config;
  private int dragThresholdPx;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    config = new Config(this);
    dragThresholdPx = Utils.dp2Px(this, DRAG_THRESHOLD_DP);
    startInForeground();
    if (!canDrawOverlays(this)) {
      Log.w(TAG, "Overlay permission missing; stopping service.");
      stopSelf();
      return;
    }
    attachOverlay();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // 服務被系統殺掉後 START_NOT_STICKY：用戶可從設定重新開啟，避免無限自啟。
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    detachOverlay();
    super.onDestroy();
  }

  // ---------------------------------------------------------------------------
  // Overlay
  // ---------------------------------------------------------------------------

  private void attachOverlay() {
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    if (windowManager == null) {
      stopSelf();
      return;
    }
    ImageView button = new ImageView(this);
    int sizeDp = config.getFloatingHomeSizeDp();
    int sizePx = Utils.dp2Px(this, sizeDp);
    int padPx = Utils.dp2Px(this, sizeDp / 4);
    button.setImageResource(R.drawable.ic_home_overlay);
    button.setBackgroundResource(R.drawable.floating_home_bg);
    button.setPadding(padPx, padPx, padPx, padPx);
    button.setContentDescription(getString(R.string.floating_home_label));

    params = new WindowManager.LayoutParams(
        sizePx,
        sizePx,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.START | Gravity.TOP;
    params.x = config.getFloatingHomeX();
    params.y = config.getFloatingHomeY();
    if (params.y < 0) {
      // 首次使用：放在螢幕右側中間，比起 0,0 更不擋內容。
      params.x = getResources().getDisplayMetrics().widthPixels - sizePx - Utils.dp2Px(this, 8);
      params.y = getResources().getDisplayMetrics().heightPixels / 3;
    }

    button.setOnTouchListener(new DragTapListener());

    try {
      windowManager.addView(button, params);
      overlay = button;
    } catch (Exception e) {
      Log.e(TAG, "Failed to add overlay", e);
      stopSelf();
    }
  }

  private void detachOverlay() {
    if (overlay != null && windowManager != null) {
      try {
        windowManager.removeView(overlay);
      } catch (Exception ignored) {
      }
    }
    overlay = null;
  }

  private static int overlayType() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE;
  }

  /** ACTION_DOWN/MOVE/UP 區分拖拽與點擊。 */
  private final class DragTapListener implements View.OnTouchListener {
    private int startX, startY;
    private float startTouchX, startTouchY;
    private boolean dragging;

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
      switch (ev.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          startX = params.x;
          startY = params.y;
          startTouchX = ev.getRawX();
          startTouchY = ev.getRawY();
          dragging = false;
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dx = ev.getRawX() - startTouchX;
          float dy = ev.getRawY() - startTouchY;
          if (!dragging
              && (Math.abs(dx) > dragThresholdPx || Math.abs(dy) > dragThresholdPx)) {
            dragging = true;
          }
          if (dragging) {
            params.x = startX + (int) dx;
            params.y = startY + (int) dy;
            try {
              windowManager.updateViewLayout(overlay, params);
            } catch (Exception ignored) {
            }
          }
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (dragging) {
            // 持久化位置；不夾邊以便用戶可放到角落。
            config.setFloatingHomePosition(params.x, params.y);
          } else {
            sendHomeIntent();
          }
          return true;
      }
      return false;
    }
  }

  private void sendHomeIntent() {
    Intent home = new Intent(Intent.ACTION_MAIN);
    home.addCategory(Intent.CATEGORY_HOME);
    home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(home);
    } catch (Exception e) {
      Log.e(TAG, "Failed to launch HOME intent", e);
    }
  }

  // ---------------------------------------------------------------------------
  // Foreground notification
  // ---------------------------------------------------------------------------

  private void startInForeground() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel ch = new NotificationChannel(
          CHANNEL_ID,
          getString(R.string.floating_home_label),
          NotificationManager.IMPORTANCE_MIN);
      ch.setShowBadge(false);
      nm.createNotificationChannel(ch);
    }
    Intent open = new Intent(this, Launcher.class)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi = PendingIntent.getActivity(this, 0, open,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notif = new Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_home_overlay)
        .setContentTitle(getString(R.string.floating_home_running))
        .setContentText(getString(R.string.floating_home_running_summary))
        .setContentIntent(pi)
        .setOngoing(true)
        .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIF_ID, notif,
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(NOTIF_ID, notif);
    }
  }

  // ---------------------------------------------------------------------------
  // Public helpers
  // ---------------------------------------------------------------------------

  public static boolean canDrawOverlays(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Settings.canDrawOverlays(context);
    }
    return true;
  }

  /**
   * 啟動懸浮 Home。呼叫前應確認 {@link #canDrawOverlays(Context)} 為 true。
   */
  public static void start(Context context) {
    Intent svc = new Intent(context.getApplicationContext(), FloatingHomeService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.getApplicationContext().startForegroundService(svc);
    } else {
      context.getApplicationContext().startService(svc);
    }
  }

  public static void stop(Context context) {
    Intent svc = new Intent(context.getApplicationContext(), FloatingHomeService.class);
    context.getApplicationContext().stopService(svc);
  }
}
