package cn.modificator.launcher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;

/**
 * 把当前 app 设为系统默认 home/launcher 的辅助类。
 * - API 29+ 使用 {@link RoleManager#ROLE_HOME} 弹出系统标准对话框
 * - 旧版本回退到设置面板（Settings.ACTION_HOME_SETTINGS / MANAGE_DEFAULT_APPS）
 */
public final class LauncherDefaultHelper {

  private LauncherDefaultHelper() {
  }

  /** 当前 app 是否已经是默认 home。 */
  public static boolean isDefaultHome(Context ctx) {
    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    ResolveInfo info = ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
    if (info == null || info.activityInfo == null) return false;
    String pkg = info.activityInfo.packageName;
    // android:resolver 表示系统会弹选择器（即没有任何默认）
    if ("android".equals(pkg)) return false;
    return ctx.getPackageName().equals(pkg);
  }

  /**
   * 请求成为默认 home。优先使用 RoleManager（标准弹窗），否则跳到系统设置页。
   *
   * @param activity     必须是 androidx ComponentActivity 才能 startActivityForResult via launcher
   * @param roleLauncher 已经 registerForActivityResult 的 ActivityResultLauncher（用于 API 29+ 路径）
   */
  public static void requestDefault(Activity activity, ActivityResultLauncher<Intent> roleLauncher) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      RoleManager rm = activity.getSystemService(RoleManager.class);
      if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
        try {
          roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME));
          return;
        } catch (Exception ignored) {
          // Fall through to settings panel
        }
      }
    }
    openHomeSettings(activity);
  }

  private static void openHomeSettings(Activity activity) {
    try {
      activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)
          .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return;
    } catch (Exception ignored) {
    }
    try {
      activity.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
          .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (Exception ignored) {
    }
  }
}
