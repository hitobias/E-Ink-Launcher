package cn.modificator.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

import cn.modificator.launcher.model.LauncherRedirectService;

/**
 * 集中產生「接管 Supernote 右側滑條」功能的診斷報告。
 *
 * <p>用途：當用戶反映右側滑條沒反應時，一鍵展示：
 * <ul>
 *   <li>SupernoteLauncher 是否安裝 / 啟用狀態</li>
 *   <li>本 app 的 AccessibilityService 是否真的已被系統 bind</li>
 *   <li>config flag 是否打開</li>
 *   <li>最近 30 次窗口切換事件（可看出滑條到底觸發了什麼 package）</li>
 * </ul>
 */
public final class LauncherRedirectDiagnostics {

  private static final String SUPERNOTE_LAUNCHER = "com.ratta.supernote.launcher";

  private LauncherRedirectDiagnostics() {}

  public static String build(Context context) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Redirect Diagnostics ===\n\n");

    sb.append("SupernoteLauncher (").append(SUPERNOTE_LAUNCHER).append("):\n");
    sb.append("  ").append(describePackage(context, SUPERNOTE_LAUNCHER)).append("\n\n");

    Config config = new Config(context);
    sb.append("Config.isLauncherRedirectEnabled: ")
        .append(config.isLauncherRedirectEnabled()).append("\n");
    sb.append("Accessibility service granted: ")
        .append(LauncherRedirectService.isEnabled(context)).append("\n\n");

    sb.append("Recent window-state events (newest last):\n");
    List<String> events = LauncherRedirectService.recentEvents();
    if (events.isEmpty()) {
      sb.append("  (none yet — try the right-slider gesture now,\n");
      sb.append("   then long-press \"Redirect Supernote Right-Slider\" again)\n");
    } else {
      for (String e : events) {
        sb.append("  ").append(e).append("\n");
      }
    }
    return sb.toString();
  }

  /** 三種狀態：不存在 / 已停用 / 啟用。輔助判斷用戶有沒有真的啟用回 SupernoteLauncher。 */
  private static String describePackage(Context context, String pkg) {
    PackageManager pm = context.getPackageManager();
    try {
      // GET_DISABLED_COMPONENTS 等 deprecated flag 在新 API 下用 MATCH_DISABLED_COMPONENTS
      int flags = PackageManager.MATCH_DISABLED_COMPONENTS
          | PackageManager.MATCH_UNINSTALLED_PACKAGES;
      ApplicationInfo info = pm.getApplicationInfo(pkg, flags);
      boolean installed = (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
      if (!installed) return "NOT INSTALLED";
      return info.enabled ? "ENABLED" : "DISABLED (re-enable in its App Info)";
    } catch (PackageManager.NameNotFoundException e) {
      return "NOT FOUND on this device";
    }
  }
}
