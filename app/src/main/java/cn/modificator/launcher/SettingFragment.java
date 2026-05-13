package cn.modificator.launcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;

import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.FontManager;
import cn.modificator.launcher.model.NotificationCounter;
import cn.modificator.launcher.model.WifiControl;

/**
 * 设置页面 Fragment（androidx）。
 */
public class SettingFragment extends Fragment implements View.OnClickListener {

  public interface OnSettingChangeListener {
    void onRowNumChanged(int rowNum);
    void onColNumChanged(int colNum);
    void onFontSizeChanged(float size);
    void onAppNameLinesChanged(int lines);
    void onHideDividerChanged(boolean hide);
    void onShowStatusBarChanged(boolean show);
    void onShowCustomIconChanged(boolean show);
    void onSortModeChanged(int mode);
    void onHideSystemAppsChanged(boolean hide);
    void onReturnNotificationChanged(boolean enabled);
    void onAutoRefreshChanged(int minutes);
    void onPinRecentCountChanged(int n);
    void onNotificationBadgeChanged(boolean enabled);
    void onDockEnabledChanged(boolean enabled);
    void onEnterManageMode();
  }

  private OnSettingChangeListener listener;

  private Spinner colNumSpinner;
  private Spinner rowNumSpinner;
  private Spinner appNameLinesSpinner;
  private Spinner sortModeSpinner;
  private SeekBar fontControl;
  private TextView hideDivider;
  private TextView showStatusBar;
  private TextView showCustomIcon;
  private TextView hideSystemApps;
  private TextView returnNotification;
  private TextView autoRefreshLabel;
  private TextView pinRecentLabel;
  private TextView notificationBadge;
  private TextView dockEnabled;
  private Config config;

  private ActivityResultLauncher<String[]> storagePermissionLauncher;
  private ActivityResultLauncher<String> locationPermissionLauncher;
  private ActivityResultLauncher<String[]> fontPickerLauncher;
  private ActivityResultLauncher<Intent> roleHomeLauncher;
  private ActivityResultLauncher<String> exportConfigLauncher;
  private ActivityResultLauncher<String[]> importConfigLauncher;
  private TextView changeFontLabel;
  private TextView defaultLauncherLabel;
  private Runnable pendingStorageAction;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) context;
    } else {
      throw new ClassCastException(context + " must implement OnSettingChangeListener");
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    storagePermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),
        grants -> {
          Runnable run = pendingStorageAction;
          pendingStorageAction = null;
          if (run != null) run.run();
        });
    locationPermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
          WifiControl.reloadWifiName();
          requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });
    fontPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
          if (uri == null) return;
          installFont(uri);
        });
    roleHomeLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> updateDefaultLauncherLabel());
    exportConfigLauncher = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/json"),
        uri -> {
          if (uri != null) doExportConfig(uri);
        });
    importConfigLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
          if (uri != null) doImportConfig(uri);
        });
    overlayPermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> onOverlayPermissionResult());
  }

  private ActivityResultLauncher<Intent> overlayPermissionLauncher;
  private TextView floatingHome;
  private TextView launcherRedirect;

  private void doExportConfig(android.net.Uri uri) {
    try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
      if (out == null) throw new java.io.IOException("null output stream");
      ConfigBackup.exportTo(requireContext(), out);
      Toast.makeText(requireContext(), R.string.config_export_success, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(requireContext(), R.string.config_export_failed, Toast.LENGTH_LONG).show();
    }
  }

  private void doImportConfig(android.net.Uri uri) {
    try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
      if (in == null) throw new java.io.IOException("null input stream");
      ConfigBackup.importFrom(requireContext(), in);
      Toast.makeText(requireContext(), R.string.config_import_success, Toast.LENGTH_SHORT).show();
      // 重建以便所有缓存字段重新读取
      requireActivity().recreate();
    } catch (Exception e) {
      Toast.makeText(requireContext(), R.string.config_import_failed, Toast.LENGTH_LONG).show();
    }
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    config = new Config(requireContext());
    initViews(view);
    initSpinners();
    initFontControl();
  }

  // =========================================================================
  // 初始化
  // =========================================================================

  private void initViews(View root) {
    root.findViewById(R.id.toBack).setOnClickListener(this);
    root.findViewById(R.id.rootView).setOnClickListener(this);
    root.findViewById(R.id.deleteApp).setOnClickListener(this);
    root.findViewById(R.id.showWifiName).setOnClickListener(this);
    root.findViewById(R.id.btnHideFontControl).setOnClickListener(this);
    root.findViewById(R.id.changeFontSize).setOnClickListener(this);
    root.findViewById(R.id.helpAbout).setOnClickListener(this);
    root.findViewById(R.id.openDeviceManager).setOnClickListener(this);

    showStatusBar = root.findViewById(R.id.showStatusBar);
    showCustomIcon = root.findViewById(R.id.showCustomIcon);
    hideSystemApps = root.findViewById(R.id.hideSystemApps);
    returnNotification = root.findViewById(R.id.returnNotification);
    autoRefreshLabel = root.findViewById(R.id.autoRefresh);
    hideDivider = root.findViewById(R.id.hideDivider);
    fontControl = root.findViewById(R.id.font_control);
    colNumSpinner = root.findViewById(R.id.col_num_spinner);
    rowNumSpinner = root.findViewById(R.id.row_num_spinner);
    appNameLinesSpinner = root.findViewById(R.id.appNameLine);
    sortModeSpinner = root.findViewById(R.id.sortModeSpinner);

    showStatusBar.setOnClickListener(this);
    hideDivider.setOnClickListener(this);
    showCustomIcon.setOnClickListener(this);

    showStatusBar.getPaint().setStrikeThruText(config.isShowStatusBar());
    hideDivider.getPaint().setStrikeThruText(config.isHideDivider());
    hideDivider.setText(config.isHideDivider()
        ? R.string.setting_show_divider
        : R.string.setting_hide_divider);
    showCustomIcon.getPaint().setStrikeThruText(config.isShowCustomIcon());
    if (hideSystemApps != null) {
      hideSystemApps.setOnClickListener(this);
      hideSystemApps.getPaint().setStrikeThruText(config.isHideSystemApps());
    }
    if (returnNotification != null) {
      returnNotification.setOnClickListener(this);
      returnNotification.getPaint().setStrikeThruText(!config.isReturnNotificationEnabled());
    }
    if (autoRefreshLabel != null) {
      autoRefreshLabel.setOnClickListener(this);
      updateAutoRefreshLabel();
    }
    pinRecentLabel = root.findViewById(R.id.pinRecent);
    if (pinRecentLabel != null) {
      pinRecentLabel.setOnClickListener(this);
      updatePinRecentLabel();
    }
    notificationBadge = root.findViewById(R.id.notificationBadge);
    if (notificationBadge != null) {
      notificationBadge.setOnClickListener(this);
      // 实际生效状态 = 配置开启 && 已授予权限；strikethrough 表示"未启用"。
      notificationBadge.getPaint().setStrikeThruText(!isNotificationBadgeActive());
    }
    dockEnabled = root.findViewById(R.id.dockEnabled);
    if (dockEnabled != null) {
      dockEnabled.setOnClickListener(this);
      dockEnabled.getPaint().setStrikeThruText(!config.isDockEnabled());
    }
    floatingHome = root.findViewById(R.id.floatingHome);
    if (floatingHome != null) {
      floatingHome.setOnClickListener(this);
      updateFloatingHomeLabel();
    }
    launcherRedirect = root.findViewById(R.id.launcherRedirect);
    if (launcherRedirect != null) {
      launcherRedirect.setOnClickListener(this);
      updateLauncherRedirectLabel();
    }
    fontControl.setProgress((int) ((config.getFontSize() - 10) * 10));

    changeFontLabel = root.findViewById(R.id.changeFont);
    if (changeFontLabel != null) {
      changeFontLabel.setOnClickListener(this);
      updateFontLabel();
    }

    defaultLauncherLabel = root.findViewById(R.id.setDefaultLauncher);
    if (defaultLauncherLabel != null) {
      defaultLauncherLabel.setOnClickListener(this);
      updateDefaultLauncherLabel();
    }

    View changeLanguage = root.findViewById(R.id.changeLanguage);
    if (changeLanguage != null) {
      changeLanguage.setOnClickListener(this);
    }

    View exportConfig = root.findViewById(R.id.exportConfig);
    if (exportConfig != null) exportConfig.setOnClickListener(this);
    View importConfig = root.findViewById(R.id.importConfig);
    if (importConfig != null) importConfig.setOnClickListener(this);
    View hiddenAppsManager = root.findViewById(R.id.hiddenAppsManager);
    if (hiddenAppsManager != null) hiddenAppsManager.setOnClickListener(this);
    View gestureHelp = root.findViewById(R.id.gestureHelp);
    if (gestureHelp != null) gestureHelp.setOnClickListener(this);
  }

  private void showGestureCheatSheet() {
    String body = getString(R.string.gesture_help_body);
    android.widget.TextView tv = new android.widget.TextView(requireContext());
    tv.setText(body);
    tv.setTextColor(0xff000000);
    tv.setTextSize(14);
    int p = Utils.dp2Px(requireContext(), 16);
    tv.setPadding(p, p, p, p);
    android.widget.ScrollView sv = new android.widget.ScrollView(requireContext());
    sv.addView(tv);
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.setting_gesture_help)
        .setView(sv)
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }

  private void initSpinners() {
    rowNumSpinner.setSelection(config.getRowNum() - 2, false);
    rowNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int rowNum = position + 2;
        config.setRowNum(rowNum);
        listener.onRowNumChanged(rowNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    colNumSpinner.setSelection(config.getColNum() - 2, false);
    colNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int colNum = position + 2;
        config.setColNum(colNum);
        listener.onColNumChanged(colNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    appNameLinesSpinner.setSelection(getAppLineSpinnerSelectPosition(), false);
    appNameLinesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int lines = (position == 3) ? Integer.MAX_VALUE : position;
        config.setAppNameLines(lines);
        listener.onAppNameLinesChanged(lines);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    sortModeSpinner.setSelection(config.getSortMode(), false);
    sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (AppSortComparator.modeNeedsUsageStats(position)
            && !AppSortComparator.hasUsageStatsPermission(requireContext())) {
          Toast.makeText(requireContext(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
          try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
          } catch (Exception ignored) {
          }
          sortModeSpinner.setSelection(config.getSortMode(), false);
          return;
        }
        config.setSortMode(position);
        listener.onSortModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private void initFontControl() {
    fontControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float newSize = 10 + progress / 10f;
          config.setFontSize(newSize);
          listener.onFontSizeChanged(newSize);
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }

  private int getAppLineSpinnerSelectPosition() {
    int lines = config.getAppNameLines();
    return (lines <= 2) ? lines : 3;
  }

  // =========================================================================
  // 点击处理
  // =========================================================================

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      requireActivity().getOnBackPressedDispatcher().onBackPressed();
    } else if (id == R.id.deleteApp) {
      handleDeleteApp();
    } else if (id == R.id.showStatusBar) {
      handleToggleStatusBar();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(requireContext()).show();
    } else if (id == R.id.btnHideFontControl) {
      requireView().findViewById(R.id.menuList).setVisibility(View.VISIBLE);
      requireView().findViewById(R.id.font_control_p).setVisibility(View.GONE);
    } else if (id == R.id.changeFontSize) {
      requireView().findViewById(R.id.menuList).setVisibility(View.GONE);
      requireView().findViewById(R.id.font_control_p).setVisibility(View.VISIBLE);
    } else if (id == R.id.hideDivider) {
      handleToggleDivider();
    } else if (id == R.id.showWifiName) {
      handleShowWifiName();
    } else if (id == R.id.showCustomIcon) {
      handleToggleCustomIcon();
    } else if (id == R.id.hideSystemApps) {
      handleToggleHideSystemApps();
    } else if (id == R.id.returnNotification) {
      handleToggleReturnNotification();
    } else if (id == R.id.autoRefresh) {
      handleAutoRefresh();
    } else if (id == R.id.pinRecent) {
      handlePinRecent();
    } else if (id == R.id.notificationBadge) {
      handleToggleNotificationBadge();
    } else if (id == R.id.dockEnabled) {
      handleToggleDock();
    } else if (id == R.id.floatingHome) {
      handleToggleFloatingHome();
    } else if (id == R.id.launcherRedirect) {
      handleToggleLauncherRedirect();
    } else if (id == R.id.exportConfig) {
      exportConfigLauncher.launch("eink-launcher-config.json");
    } else if (id == R.id.importConfig) {
      importConfigLauncher.launch(new String[]{"application/json", "*/*"});
    } else if (id == R.id.hiddenAppsManager) {
      startActivity(new Intent(requireContext(), HiddenAppsActivity.class));
    } else if (id == R.id.gestureHelp) {
      showGestureCheatSheet();
    } else if (id == R.id.openDeviceManager) {
      try {
        startActivity(new Intent().setComponent(new android.content.ComponentName(
            "com.android.settings",
            "com.android.settings.DeviceAdminSettings")));
      } catch (Exception ignored) {
      }
    } else if (id == R.id.changeFont) {
      handleChangeFont();
    } else if (id == R.id.setDefaultLauncher) {
      handleSetDefaultLauncher();
    } else if (id == R.id.changeLanguage) {
      handleChangeLanguage();
    }
  }

  // 语言选项：tag 用于持久化，label 用各自语言显示。
  private static final String[] LOCALE_TAGS = {
      "", "zh-CN", "zh-TW", "en", "ko", "pl"
  };
  private static final CharSequence[] LOCALE_LABELS = {
      "跟随系统 / Follow System",
      "简体中文",
      "繁體中文",
      "English",
      "한국어",
      "Polski"
  };

  private void handleChangeLanguage() {
    String current = config.getLocaleTag();
    int currentIdx = 0;
    for (int i = 0; i < LOCALE_TAGS.length; i++) {
      if (LOCALE_TAGS[i].equals(current)) {
        currentIdx = i;
        break;
      }
    }
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.setting_language)
        .setSingleChoiceItems(LOCALE_LABELS, currentIdx, (dialog, which) -> {
          dialog.dismiss();
          String tag = LOCALE_TAGS[which];
          if (tag.equals(config.getLocaleTag())) return;
          config.setLocaleTag(tag);
          LocaleManager.applyToProcess(tag);
          requireActivity().recreate();
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void handleSetDefaultLauncher() {
    if (LauncherDefaultHelper.isDefaultHome(requireContext())) {
      Toast.makeText(requireContext(), R.string.setting_is_default_launcher, Toast.LENGTH_SHORT).show();
      return;
    }
    LauncherDefaultHelper.requestDefault(requireActivity(), roleHomeLauncher);
  }

  private void updateDefaultLauncherLabel() {
    if (defaultLauncherLabel == null) return;
    boolean isDefault = LauncherDefaultHelper.isDefaultHome(requireContext());
    defaultLauncherLabel.setText(isDefault
        ? R.string.setting_is_default_launcher
        : R.string.setting_set_default_launcher);
    defaultLauncherLabel.setEnabled(!isDefault);
    defaultLauncherLabel.getPaint().setStrikeThruText(isDefault);
  }

  private void handleChangeFont() {
    String current = config.getFontPath();
    if (current != null) {
      new android.app.AlertDialog.Builder(requireContext())
          .setTitle(R.string.setting_set_font)
          .setItems(new CharSequence[]{
              getString(R.string.setting_set_font),
              getString(R.string.setting_clear_font)
          }, (d, which) -> {
            if (which == 0) launchPicker();
            else clearFont();
          })
          .setNegativeButton(R.string.dialog_cancel, null)
          .show();
    } else {
      launchPicker();
    }
  }

  private void launchPicker() {
    try {
      fontPickerLauncher.launch(new String[]{
          "font/ttf", "font/otf", "application/font-sfnt",
          "application/x-font-ttf", "application/x-font-otf",
          "application/octet-stream", "*/*"
      });
    } catch (Exception e) {
      Toast.makeText(requireContext(), R.string.font_load_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void clearFont() {
    config.clearFontPath();
    FontManager.clear();
    File installed = new File(requireContext().getFilesDir(), "fonts/custom.ttf");
    if (installed.exists()) installed.delete();
    updateFontLabel();
  }

  private void installFont(android.net.Uri uri) {
    File fontsDir = new File(requireContext().getFilesDir(), "fonts");
    if (!fontsDir.exists() && !fontsDir.mkdirs()) {
      Toast.makeText(requireContext(), R.string.font_load_failed, Toast.LENGTH_SHORT).show();
      return;
    }
    File dst = new File(fontsDir, "custom.ttf");
    try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
         java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
      if (in == null) throw new java.io.IOException("null input stream");
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    } catch (Exception e) {
      Toast.makeText(requireContext(), R.string.font_load_failed, Toast.LENGTH_LONG).show();
      return;
    }
    if (FontManager.load(dst)) {
      config.setFontPath(dst.getAbsolutePath());
      updateFontLabel();
    } else {
      dst.delete();
      Toast.makeText(requireContext(), R.string.font_load_failed, Toast.LENGTH_LONG).show();
    }
  }

  private void updateFontLabel() {
    if (changeFontLabel == null) return;
    changeFontLabel.setText(config.getFontPath() != null
        ? R.string.setting_set_font
        : R.string.setting_set_font);
  }

  private void handleDeleteApp() {
    listener.onEnterManageMode();
    requireActivity().getOnBackPressedDispatcher().onBackPressed();
  }

  private void handleToggleStatusBar() {
    boolean newValue = !config.isShowStatusBar();
    config.setShowStatusBar(newValue);
    listener.onShowStatusBarChanged(newValue);
    requireActivity().getOnBackPressedDispatcher().onBackPressed();
  }

  private void handleToggleDivider() {
    boolean newValue = !config.isHideDivider();
    config.setHideDivider(newValue);
    hideDivider.setText(newValue ? R.string.setting_show_divider : R.string.setting_hide_divider);
    listener.onHideDividerChanged(newValue);
    requireActivity().getOnBackPressedDispatcher().onBackPressed();
  }

  private void handleShowWifiName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    } else {
      WifiControl.reloadWifiName();
      requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
  }

  private void handleToggleCustomIcon() {
    withStoragePermission(() -> {
      boolean newValue = !config.isShowCustomIcon();
      config.setShowCustomIcon(newValue);
      listener.onShowCustomIconChanged(newValue);
      requireActivity().getOnBackPressedDispatcher().onBackPressed();
    });
  }

  private void handleToggleHideSystemApps() {
    boolean newValue = !config.isHideSystemApps();
    config.setHideSystemApps(newValue);
    if (hideSystemApps != null) {
      hideSystemApps.getPaint().setStrikeThruText(newValue);
      hideSystemApps.invalidate();
    }
    listener.onHideSystemAppsChanged(newValue);
  }

  private void handleToggleReturnNotification() {
    boolean newValue = !config.isReturnNotificationEnabled();
    config.setReturnNotificationEnabled(newValue);
    if (returnNotification != null) {
      returnNotification.getPaint().setStrikeThruText(!newValue);
      returnNotification.invalidate();
    }
    listener.onReturnNotificationChanged(newValue);
  }

  // 选项：关闭 / 5 / 10 / 30 分钟 — 对应 setting_auto_refresh_options 数组。
  private static final int[] AUTO_REFRESH_VALUES = {0, 5, 10, 30};

  private void handleAutoRefresh() {
    CharSequence[] labels = new CharSequence[]{
        getString(R.string.setting_auto_refresh_off),
        "5 " + getString(R.string.unit_minute),
        "10 " + getString(R.string.unit_minute),
        "30 " + getString(R.string.unit_minute),
    };
    int current = config.getAutoRefreshMinutes();
    int currentIdx = 0;
    for (int i = 0; i < AUTO_REFRESH_VALUES.length; i++) {
      if (AUTO_REFRESH_VALUES[i] == current) {
        currentIdx = i;
        break;
      }
    }
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.setting_auto_refresh)
        .setSingleChoiceItems(labels, currentIdx, (dialog, which) -> {
          dialog.dismiss();
          int chosen = AUTO_REFRESH_VALUES[which];
          config.setAutoRefreshMinutes(chosen);
          updateAutoRefreshLabel();
          listener.onAutoRefreshChanged(chosen);
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updateAutoRefreshLabel() {
    if (autoRefreshLabel == null) return;
    int min = config.getAutoRefreshMinutes();
    String value = min == 0
        ? getString(R.string.setting_auto_refresh_off)
        : min + " " + getString(R.string.unit_minute);
    autoRefreshLabel.setText(getString(R.string.setting_auto_refresh) + ": " + value);
  }

  private static final int[] PIN_RECENT_VALUES = {0, 3, 5, 8};

  private void handlePinRecent() {
    // 需要 PACKAGE_USAGE_STATS 权限 —— 复用排序模式的检查逻辑。
    if (!cn.modificator.launcher.model.AppSortComparator.hasUsageStatsPermission(requireContext())) {
      Toast.makeText(requireContext(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
      try {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
      } catch (Exception ignored) {
      }
      return;
    }
    CharSequence[] labels = new CharSequence[]{
        getString(R.string.setting_pin_recent_off),
        "3", "5", "8"
    };
    int current = config.getPinRecentCount();
    int currentIdx = 0;
    for (int i = 0; i < PIN_RECENT_VALUES.length; i++) {
      if (PIN_RECENT_VALUES[i] == current) {
        currentIdx = i;
        break;
      }
    }
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.setting_pin_recent)
        .setSingleChoiceItems(labels, currentIdx, (dialog, which) -> {
          dialog.dismiss();
          int chosen = PIN_RECENT_VALUES[which];
          config.setPinRecentCount(chosen);
          updatePinRecentLabel();
          listener.onPinRecentCountChanged(chosen);
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updatePinRecentLabel() {
    if (pinRecentLabel == null) return;
    int n = config.getPinRecentCount();
    String value = n == 0 ? getString(R.string.setting_pin_recent_off) : String.valueOf(n);
    pinRecentLabel.setText(getString(R.string.setting_pin_recent) + ": " + value);
  }

  /** 通知角标是否真正生效：配置启用 + 系统已授予通知访问权限。 */
  private boolean isNotificationBadgeActive() {
    return config.isNotificationBadgeEnabled()
        && NotificationCounter.hasAccess(requireContext());
  }

  private void updateNotificationBadgeLabel() {
    if (notificationBadge == null) return;
    notificationBadge.getPaint().setStrikeThruText(!isNotificationBadgeActive());
    notificationBadge.invalidate();
  }

  private void handleToggleNotificationBadge() {
    boolean current = config.isNotificationBadgeEnabled();
    boolean newValue = !current;
    if (newValue && !NotificationCounter.hasAccess(requireContext())) {
      // 首次启用且权限未授予：提示并跳转系统设置；不修改 config
      Toast.makeText(requireContext(),
          R.string.notification_badge_need_access, Toast.LENGTH_LONG).show();
      try {
        startActivity(NotificationCounter.buildSettingsIntent());
      } catch (Exception ignored) {
      }
      // 不立即落盘 newValue —— 等用户从设置页回来后下次再点会真正开启；
      // 这样可以避免"开关说开了但其实没权限"的状态错乱。
      return;
    }
    config.setNotificationBadgeEnabled(newValue);
    // 不动 component 启停状态 —— 让系统的 NotificationListener 始终可用，
    // 仅用 config 控制 UI 是否绘制角标。这样关闭后再开启不必重新授权。
    updateNotificationBadgeLabel();
    listener.onNotificationBadgeChanged(newValue);
  }

  private void handleToggleDock() {
    boolean newValue = !config.isDockEnabled();
    config.setDockEnabled(newValue);
    if (dockEnabled != null) {
      dockEnabled.getPaint().setStrikeThruText(!newValue);
      dockEnabled.invalidate();
    }
    listener.onDockEnabledChanged(newValue);
  }

  /**
   * 切換懸浮 Home 按鈕。首次開啟若無 overlay 權限，先跳系統授權頁；
   * 用戶回到本頁時 {@link #onOverlayPermissionResult()} 收尾。
   */
  private void handleToggleFloatingHome() {
    boolean current = config.isFloatingHomeEnabled();
    if (current) {
      config.setFloatingHomeEnabled(false);
      cn.modificator.launcher.model.FloatingHomeService.stop(requireContext());
      Toast.makeText(requireContext(),
          R.string.floating_home_disabled_toast, Toast.LENGTH_SHORT).show();
      updateFloatingHomeLabel();
      return;
    }
    if (!cn.modificator.launcher.model.FloatingHomeService.canDrawOverlays(requireContext())) {
      Toast.makeText(requireContext(),
          R.string.floating_home_need_permission, Toast.LENGTH_LONG).show();
      Intent grant = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          android.net.Uri.parse("package:" + requireContext().getPackageName()));
      try {
        overlayPermissionLauncher.launch(grant);
      } catch (Exception ignored) {
      }
      return;
    }
    enableFloatingHomeNow();
  }

  private void onOverlayPermissionResult() {
    // 用戶從系統授權頁回來：若已授權則直接啟用；否則無動作（用戶下次再點即可）。
    if (cn.modificator.launcher.model.FloatingHomeService.canDrawOverlays(requireContext())) {
      enableFloatingHomeNow();
    }
  }

  private void enableFloatingHomeNow() {
    config.setFloatingHomeEnabled(true);
    cn.modificator.launcher.model.FloatingHomeService.start(requireContext());
    Toast.makeText(requireContext(),
        R.string.floating_home_enabled_toast, Toast.LENGTH_LONG).show();
    updateFloatingHomeLabel();
  }

  private void updateFloatingHomeLabel() {
    if (floatingHome == null) return;
    floatingHome.getPaint().setStrikeThruText(!config.isFloatingHomeEnabled());
    floatingHome.invalidate();
  }

  /**
   * 接管 Supernote 右側滑條：先彈說明對話框告知用戶要重新啟用原廠 launcher
   * + 授予無障礙權限；同意後跳到系統無障礙設定頁，用戶回來後 onResume 再
   * 重新讀取狀態並更新標籤。
   */
  private void handleToggleLauncherRedirect() {
    boolean current = config.isLauncherRedirectEnabled();
    if (current) {
      config.setLauncherRedirectEnabled(false);
      Toast.makeText(requireContext(),
          R.string.launcher_redirect_disabled_toast, Toast.LENGTH_SHORT).show();
      updateLauncherRedirectLabel();
      return;
    }
    // 先設置 config flag——AccessibilityService 啟用後立即生效，不必再回到設定頁。
    config.setLauncherRedirectEnabled(true);
    // 3 顆按鈕：
    // - 正向：跳系統「無障礙服務」設定（第 2 步：授權）
    // - 中性：直接打開 SupernoteLauncher 的「應用資訊」（第 1 步：啟用該包）
    // - 取消：回退 config
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.setting_launcher_redirect)
        .setMessage(R.string.launcher_redirect_steps)
        .setNeutralButton(R.string.launcher_redirect_open_supernote, (d, w) ->
            openSupernoteLauncherAppInfo())
        .setPositiveButton(R.string.launcher_redirect_open_accessibility, (d, w) -> {
          try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          } catch (Exception ignored) {
          }
        })
        .setNegativeButton(R.string.dialog_cancel, (d, w) -> {
          config.setLauncherRedirectEnabled(false);
          updateLauncherRedirectLabel();
        })
        .show();
  }

  /**
   * 直接跳到 SupernoteLauncher 的「應用資訊」頁，用戶可一鍵點「啟用」。
   * 系統不一定每個 ROM 都支援 disabled package 的詳情頁，失敗時退回全 app 列表。
   */
  private void openSupernoteLauncherAppInfo() {
    Intent direct = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.parse("package:com.ratta.supernote.launcher"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(direct);
      return;
    } catch (Exception ignored) {
    }
    Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(fallback);
    } catch (Exception ignored) {
    }
  }

  private void updateLauncherRedirectLabel() {
    if (launcherRedirect == null) return;
    // 配置開啟 && 系統無障礙權限已授予 = 真正啟用；strikethrough 表示「未啟用」。
    boolean active = config.isLauncherRedirectEnabled()
        && cn.modificator.launcher.model.LauncherRedirectService.isEnabled(requireContext());
    launcherRedirect.getPaint().setStrikeThruText(!active);
    launcherRedirect.invalidate();
  }

  /**
   * 在最新平台上请求合适的存储/媒体权限，授权后执行回调。
   */
  private void withStoragePermission(Runnable next) {
    String required = pickStoragePermission();
    if (required == null
        || ContextCompat.checkSelfPermission(requireContext(), required)
        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      next.run();
      return;
    }
    pendingStorageAction = next;
    storagePermissionLauncher.launch(new String[]{required});
  }

  private String pickStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return Manifest.permission.READ_MEDIA_IMAGES;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Manifest.permission.READ_EXTERNAL_STORAGE;
    }
    return null;
  }

  // =========================================================================
  // 生命周期
  // =========================================================================

  @Override
  public void onResume() {
    super.onResume();
    updateDefaultLauncherLabel();
    updateNotificationBadgeLabel();
    // 用戶從系統無障礙設定頁回來時刷新接管狀態標籤。
    updateLauncherRedirectLabel();
  }
}
