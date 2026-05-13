package cn.modificator.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知监听服务：维护"每个包名当前活动通知数"的内存快照。
 * <p>
 * 桌面通过 {@link #getCount(String)} 读取计数，通过 {@link #register(Listener)} 订阅变更。
 * 跳过持续型 / 前台服务通知（{@link StatusBarNotification#isOngoing()}），
 * 避免把不可消除的状态条目计入角标。
 */
public class NotificationCounter extends NotificationListenerService {

  /** 包名 -> 活动通知条数。仅在服务连接期间有效；服务未连接时全为 0。 */
  private static final ConcurrentMap<String, Integer> COUNTS = new ConcurrentHashMap<>();

  private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

  /** 当 {@link #COUNTS} 有任何变动时回调。 */
  public interface Listener {
    void onCountsChanged();
  }

  // =========================================================================
  // 静态查询/订阅 API
  // =========================================================================

  /** 返回该包的活动通知数；服务未连接或无通知时返回 0。 */
  public static int getCount(String pkg) {
    if (pkg == null) return 0;
    Integer v = COUNTS.get(pkg);
    return v != null ? v : 0;
  }

  public static void register(Listener l) {
    if (l != null) LISTENERS.addIfAbsent(l);
  }

  public static void unregister(Listener l) {
    if (l != null) LISTENERS.remove(l);
  }

  /** 是否已被系统授予通知访问权限。 */
  public static boolean hasAccess(Context ctx) {
    if (ctx == null) return false;
    try {
      return NotificationManagerCompat.getEnabledListenerPackages(ctx)
          .contains(ctx.getPackageName());
    } catch (Exception e) {
      return false;
    }
  }

  /** 构造跳转"通知访问"系统设置页的 Intent。 */
  public static Intent buildSettingsIntent() {
    Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return i;
  }

  // =========================================================================
  // 服务回调：维护 COUNTS
  // =========================================================================

  @Override
  public void onListenerConnected() {
    super.onListenerConnected();
    rebuildSnapshot();
    notifyListeners();
  }

  @Override
  public void onListenerDisconnected() {
    super.onListenerDisconnected();
    COUNTS.clear();
    notifyListeners();
  }

  @Override
  public void onNotificationPosted(StatusBarNotification sbn) {
    if (sbn == null) return;
    rebuildSnapshot();
    notifyListeners();
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification sbn) {
    if (sbn == null) return;
    rebuildSnapshot();
    notifyListeners();
  }

  // =========================================================================
  // 内部辅助
  // =========================================================================

  /**
   * 从当前活动通知重建快照。
   * 每次变更都全量重建：避免增量计数因 group summary / ongoing 切换而漂移。
   */
  private void rebuildSnapshot() {
    StatusBarNotification[] active;
    try {
      active = getActiveNotifications();
    } catch (SecurityException | NullPointerException e) {
      // 服务在 onListenerConnected 前调用会抛 NPE；这里直接清空。
      COUNTS.clear();
      return;
    }
    if (active == null) {
      COUNTS.clear();
      return;
    }
    Map<String, Integer> next = new HashMap<>();
    for (StatusBarNotification sbn : active) {
      if (sbn == null) continue;
      if (sbn.isOngoing()) continue;
      String pkg = sbn.getPackageName();
      if (pkg == null) continue;
      Integer cur = next.get(pkg);
      next.put(pkg, cur == null ? 1 : cur + 1);
    }
    COUNTS.clear();
    COUNTS.putAll(next);
  }

  private static void notifyListeners() {
    for (Listener l : LISTENERS) {
      try {
        l.onCountsChanged();
      } catch (Exception ignored) {
        // 监听器异常不应影响其他订阅者。
      }
    }
  }
}
