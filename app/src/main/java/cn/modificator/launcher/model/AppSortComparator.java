package cn.modificator.launcher.model;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应用排序比较器。
 * <p>
 * 排序模式常量：
 * <ul>
 *   <li>0 - 名称正序</li>
 *   <li>1 - 名称逆序</li>
 *   <li>2 - 安装时间正序（最早优先）</li>
 *   <li>3 - 安装时间逆序（最新优先）</li>
 *   <li>4 - 使用时长正序（最少优先）</li>
 *   <li>5 - 使用时长逆序（最多优先）</li>
 *   <li>6 - 最近访问正序（最久优先）</li>
 *   <li>7 - 最近访问逆序（最近优先）</li>
 * </ul>
 * 虚拟图标（锁屏、WiFi）始终排在末尾。
 */
public class AppSortComparator implements Comparator<ResolveInfo> {

  public static final int SORT_NAME_ASC = 0;
  public static final int SORT_NAME_DESC = 1;
  public static final int SORT_INSTALL_ASC = 2;
  public static final int SORT_INSTALL_DESC = 3;
  public static final int SORT_USAGE_ASC = 4;
  public static final int SORT_USAGE_DESC = 5;
  public static final int SORT_RECENT_ASC = 6;
  public static final int SORT_RECENT_DESC = 7;

  private final int mode;
  private final PackageManager pm;
  private final Collator collator;
  private final Map<String, Long> installTimeCache = new HashMap<>();
  private Map<String, UsageStats> usageStatsMap;
  /** 文件夹 id -> 名称；null 表示当前没有文件夹（文件夹按虚拟图标排到末尾）。 */
  private final Map<String, String> folderIdToName;

  public AppSortComparator(Context context, PackageManager pm, int mode) {
    this(context, pm, mode, null);
  }

  /**
   * @param folderIdToName 文件夹 id 到名称的快照；非 null 时文件夹排在所有普通应用之前，
   *                       内部按名称字母序。null 时文件夹被当作虚拟图标排到末尾。
   */
  public AppSortComparator(Context context, PackageManager pm, int mode,
                           Map<String, String> folderIdToName) {
    this.mode = mode;
    this.pm = pm;
    this.collator = Collator.getInstance(Locale.getDefault());
    this.collator.setStrength(Collator.PRIMARY);
    this.folderIdToName = folderIdToName;

    if (needsUsageStats()) {
      usageStatsMap = queryUsageStats(context);
    }
  }

  /**
   * 检查使用统计权限是否已授予（API 22+）。
   */
  public static boolean hasUsageStatsPermission(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return false;
    UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    if (usm == null) return false;
    long now = System.currentTimeMillis();
    List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now);
    return stats != null && !stats.isEmpty();
  }

  /**
   * 判断给定模式是否依赖使用统计数据。
   */
  public static boolean modeNeedsUsageStats(int mode) {
    return mode >= SORT_USAGE_ASC && mode <= SORT_RECENT_DESC;
  }

  @Override
  public int compare(ResolveInfo a, ResolveInfo b) {
    // 文件夹优先于普通应用、晚于虚拟图标的对侧——
    // 具体规则：folder 永远比普通 app 排在前；folder 与虚拟图标比，folder 在前。
    // 我们把文件夹"前置"通过单独的判断实现，再让其他逻辑保持不变。
    String aFolderId = folderIdOf(a);
    String bFolderId = folderIdOf(b);
    if (aFolderId != null || bFolderId != null) {
      if (aFolderId != null && bFolderId != null) {
        String na = folderName(aFolderId);
        String nb = folderName(bFolderId);
        return collator.compare(na, nb);
      }
      // 只有一边是文件夹：文件夹排到前面。
      return aFolderId != null ? -1 : 1;
    }

    boolean aVirtual = isVirtual(a);
    boolean bVirtual = isVirtual(b);
    if (aVirtual && bVirtual) return 0;
    if (aVirtual) return 1;
    if (bVirtual) return -1;

    switch (mode) {
      case SORT_NAME_ASC:
        return compareByName(a, b);
      case SORT_NAME_DESC:
        return compareByName(b, a);
      case SORT_INSTALL_ASC:
        return Long.compare(getInstallTime(a), getInstallTime(b));
      case SORT_INSTALL_DESC:
        return Long.compare(getInstallTime(b), getInstallTime(a));
      case SORT_USAGE_ASC:
        return Long.compare(getUsageTime(a), getUsageTime(b));
      case SORT_USAGE_DESC:
        return Long.compare(getUsageTime(b), getUsageTime(a));
      case SORT_RECENT_ASC:
        return Long.compare(getLastUsed(a), getLastUsed(b));
      case SORT_RECENT_DESC:
        return Long.compare(getLastUsed(b), getLastUsed(a));
      default:
        return compareByName(a, b);
    }
  }

  private boolean isVirtual(ResolveInfo info) {
    String pkg = info.activityInfo.packageName;
    return AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)
        || AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(pkg);
  }

  /**
   * 如果该 ResolveInfo 是文件夹虚拟项且当前 comparator 有文件夹快照，返回文件夹 id；
   * 否则返回 null。当 {@link #folderIdToName} 为 null（例如未启用文件夹的单元测试），
   * 文件夹包名不会被识别为"文件夹"，此时 caller 通常也不会构造这种项。
   */
  private String folderIdOf(ResolveInfo info) {
    if (folderIdToName == null) return null;
    if (info == null || info.activityInfo == null) return null;
    String id = AppDataCenter.folderIdFromPackage(info.activityInfo.packageName);
    if (id == null) return null;
    return folderIdToName.containsKey(id) ? id : null;
  }

  /** 返回排序用的文件夹显示名；空名退化为 id，让对应项稳定排序。 */
  private String folderName(String folderId) {
    String n = folderIdToName != null ? folderIdToName.get(folderId) : null;
    if (n == null || n.isEmpty()) return folderId;
    return n;
  }

  private int compareByName(ResolveInfo a, ResolveInfo b) {
    String labelA = a.loadLabel(pm).toString();
    String labelB = b.loadLabel(pm).toString();
    return collator.compare(labelA, labelB);
  }

  private long getInstallTime(ResolveInfo info) {
    String pkg = info.activityInfo.packageName;
    Long cached = installTimeCache.get(pkg);
    if (cached != null) return cached;
    try {
      long time = pm.getPackageInfo(pkg, 0).firstInstallTime;
      installTimeCache.put(pkg, time);
      return time;
    } catch (PackageManager.NameNotFoundException e) {
      installTimeCache.put(pkg, 0L);
      return 0;
    }
  }

  private long getUsageTime(ResolveInfo info) {
    if (usageStatsMap == null) return 0;
    UsageStats stats = usageStatsMap.get(info.activityInfo.packageName);
    if (stats == null) return 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return stats.getTotalTimeInForeground();
    }
    return 0;
  }

  private long getLastUsed(ResolveInfo info) {
    if (usageStatsMap == null) return 0;
    UsageStats stats = usageStatsMap.get(info.activityInfo.packageName);
    if (stats == null) return 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return stats.getLastTimeUsed();
    }
    return 0;
  }

  private boolean needsUsageStats() {
    return modeNeedsUsageStats(mode);
  }

  private Map<String, UsageStats> queryUsageStats(Context context) {
    Map<String, UsageStats> map = new HashMap<>();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return map;
    UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    if (usm == null) return map;
    long now = System.currentTimeMillis();
    // 查询最近 30 天的使用统计
    List<UsageStats> statsList = usm.queryUsageStats(
        UsageStatsManager.INTERVAL_MONTHLY, now - 30L * 24 * 60 * 60 * 1000, now);
    if (statsList == null) return map;
    for (UsageStats stats : statsList) {
      String pkg = stats.getPackageName();
      UsageStats existing = map.get(pkg);
      if (existing == null) {
        map.put(pkg, stats);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // 保留 totalTimeInForeground 和 lastTimeUsed 更大的那个
        if (stats.getLastTimeUsed() > existing.getLastTimeUsed()) {
          map.put(pkg, stats);
        }
      }
    }
    return map;
  }
}
