package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 应用配置管理类。
 * 统一管理 SharedPreferences 的读写，缓存常用配置值。
 * 偏好键（KEY_*）集中定义于此类。
 */
public class Config {

  // ---- 偏好键常量 ----
  public static final String KEY_COL_NUM = "colNumKey";
  public static final String KEY_ROW_NUM = "rowNumKey";
  public static final String KEY_APP_NAME_LINES = "appNameShowLines";
  public static final String KEY_HIDE_APPS = "hideAppsKey";
  public static final String KEY_FONT_SIZE = "launcherFontSize";
  public static final String KEY_HIDE_DIVIDER = "launcherHideDivider";
  public static final String KEY_SHOW_STATUS_BAR = "launcherShowStatusBar";
  public static final String KEY_SHOW_CUSTOM_ICON = "launcherShowCustomIcon";
  public static final String KEY_SORT_MODE = "launcherSortMode";
  public static final String KEY_FONT_PATH = "launcherFontPath";
  public static final String KEY_LOCALE_TAG = "launcherLocaleTag";
  public static final String KEY_HIDE_SYSTEM_APPS = "launcherHideSystemApps";
  public static final String KEY_RETURN_NOTIFICATION = "launcherReturnNotification";
  /** 自动刷屏间隔（分钟，0 = 关闭）。 */
  public static final String KEY_AUTO_REFRESH_MIN = "launcherAutoRefreshMin";
  /** 置顶最近使用应用数量（0 = 关闭，3/5/8 = 钉住前 N 个）。 */
  public static final String KEY_PIN_RECENT_COUNT = "launcherPinRecentCount";
  /** 每个 app 的用户自定义显示名；存为 prefs 内 "rename_<pkg>" 单独 key 避免合并冲突。 */
  public static final String KEY_RENAME_PREFIX = "rename_";
  /** 快速啟動 Dock 釘住的 app 包名列表（CSV 順序保留）。 */
  public static final String KEY_DOCK_APPS = "launcherDockApps";
  public static final String KEY_DOCK_ENABLED = "launcherDockEnabled";
  /** Dock 最多允許的釘住數量（避免擠爆底部）。 */
  public static final int DOCK_MAX_SIZE = 5;

  /** 懸浮 Home 按鈕。 */
  public static final String KEY_FLOATING_HOME_ENABLED = "launcherFloatingHomeEnabled";
  public static final String KEY_FLOATING_HOME_X = "launcherFloatingHomeX";
  public static final String KEY_FLOATING_HOME_Y = "launcherFloatingHomeY";
  public static final String KEY_FLOATING_HOME_SIZE_DP = "launcherFloatingHomeSizeDp";
  public static final int FLOATING_HOME_DEFAULT_SIZE_DP = 44;
  /** 通知角标总开关（默认关闭，开启时需要用户授予通知访问权限）。 */
  public static final String KEY_NOTIFICATION_BADGE = "launcherNotificationBadge";
  /** Schema 版本号，用于将来字段重命名 / 类型变更时做迁移。 */
  public static final String KEY_SCHEMA_VERSION = "_schemaVersion";

  /** 当前 schema 版本。变更字段时递增，并在 {@link #migrate} 中处理对应版本号。 */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  // ---- 默认值 ----
  private static final int DEFAULT_COL_NUM = 5;
  private static final int DEFAULT_ROW_NUM = 5;
  private static final float DEFAULT_FONT_SIZE = 14f;
  private static final int DEFAULT_APP_NAME_LINES = Integer.MAX_VALUE;
  private static final boolean DEFAULT_HIDE_DIVIDER = true;
  private static final boolean DEFAULT_SHOW_STATUS_BAR = true;
  private static final boolean DEFAULT_SHOW_CUSTOM_ICON = false;
  private static final int DEFAULT_SORT_MODE = 0;

  private static final String PREFS_FILE = "launcherPropertyFile";

  private final SharedPreferences prefs;

  // ---- 缓存字段 ----
  private int colNum = -1;
  private int rowNum = -1;
  private float fontSize = -1;
  private int appNameLines = -1;
  private boolean hideDivider;
  private boolean showStatusBar;
  private boolean showCustomIcon;
  private int sortMode = -1;
  private final Set<String> hideApps = new HashSet<>();
  private boolean hideAppsLoaded = false;

  public Config(Context context) {
    // attachBaseContext 阶段 getApplicationContext() 可能为 null（Robolectric / 部分 ROM）
    Context appCtx = context.getApplicationContext();
    if (appCtx == null) appCtx = context;
    this.prefs = appCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    migrateIfNeeded();
    this.hideDivider = prefs.getBoolean(KEY_HIDE_DIVIDER, DEFAULT_HIDE_DIVIDER);
    this.showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, DEFAULT_SHOW_STATUS_BAR);
    this.showCustomIcon = prefs.getBoolean(KEY_SHOW_CUSTOM_ICON, DEFAULT_SHOW_CUSTOM_ICON);
    this.appNameLines = prefs.getInt(KEY_APP_NAME_LINES, DEFAULT_APP_NAME_LINES);
  }

  /**
   * 跑一次性迁移：把存储的 schema 版本升到 {@link #CURRENT_SCHEMA_VERSION}。
   * 当前版本 1 也清理已废弃的 FTP 偏好键，防止旧版本残留占空间。
   */
  private void migrateIfNeeded() {
    int stored = prefs.getInt(KEY_SCHEMA_VERSION, 0);
    if (stored == CURRENT_SCHEMA_VERSION) return;
    SharedPreferences.Editor e = prefs.edit();
    if (stored < 1) {
      // v1: 移除 FTP 相关偏好
      e.remove("ftpPort").remove("ftpUser").remove("ftpPassword");
    }
    e.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply();
  }

  // ---- 列数 ----

  public int getColNum() {
    if (colNum == -1) {
      colNum = prefs.getInt(KEY_COL_NUM, DEFAULT_COL_NUM);
    }
    return colNum;
  }

  public void setColNum(int colNum) {
    if (this.colNum == colNum) return;
    this.colNum = colNum;
    prefs.edit().putInt(KEY_COL_NUM, colNum).apply();
  }

  // ---- 行数 ----

  public int getRowNum() {
    if (rowNum == -1) {
      rowNum = prefs.getInt(KEY_ROW_NUM, DEFAULT_ROW_NUM);
    }
    return rowNum;
  }

  public void setRowNum(int rowNum) {
    if (this.rowNum == rowNum) return;
    this.rowNum = rowNum;
    prefs.edit().putInt(KEY_ROW_NUM, rowNum).apply();
  }

  // ---- 隐藏应用 ----

  public void addHideApp(String packageName) {
    ensureHideAppsLoaded();
    if (hideApps.add(packageName)) {
      persistHideApps();
    }
  }

  public void removeHideApp(String packageName) {
    ensureHideAppsLoaded();
    if (hideApps.remove(packageName)) {
      persistHideApps();
    }
  }

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    this.hideAppsLoaded = true;
    persistHideApps();
  }

  /** 返回隐藏应用集合的只读视图，禁止调用方直接修改内部状态。 */
  public Set<String> getHideApps() {
    ensureHideAppsLoaded();
    return Collections.unmodifiableSet(hideApps);
  }

  private void ensureHideAppsLoaded() {
    if (!hideAppsLoaded) {
      Set<String> stored = prefs.getStringSet(KEY_HIDE_APPS, null);
      if (stored != null) {
        hideApps.addAll(stored);
      }
      hideAppsLoaded = true;
    }
  }

  private void persistHideApps() {
    // 写入前复制，避免与 SharedPreferences 内部副本共享可变 Set。
    prefs.edit().putStringSet(KEY_HIDE_APPS, new HashSet<>(hideApps)).apply();
  }

  // ---- 字体大小 ----

  public float getFontSize() {
    if (fontSize < 0) {
      fontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }
    return fontSize;
  }

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    prefs.edit().putFloat(KEY_FONT_SIZE, fontSize).apply();
  }

  // ---- 分隔线 ----

  public boolean isHideDivider() {
    return hideDivider;
  }

  public void setHideDivider(boolean hide) {
    this.hideDivider = hide;
    prefs.edit().putBoolean(KEY_HIDE_DIVIDER, hide).apply();
  }

  // ---- 状态栏 ----

  public boolean isShowStatusBar() {
    return showStatusBar;
  }

  public void setShowStatusBar(boolean show) {
    this.showStatusBar = show;
    prefs.edit().putBoolean(KEY_SHOW_STATUS_BAR, show).apply();
  }

  // ---- 自定义图标 ----

  public boolean isShowCustomIcon() {
    return showCustomIcon;
  }

  public void setShowCustomIcon(boolean show) {
    this.showCustomIcon = show;
    prefs.edit().putBoolean(KEY_SHOW_CUSTOM_ICON, show).apply();
  }

  // ---- 应用名行数 ----

  public int getAppNameLines() {
    return appNameLines;
  }

  public void setAppNameLines(int lines) {
    this.appNameLines = lines;
    prefs.edit().putInt(KEY_APP_NAME_LINES, lines).apply();
  }

  // ---- 排序方式 ----

  public int getSortMode() {
    if (sortMode == -1) {
      sortMode = prefs.getInt(KEY_SORT_MODE, DEFAULT_SORT_MODE);
    }
    return sortMode;
  }

  public void setSortMode(int mode) {
    if (this.sortMode == mode) return;
    this.sortMode = mode;
    prefs.edit().putInt(KEY_SORT_MODE, mode).apply();
  }

  // ---- 字体文件 ----

  public String getFontPath() {
    return prefs.getString(KEY_FONT_PATH, null);
  }

  public void setFontPath(String path) {
    prefs.edit().putString(KEY_FONT_PATH, path).apply();
  }

  public void clearFontPath() {
    prefs.edit().remove(KEY_FONT_PATH).apply();
  }

  // ---- 语言 override ----

  /** 返回强制 locale 标签（BCP-47）；空字符串 = 跟随系统。 */
  public String getLocaleTag() {
    return prefs.getString(KEY_LOCALE_TAG, "");
  }

  public void setLocaleTag(String tag) {
    prefs.edit().putString(KEY_LOCALE_TAG, tag != null ? tag : "").apply();
  }

  // ---- 隐藏系统应用 ----

  public boolean isHideSystemApps() {
    return prefs.getBoolean(KEY_HIDE_SYSTEM_APPS, false);
  }

  public void setHideSystemApps(boolean hide) {
    prefs.edit().putBoolean(KEY_HIDE_SYSTEM_APPS, hide).apply();
  }

  // ---- 返回桌面通知 ----

  public boolean isReturnNotificationEnabled() {
    return prefs.getBoolean(KEY_RETURN_NOTIFICATION, false);
  }

  public void setReturnNotificationEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_RETURN_NOTIFICATION, enabled).apply();
  }

  // ---- 自动刷屏 ----

  public int getAutoRefreshMinutes() {
    return prefs.getInt(KEY_AUTO_REFRESH_MIN, 0);
  }

  public void setAutoRefreshMinutes(int minutes) {
    prefs.edit().putInt(KEY_AUTO_REFRESH_MIN, Math.max(0, minutes)).apply();
  }

  // ---- 置顶最近使用 ----

  public int getPinRecentCount() {
    return prefs.getInt(KEY_PIN_RECENT_COUNT, 0);
  }

  public void setPinRecentCount(int n) {
    prefs.edit().putInt(KEY_PIN_RECENT_COUNT, Math.max(0, n)).apply();
  }

  // ---- 通知角标 ----

  public boolean isNotificationBadgeEnabled() {
    return prefs.getBoolean(KEY_NOTIFICATION_BADGE, false);
  }

  public void setNotificationBadgeEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_NOTIFICATION_BADGE, enabled).apply();
  }

  // ---- per-app 重命名 ----

  /** 返回该 app 的自定义显示名；未设置时返回 null。 */
  public String getRename(String packageName) {
    if (packageName == null || packageName.isEmpty()) return null;
    String v = prefs.getString(KEY_RENAME_PREFIX + packageName, null);
    return (v == null || v.isEmpty()) ? null : v;
  }

  public void setRename(String packageName, String customName) {
    if (packageName == null || packageName.isEmpty()) return;
    String key = KEY_RENAME_PREFIX + packageName;
    if (customName == null || customName.isEmpty()) {
      prefs.edit().remove(key).apply();
    } else {
      prefs.edit().putString(key, customName).apply();
    }
  }

  // ---- 快速啟動 Dock ----

  public boolean isDockEnabled() {
    return prefs.getBoolean(KEY_DOCK_ENABLED, false);
  }

  public void setDockEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_DOCK_ENABLED, enabled).apply();
  }

  /** 返回 Dock 中釘住的包名（順序保留）。 */
  public java.util.List<String> getDockApps() {
    String csv = prefs.getString(KEY_DOCK_APPS, "");
    if (csv.isEmpty()) return java.util.Collections.emptyList();
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    for (String s : csv.split(",")) {
      if (!s.isEmpty()) result.add(s);
    }
    return result;
  }

  public void setDockApps(java.util.List<String> apps) {
    if (apps == null || apps.isEmpty()) {
      prefs.edit().remove(KEY_DOCK_APPS).apply();
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < apps.size() && i < DOCK_MAX_SIZE; i++) {
      if (i > 0) sb.append(',');
      sb.append(apps.get(i));
    }
    prefs.edit().putString(KEY_DOCK_APPS, sb.toString()).apply();
  }

  public boolean isInDock(String pkg) {
    return getDockApps().contains(pkg);
  }

  /** @return true 表示加入成功；false 表示已存在或 Dock 已滿 */
  public boolean addToDock(String pkg) {
    java.util.List<String> list = new java.util.ArrayList<>(getDockApps());
    if (list.contains(pkg)) return false;
    if (list.size() >= DOCK_MAX_SIZE) return false;
    list.add(pkg);
    setDockApps(list);
    return true;
  }

  public boolean removeFromDock(String pkg) {
    java.util.List<String> list = new java.util.ArrayList<>(getDockApps());
    if (!list.remove(pkg)) return false;
    setDockApps(list);
    return true;
  }

  // ---- 懸浮 Home 按鈕 ----

  public boolean isFloatingHomeEnabled() {
    return prefs.getBoolean(KEY_FLOATING_HOME_ENABLED, false);
  }

  public void setFloatingHomeEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_FLOATING_HOME_ENABLED, enabled).apply();
  }

  public int getFloatingHomeX() {
    return prefs.getInt(KEY_FLOATING_HOME_X, -1);
  }

  public int getFloatingHomeY() {
    return prefs.getInt(KEY_FLOATING_HOME_Y, -1);
  }

  public void setFloatingHomePosition(int x, int y) {
    prefs.edit()
        .putInt(KEY_FLOATING_HOME_X, x)
        .putInt(KEY_FLOATING_HOME_Y, y)
        .apply();
  }

  public int getFloatingHomeSizeDp() {
    return prefs.getInt(KEY_FLOATING_HOME_SIZE_DP, FLOATING_HOME_DEFAULT_SIZE_DP);
  }

  public void setFloatingHomeSizeDp(int dp) {
    prefs.edit().putInt(KEY_FLOATING_HOME_SIZE_DP, dp).apply();
  }
}
