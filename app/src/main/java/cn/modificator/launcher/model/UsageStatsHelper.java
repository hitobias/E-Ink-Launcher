package cn.modificator.launcher.model;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

/**
 * 应用使用统计查询辅助：今日打开次数 + 总使用时长（毫秒）。
 * 需要 PACKAGE_USAGE_STATS 权限；用 {@link AppSortComparator#hasUsageStatsPermission(Context)} 检查。
 */
public final class UsageStatsHelper {

  public static final class Snapshot {
    public final long todayForegroundMs;
    public final int todayLaunchCount;
    public final long totalForegroundMs;
    public final long lastUsedMs;

    public Snapshot(long todayForegroundMs, int todayLaunchCount,
                    long totalForegroundMs, long lastUsedMs) {
      this.todayForegroundMs = todayForegroundMs;
      this.todayLaunchCount = todayLaunchCount;
      this.totalForegroundMs = totalForegroundMs;
      this.lastUsedMs = lastUsedMs;
    }

    public static Snapshot empty() {
      return new Snapshot(0, 0, 0, 0);
    }
  }

  private UsageStatsHelper() {
  }

  public static Snapshot query(Context context, String packageName) {
    if (context == null || packageName == null) return Snapshot.empty();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return Snapshot.empty();
    UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    if (usm == null) return Snapshot.empty();

    long now = System.currentTimeMillis();

    // 今日范围 = 今天 0:00 至现在
    Calendar dayStart = Calendar.getInstance();
    dayStart.set(Calendar.HOUR_OF_DAY, 0);
    dayStart.set(Calendar.MINUTE, 0);
    dayStart.set(Calendar.SECOND, 0);
    dayStart.set(Calendar.MILLISECOND, 0);
    long todayStart = dayStart.getTimeInMillis();

    long todayForeground = 0;
    int todayLaunches = 0;
    try {
      List<UsageStats> today = usm.queryUsageStats(
          UsageStatsManager.INTERVAL_DAILY, todayStart, now);
      if (today != null) {
        for (UsageStats s : today) {
          if (!packageName.equals(s.getPackageName())) continue;
          todayForeground += s.getTotalTimeInForeground();
          // launchCount 是 API 28+
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
              todayLaunches += extractLaunchCount(s);
            } catch (Throwable ignored) {
            }
          }
        }
      }
    } catch (Exception ignored) {
    }

    long totalForeground = 0;
    long lastUsed = 0;
    try {
      List<UsageStats> all = usm.queryUsageStats(
          UsageStatsManager.INTERVAL_MONTHLY,
          now - 90L * 24 * 60 * 60 * 1000, now);
      if (all != null) {
        for (UsageStats s : all) {
          if (!packageName.equals(s.getPackageName())) continue;
          totalForeground += s.getTotalTimeInForeground();
          lastUsed = Math.max(lastUsed, s.getLastTimeUsed());
        }
      }
    } catch (Exception ignored) {
    }

    return new Snapshot(todayForeground, todayLaunches, totalForeground, lastUsed);
  }

  /** API 28+: UsageStats#getAppLaunchCount() —— hidden 方法，用反射防止编译报错。 */
  private static int extractLaunchCount(UsageStats s) {
    try {
      java.lang.reflect.Method m = s.getClass().getMethod("getAppLaunchCount");
      Object r = m.invoke(s);
      return r instanceof Integer ? (Integer) r : 0;
    } catch (Throwable ignored) {
      return 0;
    }
  }
}
