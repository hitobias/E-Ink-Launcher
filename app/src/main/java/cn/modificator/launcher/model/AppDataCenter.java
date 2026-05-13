package cn.modificator.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 应用数据管理中心，负责加载应用列表和分页逻辑。
 */
public class AppDataCenter {

  /** 虚拟包名：Wifi 控制入口 */
  public static final String WIFI_PACKAGE_NAME = "E-ink_Launcher.WiFi";
  /** 虚拟包名：一键锁屏入口 */
  public static final String LOCK_PACKAGE_NAME = "E-ink_Launcher.Lock";
  /** 虚拟包名：蓝牙开关 / 设置入口 */
  public static final String BLUETOOTH_PACKAGE_NAME = "E-ink_Launcher.Bluetooth";
  /** 文件夹虚拟包名前缀，后面跟 UUID。 */
  public static final String FOLDER_PACKAGE_PREFIX = "E-ink_Launcher.Folder.";

  /**
   * 如果给定 pkg 是文件夹虚拟包名，返回 UUID；否则返回 null。
   */
  public static String folderIdFromPackage(String pkg) {
    if (pkg == null) return null;
    if (!pkg.startsWith(FOLDER_PACKAGE_PREFIX)) return null;
    String id = pkg.substring(FOLDER_PACKAGE_PREFIX.length());
    return id.isEmpty() ? null : id;
  }

  private final Context mContext;
  private final List<ResolveInfo> mApps = new ArrayList<>();
  private int pageIndex = 0;
  private int pageCount = 0;
  private int colNum = 5;
  private int rowNum = 5;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private TextView pageStatus;
  private final Set<String> hideApps = new HashSet<>();
  private int sortMode = AppSortComparator.SORT_NAME_ASC;
  private boolean hideSystemApps = false;
  private int pinRecentCount = 0;

  private final AppListCache appListCache;
  private final FolderStore folderStore;
  /** queryIntentActivities result, without virtual icons or hide filter. */
  private List<ResolveInfo> cachedRawList;
  /** When true, next load must re-query PackageManager. */
  private boolean rawListDirty = true;

  // Sort cache: snapshot of the last sorted mApps and the inputs it was sorted with.
  private List<ResolveInfo> sortedSnapshot;
  private int cachedSortMode = Integer.MIN_VALUE;
  private int cachedHideHash = 0;
  private int cachedRawHash = 0;

  public AppDataCenter(Context context) {
    this.mContext = context;
    this.appListCache = new AppListCache(context);
    this.folderStore = new FolderStore(context);
    // Best-effort warm cache: resolve recently-known packages so first render
    // can show stale-but-fast UI.
    tryWarmCacheFromDisk();
  }

  /** 暴露文件夹存储，供 UI 层读取 / 修改文件夹。 */
  public FolderStore folderStore() {
    return folderStore;
  }

  /**
   * 把当前文件夹快照转成排序用的 id->name 映射。返回 null 表示当前没有文件夹，
   * 调用方可走老的"虚拟图标统一垫底"逻辑。
   */
  private java.util.Map<String, String> snapshotFolderNames() {
    java.util.List<AppFolder> all = folderStore.all();
    if (all.isEmpty()) return null;
    java.util.Map<String, String> m = new java.util.HashMap<>(all.size());
    for (AppFolder f : all) {
      m.put(f.id, f.name);
    }
    return m;
  }

  /** 构建一个文件夹的虚拟 ResolveInfo。 */
  private ResolveInfo createFolderIcon(AppFolder folder) {
    ResolveInfo info = new ResolveInfo();
    info.activityInfo = new ActivityInfo();
    info.activityInfo.packageName = FOLDER_PACKAGE_PREFIX + folder.id;
    // name 字段填一个稳定值，供 LauncherAdapter 的 key 比较使用。
    info.activityInfo.name = "Folder";
    return info;
  }

  /**
   * 把所有"在某个文件夹里"的包名收集成集合，便于在主网格里过滤掉它们。
   */
  private java.util.Set<String> collectFolderMemberPackages() {
    java.util.Set<String> set = new java.util.HashSet<>();
    for (AppFolder f : folderStore.all()) {
      set.addAll(f.packages);
    }
    return set;
  }

  // =========================================================================
  // Adapter / Binder 绑定
  // =========================================================================

  public void setAdapter(LauncherAdapter adapter) {
    this.adapter = adapter;
    this.binder = adapter.getBinder();
    if (binder != null) {
      binder.setHideAppPkg(hideApps);
    }
    setPageShow();
  }

  public void setPageStatus(TextView pageStatus) {
    this.pageStatus = pageStatus;
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  // =========================================================================
  // 隐藏应用管理
  // =========================================================================

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    loadApps();
  }

  /**
   * 返回当前会显示的应用列表的只读副本（含虚拟图标，遵守用户隐藏 / 系统过滤）。
   * 用于搜索等需要快照当前可见集合的场景。
   */
  public List<ResolveInfo> snapshotDisplayedApps() {
    return Collections.unmodifiableList(new ArrayList<>(mApps));
  }

  public Set<String> getHideApps() {
    return hideApps;
  }

  // =========================================================================
  // 列数/行数
  // =========================================================================

  public void setColNum(int colNum) {
    this.colNum = colNum;
    updatePageCount();
    setPageShow();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  /** 批量设置行列数，只触发一次分页更新 */
  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  // =========================================================================
  // 排序
  // =========================================================================

  public void setSortMode(int sortMode) {
    this.sortMode = sortMode;
  }

  public int getSortMode() {
    return sortMode;
  }

  public void setHideSystemApps(boolean hide) {
    if (this.hideSystemApps == hide) return;
    this.hideSystemApps = hide;
    sortedSnapshot = null;
  }

  public void setPinRecentCount(int n) {
    if (this.pinRecentCount == n) return;
    this.pinRecentCount = n;
    sortedSnapshot = null;
  }

  // =========================================================================
  // 翻页
  // =========================================================================

  public void showNextPage() {
    if (pageIndex >= pageCount) return;
    pageIndex++;
    setPageShow();
  }

  public void showLastPage() {
    if (pageIndex <= 0) return;
    pageIndex--;
    setPageShow();
  }

  // =========================================================================
  // 刷新
  // =========================================================================

  /**
   * Called when an app package is installed/removed/replaced; forces next
   * refresh to re-query PackageManager.
   */
  public void markRawListDirty() {
    rawListDirty = true;
    // Sort cache also invalid since raw changed.
    sortedSnapshot = null;
  }

  /**
   * 在文件夹增/删/改名/成员变动后调用：使排序缓存失效。
   * 不重查 PackageManager（应用集合本身没变）。
   */
  public void markFoldersDirty() {
    sortedSnapshot = null;
  }

  public void refreshAppList() {
    refreshAppList(false);
  }

  public void refreshAppList(boolean showAll) {
    if (showAll) {
      loadAllApps();
    } else {
      loadApps();
    }
    setPageShow();
  }

  // =========================================================================
  // 内部加载
  // =========================================================================

  private void loadApps() {
    if (binder != null) {
      hideApps.clear();
      hideApps.addAll(binder.getHideAppPkg());
    }

    List<ResolveInfo> raw;
    if (!rawListDirty && cachedRawList != null) {
      raw = cachedRawList;
    } else {
      Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
      mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
      raw = mContext.getPackageManager().queryIntentActivities(mainIntent, 0);
      cachedRawList = raw;
      rawListDirty = false;
      appListCache.writeAsync(raw);
    }

    java.util.Set<String> folderMembers = collectFolderMemberPackages();
    mApps.clear();
    // 文件夹作为单元格出现在主网格里。
    for (AppFolder folder : folderStore.all()) {
      mApps.add(createFolderIcon(folder));
    }
    for (ResolveInfo info : raw) {
      if (!AppFilter.shouldShow(info, hideSystemApps)) continue;
      String pkg = info.activityInfo.packageName;
      if (hideApps.contains(pkg)) continue;
      // 在文件夹中的应用，从主网格里隐藏。
      if (folderMembers.contains(pkg)) continue;
      mApps.add(info);
    }
    if (!hideApps.contains(LOCK_PACKAGE_NAME)) {
      mApps.add(createPowerIcon());
    }
    if (!hideApps.contains(WIFI_PACKAGE_NAME)) {
      mApps.add(createWifiIcon());
    }
    if (BluetoothControl.isSupported() && !hideApps.contains(BLUETOOTH_PACKAGE_NAME)) {
      mApps.add(createBluetoothIcon());
    }
    sortApps();
    updatePageCount();
  }

  private void loadAllApps() {
    List<ResolveInfo> raw;
    if (!rawListDirty && cachedRawList != null) {
      raw = cachedRawList;
    } else {
      Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
      mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
      raw = mContext.getPackageManager().queryIntentActivities(mainIntent, 0);
      cachedRawList = raw;
      rawListDirty = false;
      appListCache.writeAsync(raw);
    }

    mApps.clear();
    // 管理模式下也尊重 BLOCKED_PREFIXES（这些应用永远不该出现），但忽略 hideSystemApps
    for (ResolveInfo info : raw) {
      if (AppFilter.shouldShow(info, /* hideSystemApps= */ false)) {
        mApps.add(info);
      }
    }
    mApps.add(createPowerIcon());
    mApps.add(createWifiIcon());
    if (BluetoothControl.isSupported()) {
      mApps.add(createBluetoothIcon());
    }
    if (binder != null) {
      binder.setHideAppPkg(hideApps);
    }
    sortApps();
    updatePageCount();
  }

  private void setPageShow() {
    int itemCount = colNum * rowNum;
    int pageStart = pageIndex * itemCount;
    int pageEnd = Math.min(pageStart + itemCount, mApps.size());
    adapter.setAppList(mApps.subList(pageStart, pageEnd));
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  private void updatePageCount() {
    int itemCount = colNum * rowNum;
    pageCount = mApps.size() / itemCount - (mApps.size() % itemCount == 0 ? 1 : 0);
    pageCount = Math.max(pageCount, 0);
    pageIndex = Math.min(pageIndex, pageCount);
  }

  private void sortApps() {
    int hideHash = hideApps.hashCode();
    int rawHash = (cachedRawList != null ? cachedRawList.size() : 0)
        + (cachedRawList != null && !cachedRawList.isEmpty()
           ? cachedRawList.get(0).activityInfo.packageName.hashCode() : 0);
    // Note: this is an approximate hash; if the raw list changes,
    // markRawListDirty also nulls sortedSnapshot.
    if (sortedSnapshot != null
        && cachedSortMode == sortMode
        && cachedHideHash == hideHash
        && cachedRawHash == rawHash
        && sortedSnapshot.size() == mApps.size()) {
      mApps.clear();
      mApps.addAll(sortedSnapshot);
      return;
    }
    Collections.sort(mApps,
        new AppSortComparator(mContext, mContext.getPackageManager(), sortMode, snapshotFolderNames()));
    pinRecentToFront();
    sortedSnapshot = new ArrayList<>(mApps);
    cachedSortMode = sortMode;
    cachedHideHash = hideHash;
    cachedRawHash = rawHash;
  }

  /**
   * 把最近使用的前 N 个 app（基于 UsageStats）移到 mApps 头部，无论当前 sort mode。
   * 虚拟图标 (Lock/WiFi/Bluetooth) 始终保持在最末，不参与置顶。
   */
  private void pinRecentToFront() {
    if (pinRecentCount <= 0) return;
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP_MR1) return;
    if (!AppSortComparator.hasUsageStatsPermission(mContext)) return;
    java.util.List<String> topPkgs = queryRecentPackages(pinRecentCount);
    if (topPkgs.isEmpty()) return;

    java.util.List<ResolveInfo> pinned = new ArrayList<>(topPkgs.size());
    for (String pkg : topPkgs) {
      java.util.Iterator<ResolveInfo> it = mApps.iterator();
      while (it.hasNext()) {
        ResolveInfo info = it.next();
        if (info.activityInfo != null
            && pkg.equals(info.activityInfo.packageName)
            && !LOCK_PACKAGE_NAME.equals(pkg)
            && !WIFI_PACKAGE_NAME.equals(pkg)
            && !BLUETOOTH_PACKAGE_NAME.equals(pkg)) {
          pinned.add(info);
          it.remove();
          break;
        }
      }
    }
    if (!pinned.isEmpty()) {
      mApps.addAll(0, pinned);
    }
  }

  private java.util.List<String> queryRecentPackages(int n) {
    android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
        mContext.getSystemService(Context.USAGE_STATS_SERVICE);
    if (usm == null) return java.util.Collections.emptyList();
    long now = System.currentTimeMillis();
    java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(
        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
        now - 7L * 24 * 60 * 60 * 1000, now);
    if (stats == null || stats.isEmpty()) return java.util.Collections.emptyList();
    java.util.Map<String, Long> latest = new java.util.HashMap<>();
    for (android.app.usage.UsageStats s : stats) {
      String pkg = s.getPackageName();
      if (pkg == null || pkg.equals(mContext.getPackageName())) continue;
      Long existing = latest.get(pkg);
      long t = s.getLastTimeUsed();
      if (existing == null || t > existing) latest.put(pkg, t);
    }
    java.util.List<java.util.Map.Entry<String, Long>> sorted = new ArrayList<>(latest.entrySet());
    sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    java.util.List<String> result = new ArrayList<>(n);
    for (java.util.Map.Entry<String, Long> e : sorted) {
      if (result.size() >= n) break;
      result.add(e.getKey());
    }
    return result;
  }

  private void tryWarmCacheFromDisk() {
    List<String[]> cached = appListCache.read();
    if (cached.isEmpty()) return;
    PackageManager pm = mContext.getPackageManager();
    List<ResolveInfo> warm = new ArrayList<>(cached.size());
    for (String[] entry : cached) {
      try {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(entry[0], entry[1]);
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        if (ri != null) warm.add(ri);
      } catch (Exception ignored) {
      }
    }
    if (!warm.isEmpty()) {
      cachedRawList = warm;
      // Keep rawListDirty=true so a real query still happens on first refresh,
      // but in the meantime this list is available for fast initial render.
    }
  }

  // =========================================================================
  // 虚拟图标创建
  // =========================================================================

  private ResolveInfo createWifiIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.wifi_on;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = WIFI_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createPowerIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeylock;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = LOCK_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createBluetoothIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.bt_on;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = BLUETOOTH_PACKAGE_NAME;
    return resolveInfo;
  }
}
