package cn.modificator.launcher.model;

import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

/**
 * App 列表过滤策略。
 * <ul>
 *   <li>{@link DeviceProfile#BLOCKED_PREFIXES}：flavor-specific 屏蔽前缀，
 *       通用版基本为空，Supernote 版包含厂商内部 / 服务包名</li>
 *   <li>{@code shouldShow} 检查 {@code hideSystemApps} 开关时，
 *       预装系统应用（无 user-update）也过滤掉</li>
 * </ul>
 */
public final class AppFilter {

  private AppFilter() {
  }

  /**
   * @param info           {@code queryIntentActivities} 返回的一项
   * @param hideSystemApps true 时把所有"预装且未被用户更新"的系统应用过滤掉
   * @return 是否应该显示
   */
  public static boolean shouldShow(ResolveInfo info, boolean hideSystemApps) {
    if (info == null || info.activityInfo == null) return false;
    String pkg = info.activityInfo.packageName;
    if (pkg == null) return false;

    for (String prefix : DeviceProfile.BLOCKED_PREFIXES) {
      if (pkg.startsWith(prefix)) return false;
    }

    if (hideSystemApps) {
      ApplicationInfo ai = info.activityInfo.applicationInfo;
      if (ai != null) {
        int flags = ai.flags;
        boolean isSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean wasUpdated = (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        // 隐藏未被用户更新过的预装应用
        if (isSystem && !wasUpdated) return false;
      }
    }

    return true;
  }
}
