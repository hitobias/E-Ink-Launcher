package cn.modificator.launcher;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.modificator.launcher.controllers.BatteryController;
import cn.modificator.launcher.controllers.ClockController;
import cn.modificator.launcher.controllers.DeviceAdminController;
import cn.modificator.launcher.controllers.ShortcutDispatcher;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.AppFolder;
import cn.modificator.launcher.model.BluetoothControl;
import cn.modificator.launcher.model.FolderStore;
import cn.modificator.launcher.model.EpdRefresh;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.MemoryListenerRegistry;
import cn.modificator.launcher.model.NotificationCounter;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends FragmentActivity
    implements AppItemBinder.Callback, EInkLauncherView.OnPageChangeListener,
    SettingFragment.OnSettingChangeListener {

  @Override
  protected void attachBaseContext(Context newBase) {
    String localeTag = new Config(newBase).getLocaleTag();
    super.attachBaseContext(LocaleManager.wrap(newBase, localeTag));
  }

  // ---- Views ----
  private EInkLauncherView launcherView;
  private TextView pageStatus;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private TextView textClock;

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private IconCache iconCache;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private cn.modificator.launcher.widgets.DockManager dockManager;
  private boolean isSystemApp = false;

  // ---- Controllers ----
  private BatteryController batteryController;
  private ClockController clockController;
  private DeviceAdminController deviceAdminController;

  // ---- Receivers ----
  private boolean usbRegistered;

  // 蓝牙长按弹快速连接 dialog 需要 API 31+ 的 BLUETOOTH_CONNECT 运行时权限。
  private ActivityResultLauncher<String> btConnectPermissionLauncher;

  // 自动刷屏调度器
  private final android.os.Handler autoRefreshHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable autoRefreshTask = new Runnable() {
    @Override
    public void run() {
      forceScreenRefresh();
      int minutes = config.getAutoRefreshMinutes();
      if (minutes > 0) {
        autoRefreshHandler.postDelayed(this, minutes * 60_000L);
      }
    }
  };

  private final BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      iconCache.clearAppCache();
      dataCenter.markRawListDirty();
      dataCenter.refreshAppList(binder.isDelete());
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        iconCache.markDirty();
        refreshIcons();
      }
    }
  };

  // =========================================================================
  // Lifecycle
  // =========================================================================

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // 必须在 super.onCreate 之前安装 splash，让系统在冷启动期间显示我们的图标。
    SplashScreen.installSplashScreen(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);

    config = new Config(this);
    WifiControl.init(this);
    BluetoothControl.init(this);
    btConnectPermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
          if (granted) showBluetoothQuickConnect();
          else BluetoothControl.launchSettings();
        });
    applyStatusBarVisibility();

    boolean isChina = "CN".equals(currentLocale().getCountry());

    initViews(isChina);
    registerStaticReceivers();
    checkLaunchHomeNotification();
    installBackPressHandler();
    MemoryListenerRegistry.register(memoryListener);
    // 通知角标：把当前配置同步到 binder，并订阅 NotificationCounter 的变更回调。
    binder.setNotificationBadgeEnabled(config.isNotificationBadgeEnabled());
    NotificationCounter.register(notificationCountListener);
    handleShortcutIntent(getIntent());
    // 若用戶已啟用懸浮 Home，開機後 launcher 自啟時順帶把 service 拉起來。
    if (config.isFloatingHomeEnabled()
        && cn.modificator.launcher.model.FloatingHomeService.canDrawOverlays(this)) {
      cn.modificator.launcher.model.FloatingHomeService.start(this);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleShortcutIntent(intent);
  }

  private final ShortcutDispatcher.Callbacks shortcutCallbacks = new ShortcutDispatcher.Callbacks() {
    @Override
    public void onLockShortcut() {
      deviceAdminController.lockScreen();
    }

    @Override
    public void onSearchShortcut() {
      if (launcherView != null) launcherView.post(Launcher.this::showAppSearchDialog);
    }

    @Override
    public void onSettingsShortcut() {
      getSupportFragmentManager().beginTransaction()
          .replace(android.R.id.content, new SettingFragment())
          .addToBackStack(null)
          .commit();
    }
  };

  private void handleShortcutIntent(Intent intent) {
    ShortcutDispatcher.handle(intent, shortcutCallbacks);
  }

  private final MemoryListenerRegistry.Listener memoryListener = level -> {
    if (iconCache != null) iconCache.clearAppCache();
  };

  /** 通知数量变更时在主线程刷新可见的图标。 */
  private final NotificationCounter.Listener notificationCountListener = () -> {
    // 该回调可能在 binder 线程，转发到 UI 线程。
    if (adapter == null) return;
    runOnUiThread(() -> {
      if (binder != null && binder.isNotificationBadgeEnabled() && adapter != null) {
        adapter.refreshDisplay();
      }
    });
  };

  @Override
  protected void onResume() {
    super.onResume();
    registerDynamicReceivers();
    refreshIcons();
    scheduleAutoRefresh();
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterDynamicReceivers();
    autoRefreshHandler.removeCallbacks(autoRefreshTask);
  }

  private void scheduleAutoRefresh() {
    autoRefreshHandler.removeCallbacks(autoRefreshTask);
    int minutes = config.getAutoRefreshMinutes();
    if (minutes > 0) {
      autoRefreshHandler.postDelayed(autoRefreshTask, minutes * 60_000L);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterDynamicReceivers();
    try {
      unregisterReceiver(appChangeReceiver);
    } catch (IllegalArgumentException ignored) {
      // Already unregistered if onCreate failed mid-setup.
    }
    MemoryListenerRegistry.unregister(memoryListener);
    NotificationCounter.unregister(notificationCountListener);
  }

  // =========================================================================
  // View 初始化
  // =========================================================================

  private void initViews(boolean isChinaLocale) {
    launcherView = findViewById(R.id.mList);
    pageStatus = findViewById(R.id.pageStatus);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    textClock = findViewById(R.id.textClock);

    batteryController = new BatteryController(this, batteryProgress, batteryStatus);
    clockController = new ClockController(this, textClock, isChinaLocale);
    deviceAdminController = new DeviceAdminController(this);

    ImageView settingIcon = findViewById(R.id.toSetting);
    settingIcon.setImageDrawable(
        Utils.tintDrawable(
            ContextCompat.getDrawable(this, R.drawable.navibar_icon_settings_highlight),
            ColorStateList.valueOf(0xff000000)));

    // 配置 Binder、Adapter、View
    iconCache = new IconCache();
    binder = new AppItemBinder(getPackageManager());
    binder.setCallback(this);
    binder.setIconCache(iconCache);
    binder.setHideAppPkg(config.getHideApps());
    binder.setLabelOverride(pkg -> config.getRename(pkg));
    adapter = new LauncherAdapter();
    adapter.setBinder(binder);
    adapter.setFontSize(config.getFontSize());
    adapter.setAppNameLines(config.getAppNameLines());
    launcherView.setAdapter(adapter);
    launcherView.setOnPageChangeListener(this);
    launcherView.setOnDoubleTapListener(this::forceScreenRefresh);
    // 时钟区长按也能强制刷屏（网格满时双击空白不可用的兜底入口）。
    textClock.setOnLongClickListener(v -> {
      forceScreenRefresh();
      return true;
    });

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    // FolderStore 由 dataCenter 持有，binder 也需要它来渲染文件夹单元格。
    binder.setFolderStore(dataCenter.folderStore());
    dataCenter.setSortMode(config.getSortMode());
    dataCenter.setHideSystemApps(config.isHideSystemApps());
    dataCenter.setPinRecentCount(config.getPinRecentCount());
    dataCenter.setHideApps(config.getHideApps());
    dataCenter.setPageStatus(pageStatus);
    cn.modificator.launcher.widgets.PageIndicator indicator = findViewById(R.id.pageIndicator);
    if (indicator != null) dataCenter.setPageIndicator(indicator);
    dataCenter.setAdapter(adapter);

    // 一次性配置网格参数，避免多次重建
    launcherView.configure(config.getColNum(), config.getRowNum(), config.isHideDivider());
    dataCenter.setGridSize(config.getColNum(), config.getRowNum());

    // 翻页按钮
    findViewById(R.id.lastPage).setOnClickListener(v -> dataCenter.showLastPage());
    findViewById(R.id.nextPage).setOnClickListener(v -> dataCenter.showNextPage());

    // 设置按钮：短按 = 设置；长按 = 应用搜索
    View settingBtn = findViewById(R.id.toSetting);
    settingBtn.setOnClickListener(v ->
        getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingFragment())
            .addToBackStack(null)
            .commit());
    settingBtn.setOnLongClickListener(v -> {
      showAppSearchDialog();
      return true;
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(v -> {
      binder.setDelete(false);
      // binder.hideAppPkg 是管理模式期间的真实状态来源。
      Set<String> finalHidden = new HashSet<>(binder.getHideAppPkg());
      config.setHideApps(finalHidden);
      dataCenter.refreshAppList();
      v.setVisibility(View.GONE);
    });

    // Dock 配置：監聽點擊 → 走 onItemClick；長按 → showAppInfoDialog（含 Unpin）
    android.widget.LinearLayout dockBar = findViewById(R.id.dockBar);
    if (dockBar != null) {
      dockManager = new cn.modificator.launcher.widgets.DockManager(
          dockBar, config, iconCache, getPackageManager());
      dockManager.setListener(new cn.modificator.launcher.widgets.DockManager.OnDockAction() {
        @Override
        public void onLaunch(String packageName, ResolveInfo info) {
          onItemClick(info);
        }

        @Override
        public void onLongPress(String packageName, ResolveInfo info) {
          showAppInfoDialog(info, packageName);
        }
      });
    }

    // 检测系统应用
    try {
      isSystemApp = !isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0));
    } catch (PackageManager.NameNotFoundException ignored) {
    }
  }

  private Locale currentLocale() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return getResources().getConfiguration().getLocales().get(0);
    }
    return getResources().getConfiguration().locale;
  }

  // =========================================================================
  // SettingFragment.OnSettingChangeListener 实现
  // =========================================================================

  @Override
  public void onRowNumChanged(int rowNum) {
    launcherView.setRowNum(rowNum);
    dataCenter.setRowNum(rowNum);
  }

  @Override
  public void onColNumChanged(int colNum) {
    launcherView.setColNum(colNum);
    dataCenter.setColNum(colNum);
  }

  @Override
  public void onFontSizeChanged(float size) {
    adapter.setFontSize(size);
  }

  @Override
  public void onAppNameLinesChanged(int lines) {
    adapter.setAppNameLines(lines);
  }

  @Override
  public void onHideDividerChanged(boolean hide) {
    launcherView.setHideDivider(hide);
  }

  @Override
  public void onShowStatusBarChanged(boolean show) {
    applyStatusBarVisibility();
  }

  @Override
  public void onShowCustomIconChanged(boolean show) {
    iconCache.markDirty();
    refreshIcons();
  }

  @Override
  public void onEnterManageMode() {
    binder.setDelete(true);
    dataCenter.refreshAppList(true);
    findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
  }

  @Override
  public void onSortModeChanged(int mode) {
    dataCenter.setSortMode(mode);
    dataCenter.refreshAppList(binder.isDelete());
  }

  @Override
  public void onHideSystemAppsChanged(boolean hide) {
    dataCenter.setHideSystemApps(hide);
    dataCenter.refreshAppList(binder.isDelete());
  }

  @Override
  public void onReturnNotificationChanged(boolean enabled) {
    checkLaunchHomeNotification();
  }

  @Override
  public void onAutoRefreshChanged(int minutes) {
    scheduleAutoRefresh();
  }

  @Override
  public void onPinRecentCountChanged(int n) {
    dataCenter.setPinRecentCount(n);
    dataCenter.refreshAppList(binder.isDelete());
  }

  @Override
  public void onNotificationBadgeChanged(boolean enabled) {
    if (binder != null) binder.setNotificationBadgeEnabled(enabled);
    if (adapter != null) adapter.refreshDisplay();
  }

  @Override
  public void onDockEnabledChanged(boolean enabled) {
    if (dockManager != null) dockManager.refresh();
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void refreshIcons() {
    if (adapter == null || iconCache == null) return;
    iconCache.refreshCustomIcons(this, config.isShowCustomIcon());
    adapter.refreshDisplay();
    if (dockManager != null) dockManager.refresh();
  }

  /**
   * 强制刷屏：在墨水屏上利用一次 黑->白 全屏闪烁清除残影。
   * 触发方式：launcher 空白处双击。
   */
  /**
   * 长按蓝牙图标入口：先检查 API 31+ 的运行时权限，授权后弹出已配对设备列表。
   */
  private void requestBluetoothQuickConnect() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED) {
      btConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
      return;
    }
    showBluetoothQuickConnect();
  }

  private void showBluetoothQuickConnect() {
    if (!BluetoothControl.isEnabled()) {
      android.widget.Toast.makeText(this, R.string.bt_status_off, android.widget.Toast.LENGTH_SHORT).show();
      BluetoothControl.launchSettings();
      return;
    }
    Set<BluetoothDevice> bonded = BluetoothControl.getBondedDevices();
    if (bonded.isEmpty()) {
      android.widget.Toast.makeText(this, R.string.bt_no_paired, android.widget.Toast.LENGTH_SHORT).show();
      BluetoothControl.launchSettings();
      return;
    }
    List<BluetoothDevice> list = new ArrayList<>(bonded);
    // 一次性快照每个设备的连接状态，避免排序与渲染时重复反射调用。
    java.util.Map<BluetoothDevice, Boolean> connectedMap = new java.util.HashMap<>();
    java.util.Map<BluetoothDevice, String> nameMap = new java.util.HashMap<>();
    for (BluetoothDevice d : list) {
      connectedMap.put(d, BluetoothControl.isDeviceConnected(d));
      String label;
      try {
        label = d.getName();
      } catch (SecurityException e) {
        label = null;
      }
      if (label == null || label.isEmpty()) label = d.getAddress();
      nameMap.put(d, label);
    }
    // 已连接设备置顶，其后按名字字母序。
    list.sort((a, b) -> {
      boolean ac = Boolean.TRUE.equals(connectedMap.get(a));
      boolean bc = Boolean.TRUE.equals(connectedMap.get(b));
      if (ac != bc) return ac ? -1 : 1;
      return nameMap.get(a).compareToIgnoreCase(nameMap.get(b));
    });
    CharSequence[] names = new CharSequence[list.size()];
    long now = System.currentTimeMillis();
    for (int i = 0; i < list.size(); i++) {
      BluetoothDevice d = list.get(i);
      String label = nameMap.get(d);
      if (Boolean.TRUE.equals(connectedMap.get(d))) {
        names[i] = "● " + label;
      } else {
        long last = BluetoothControl.getLastConnectMs(d);
        if (last > 0) {
          CharSequence rel = android.text.format.DateUtils.getRelativeTimeSpanString(
              last, now, android.text.format.DateUtils.MINUTE_IN_MILLIS);
          names[i] = "○ " + label + "  · " + rel;
        } else {
          names[i] = "○ " + label;
        }
      }
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.bt_quick_connect)
        .setItems(names, (dialog, which) -> {
          BluetoothDevice device = list.get(which);
          android.widget.Toast.makeText(Launcher.this,
              R.string.bt_connecting, android.widget.Toast.LENGTH_SHORT).show();
          BluetoothControl.tryConnect(device, () -> {
            android.widget.Toast.makeText(Launcher.this,
                R.string.bt_connect_failed, android.widget.Toast.LENGTH_LONG).show();
            BluetoothControl.launchSettings();
          });
        })
        .setPositiveButton(R.string.bt_open_settings, (d, w) -> BluetoothControl.launchSettings())
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  /**
   * 应用搜索对话框：按 label / packageName 实时模糊匹配。
   * 入口：长按设置 ⚙ 图标。
   */
  private void showAppSearchDialog() {
    List<android.content.pm.ResolveInfo> all = dataCenter.snapshotDisplayedApps();
    if (all.isEmpty()) return;

    View root = android.view.LayoutInflater.from(this)
        .inflate(R.layout.dialog_app_search, null, false);
    android.widget.EditText searchInput = root.findViewById(R.id.searchInput);
    android.widget.ListView searchList = root.findViewById(R.id.searchList);

    cn.modificator.launcher.widgets.AppSearchAdapter adapter =
        new cn.modificator.launcher.widgets.AppSearchAdapter(this, all, iconCache);
    searchList.setAdapter(adapter);

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setView(root)
        .setNegativeButton(R.string.dialog_cancel, null)
        .create();

    searchList.setOnItemClickListener((parent, view, position, id) -> {
      android.content.pm.ResolveInfo info = adapter.getItem(position);
      if (info == null) return;
      dialog.dismiss();
      onItemClick(info);
    });

    searchInput.addTextChangedListener(new android.text.TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override public void afterTextChanged(android.text.Editable s) {
        adapter.filter(s.toString());
      }
    });

    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setLayout(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    searchInput.requestFocus();
  }

  private void forceScreenRefresh() {
    final View root = findViewById(android.R.id.content);
    if (root == null) return;
    if (EpdRefresh.full(root)) return;
    final android.graphics.drawable.Drawable original = root.getBackground();
    root.setBackgroundColor(0xff000000);
    root.invalidate();
    root.postDelayed(() -> {
      root.setBackgroundColor(0xffffffff);
      root.invalidate();
      root.postDelayed(() -> {
        root.setBackground(original);
        root.invalidate();
      }, 80);
    }, 80);
  }

  // =========================================================================
  // AppItemBinder.Callback 实现
  // =========================================================================

  @Override
  public void onItemClick(ResolveInfo info) {
    String pkgName = info.activityInfo.packageName;

    String folderId = AppDataCenter.folderIdFromPackage(pkgName);
    if (folderId != null) {
      showFolderContents(folderId);
      return;
    }

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
      deviceAdminController.lockScreen();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
      WifiControl.onClickWifiItem();
    } else if (AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(pkgName)) {
      BluetoothControl.onClickBluetoothItem();
    } else {
      ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      intent.addCategory(Intent.CATEGORY_LAUNCHER);
      intent.setComponent(comp);
      try {
        startActivity(intent);
      } catch (Exception ignored) {
        // Target component may have been uninstalled between refreshes.
      }
    }
  }

  @Override
  public void onItemLongClick(View anchor, ResolveInfo info) {
    String packageName = info.activityInfo.packageName;

    String folderId = AppDataCenter.folderIdFromPackage(packageName);
    if (folderId != null) {
      showFolderMenu(folderId);
      return;
    }

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      deviceAdminController.showPowerMenu(isSystemApp);
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      WifiControl.onLongClickWifiItem();
    } else if (AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(packageName)) {
      requestBluetoothQuickConnect();
    } else {
      showAppInfoDialog(info, packageName);
    }
  }

  @Override
  public void onItemDeleteClick(ResolveInfo info) {
    Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
        Uri.parse("package:" + info.activityInfo.packageName));
    startActivity(deleteIntent);
  }

  @Override
  public void onItemHideToggle(String packageName, boolean hidden) {
    // 管理模式下的隐藏切换仅更新 UI 状态，"完成" 按钮处理持久化
  }

  // =========================================================================
  // EInkLauncherView.OnPageChangeListener 实现
  // =========================================================================

  @Override
  public void onPageNext() {
    dataCenter.showNextPage();
  }

  @Override
  public void onPagePrev() {
    dataCenter.showLastPage();
  }

  // =========================================================================
  // 文件夹交互
  // =========================================================================

  /**
   * 点击文件夹：展示成员应用列表，点击启动。空文件夹也允许打开（仅显示空状态）。
   */
  private void showFolderContents(String folderId) {
    FolderStore store = dataCenter.folderStore();
    AppFolder folder = store.get(folderId);
    if (folder == null) return;

    PackageManager pm = getPackageManager();
    final List<ResolveInfo> members = new ArrayList<>(folder.packages.size());
    for (String pkg : folder.packages) {
      Intent mainIntent = new Intent(Intent.ACTION_MAIN);
      mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
      mainIntent.setPackage(pkg);
      List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
      if (resolved != null && !resolved.isEmpty()) {
        members.add(resolved.get(0));
      }
    }

    String title = folder.name.isEmpty() ? getString(R.string.folder_default_name) : folder.name;
    if (members.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle(title)
          .setMessage(R.string.hidden_no_apps)
          .setNegativeButton(R.string.dialog_cancel, null)
          .show();
      return;
    }

    final cn.modificator.launcher.widgets.AppSearchAdapter listAdapter =
        new cn.modificator.launcher.widgets.AppSearchAdapter(this, members, iconCache);
    final AlertDialog[] dialogHolder = new AlertDialog[1];
    final android.widget.ListView listView = new android.widget.ListView(this);
    listView.setAdapter(listAdapter);
    listView.setOnItemClickListener((parent, view, position, id) -> {
      ResolveInfo info = listAdapter.getItem(position);
      if (info == null) return;
      if (dialogHolder[0] != null) dialogHolder[0].dismiss();
      onItemClick(info);
    });

    dialogHolder[0] = new AlertDialog.Builder(this)
        .setTitle(title)
        .setView(listView)
        .setNegativeButton(R.string.dialog_cancel, null)
        .create();
    dialogHolder[0].show();
  }

  /**
   * 长按文件夹的菜单：重命名 / 拆散。
   */
  private void showFolderMenu(String folderId) {
    FolderStore store = dataCenter.folderStore();
    AppFolder folder = store.get(folderId);
    if (folder == null) return;
    CharSequence[] actions = new CharSequence[]{
        getString(R.string.folder_rename),
        getString(R.string.folder_dissolve),
    };
    String title = folder.name.isEmpty() ? getString(R.string.folder_default_name) : folder.name;
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(actions, (dialog, which) -> {
          switch (which) {
            case 0:
              renameFolder(folderId);
              break;
            case 1:
              dissolveFolder(folderId);
              break;
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void renameFolder(String folderId) {
    final FolderStore store = dataCenter.folderStore();
    final AppFolder folder = store.get(folderId);
    if (folder == null) return;
    final android.widget.EditText input = new android.widget.EditText(this);
    input.setHint(R.string.folder_name_hint);
    input.setSingleLine(true);
    input.setText(folder.name);
    if (folder.name != null) input.setSelection(folder.name.length());
    int pad = (int) (16 * getResources().getDisplayMetrics().density);
    android.widget.FrameLayout container = new android.widget.FrameLayout(this);
    container.setPadding(pad, pad / 2, pad, 0);
    container.addView(input);

    new AlertDialog.Builder(this)
        .setTitle(R.string.folder_rename)
        .setView(container)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          String newName = input.getText().toString().trim();
          folder.name = newName;
          store.save(folder);
          dataCenter.markFoldersDirty();
          dataCenter.refreshAppList(binder.isDelete());
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void dissolveFolder(String folderId) {
    dataCenter.folderStore().delete(folderId);
    dataCenter.markFoldersDirty();
    dataCenter.refreshAppList(binder.isDelete());
  }

  /**
   * "添加到文件夹..."：选择现有文件夹或新建。
   */
  private void addAppToFolder(final String pkg) {
    final FolderStore store = dataCenter.folderStore();
    final List<AppFolder> existing = store.all();
    // 列表项：所有现有文件夹 + 末尾一个"新建"。
    final CharSequence[] items = new CharSequence[existing.size() + 1];
    for (int i = 0; i < existing.size(); i++) {
      AppFolder f = existing.get(i);
      items[i] = f.name.isEmpty() ? getString(R.string.folder_default_name) : f.name;
    }
    items[items.length - 1] = getString(R.string.folder_new);

    new AlertDialog.Builder(this)
        .setTitle(R.string.folder_add_to)
        .setItems(items, (dialog, which) -> {
          if (which == items.length - 1) {
            promptNewFolderForApp(pkg);
          } else {
            AppFolder f = existing.get(which);
            addPackageToFolder(f, pkg);
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void promptNewFolderForApp(final String pkg) {
    final android.widget.EditText input = new android.widget.EditText(this);
    input.setHint(R.string.folder_name_hint);
    input.setSingleLine(true);
    int pad = (int) (16 * getResources().getDisplayMetrics().density);
    android.widget.FrameLayout container = new android.widget.FrameLayout(this);
    container.setPadding(pad, pad / 2, pad, 0);
    container.addView(input);

    new AlertDialog.Builder(this)
        .setTitle(R.string.folder_new)
        .setView(container)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          String name = input.getText().toString().trim();
          if (name.isEmpty()) name = getString(R.string.folder_default_name);
          AppFolder folder = new AppFolder(java.util.UUID.randomUUID().toString(), name);
          folder.packages.add(pkg);
          dataCenter.folderStore().save(folder);
          dataCenter.markFoldersDirty();
          dataCenter.refreshAppList(binder.isDelete());
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void addPackageToFolder(AppFolder folder, String pkg) {
    if (folder == null || pkg == null) return;
    // 同一应用同时只能在一个文件夹里：先从任何已有文件夹移除，再加入目标。
    FolderStore store = dataCenter.folderStore();
    for (AppFolder f : store.all()) {
      if (!f.id.equals(folder.id) && f.packages.remove(pkg)) {
        store.save(f);
      }
    }
    if (!folder.packages.contains(pkg)) {
      folder.packages.add(pkg);
      store.save(folder);
    }
    dataCenter.markFoldersDirty();
    dataCenter.refreshAppList(binder.isDelete());
  }

  private void showAppInfoDialog(ResolveInfo info, final String packageName) {
    boolean pinned = config.isInDock(packageName);
    String pinAction = pinned
        ? getString(R.string.app_action_unpin_dock)
        : getString(R.string.app_action_pin_dock);
    CharSequence[] actions = new CharSequence[]{
        getString(R.string.folder_add_to),
        getString(R.string.app_action_rename),
        pinAction,
        getString(R.string.app_action_app_info),
        getString(R.string.app_action_hide),
        getString(R.string.app_action_copy_pkg),
        getString(R.string.app_action_force_stop),
        getString(R.string.app_action_uninstall),
    };
    new AlertDialog.Builder(this)
        .setIcon(iconCache.getIcon(packageName, info, getPackageManager()))
        .setTitle(iconCache.getLabel(packageName, info, getPackageManager()))
        .setMessage(buildUsageSummary(packageName))
        .setItems(actions, (dialog, which) -> {
          switch (which) {
            case 0:
              addAppToFolder(packageName);
              break;
            case 1:
              promptRenameApp(info, packageName);
              break;
            case 2:
              togglePinToDock(packageName);
              break;
            case 3:
              openAppDetailsSettings(packageName);
              break;
            case 4:
              toggleAppHidden(packageName);
              break;
            case 5:
              copyToClipboard(packageName);
              break;
            case 6:
              openAppDetailsSettings(packageName);
              break;
            case 7:
              startActivity(new Intent(Intent.ACTION_DELETE,
                  Uri.parse("package:" + packageName)));
              break;
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  /**
   * 切換 Dock 釘住狀態。釘住時若用戶未啟用 Dock，自動把它打開（首次操作就能看到）。
   */
  private void togglePinToDock(String packageName) {
    if (config.isInDock(packageName)) {
      if (config.removeFromDock(packageName)) {
        android.widget.Toast.makeText(this, R.string.dock_unpinned,
            android.widget.Toast.LENGTH_SHORT).show();
      }
    } else {
      if (!config.addToDock(packageName)) {
        android.widget.Toast.makeText(this,
            getString(R.string.dock_full, Config.DOCK_MAX_SIZE),
            android.widget.Toast.LENGTH_SHORT).show();
        return;
      }
      if (!config.isDockEnabled()) {
        config.setDockEnabled(true);
      }
      android.widget.Toast.makeText(this, R.string.dock_pinned,
          android.widget.Toast.LENGTH_SHORT).show();
    }
    if (dockManager != null) dockManager.refresh();
  }

  /**
   * 构建 app 长按对话框的副标题：包名 + 今日使用统计（如果授权）。
   */
  private CharSequence buildUsageSummary(String packageName) {
    StringBuilder sb = new StringBuilder();
    sb.append(getString(R.string.dialog_pkg_name, packageName));
    if (cn.modificator.launcher.model.AppSortComparator.hasUsageStatsPermission(this)) {
      cn.modificator.launcher.model.UsageStatsHelper.Snapshot s =
          cn.modificator.launcher.model.UsageStatsHelper.query(this, packageName);
      sb.append("\n");
      sb.append(getString(R.string.app_usage_today_summary,
          s.todayLaunchCount, formatDuration(s.todayForegroundMs)));
      if (s.totalForegroundMs > 0) {
        sb.append("\n");
        sb.append(getString(R.string.app_usage_total_summary,
            formatDuration(s.totalForegroundMs)));
      }
    }
    return sb.toString();
  }

  private static String formatDuration(long ms) {
    if (ms <= 0) return "0m";
    long mins = ms / 60_000L;
    long hours = mins / 60;
    mins = mins % 60;
    if (hours > 0) return hours + "h " + mins + "m";
    return mins + "m";
  }

  /** 弹出 EditText 让用户重命名 app；空串 = 恢复系统名。 */
  private void promptRenameApp(ResolveInfo info, String packageName) {
    String current = config.getRename(packageName);
    CharSequence systemLabel = iconCache.getLabel(packageName, info, getPackageManager());

    final android.widget.EditText input = new android.widget.EditText(this);
    input.setSingleLine();
    input.setText(current != null ? current : systemLabel);
    input.selectAll();
    int pad = Utils.dp2Px(this, 16);
    android.widget.FrameLayout container = new android.widget.FrameLayout(this);
    container.setPadding(pad, pad / 2, pad, 0);
    container.addView(input);

    new AlertDialog.Builder(this)
        .setTitle(R.string.app_action_rename)
        .setView(container)
        .setPositiveButton(android.R.string.ok, (d, w) -> {
          String typed = input.getText().toString().trim();
          if (typed.isEmpty() || typed.contentEquals(systemLabel)) {
            config.setRename(packageName, null);
          } else {
            config.setRename(packageName, typed);
          }
          if (adapter != null) adapter.refreshDisplay();
        })
        .setNeutralButton(R.string.app_action_rename_reset, (d, w) -> {
          config.setRename(packageName, null);
          if (adapter != null) adapter.refreshDisplay();
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void openAppDetailsSettings(String packageName) {
    try {
      Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + packageName));
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    } catch (Exception ignored) {
    }
  }

  private void copyToClipboard(String text) {
    android.content.ClipboardManager cm =
        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null) {
      cm.setPrimaryClip(android.content.ClipData.newPlainText("package", text));
      android.widget.Toast.makeText(this, R.string.toast_copied, android.widget.Toast.LENGTH_SHORT).show();
    }
  }

  /**
   * 切换单个应用的隐藏状态并持久化。
   * 修复了原版只改内存不写回 SharedPreferences 的 bug。
   */
  private void toggleAppHidden(String packageName) {
    Set<String> updated = new HashSet<>(config.getHideApps());
    if (!updated.add(packageName)) {
      updated.remove(packageName);
    }
    config.setHideApps(updated);
    binder.setHideAppPkg(updated);
    dataCenter.refreshAppList();
  }

  // =========================================================================
  // 广播注册/注销（仅剩 app 安装变化 + USB 挂载；电池/时间由 controller 处理）
  // =========================================================================

  private void registerStaticReceivers() {
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    Utils.registerReceiverCompat(this, appChangeReceiver, appChangeFilter);
  }

  private void registerDynamicReceivers() {
    batteryController.start();
    clockController.start();
    if (!usbRegistered) {
      IntentFilter usbFilter = new IntentFilter();
      usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
      usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
      usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
      usbFilter.addDataScheme("file");
      Utils.registerReceiverCompat(this, usbReceiver, usbFilter);
      usbRegistered = true;
    }
  }

  private void unregisterDynamicReceivers() {
    if (batteryController != null) batteryController.stop();
    if (clockController != null) clockController.stop();
    if (usbRegistered) {
      try {
        unregisterReceiver(usbReceiver);
      } catch (IllegalArgumentException ignored) {
      }
      usbRegistered = false;
    }
  }

  // =========================================================================
  // 按键处理 / 返回键
  // =========================================================================

  private void installBackPressHandler() {
    // 桌面拦截返回键：栈空时吞掉，栈非空时回退一层并提交可能修改的字体。
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
          getSupportFragmentManager().popBackStack();
          config.setFontSize(config.getFontSize());
        }
        // 栈空时不调用任何 finish/super —— 桌面应当保持驻留。
      }
    });
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      dataCenter.showLastPage();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      dataCenter.showNextPage();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  // =========================================================================
  // 状态栏/系统应用判断/通知栏
  // =========================================================================

  public void applyStatusBarVisibility() {
    int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.isShowStatusBar()) {
      getWindow().setFlags(flags, flags);
    } else {
      getWindow().clearFlags(flags);
    }
  }

  public boolean isUserApp(PackageInfo pInfo) {
    return (pInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
  }

  private void checkLaunchHomeNotification() {
    Intent service = new Intent(this, HomeEntranceService.class);
    if (config.isReturnNotificationEnabled()) {
      try {
        ContextCompat.startForegroundService(this, service);
      } catch (Exception ignored) {
        // Background-start restrictions on Android 12+ may block this when not foreground.
      }
    } else {
      try {
        stopService(service);
      } catch (Exception ignored) {
      }
    }
  }
}
