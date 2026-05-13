package cn.modificator.launcher.model;

import java.util.Arrays;
import java.util.List;

/**
 * 通用版（generic flavor）的设备特化常量。
 * <p>
 * 屏蔽前缀只包含与所有 Android 设备无关的项：
 * 主题图标包 + Launcher 自身。其余厂商私有包名留给具体 flavor 处理。
 */
public final class DeviceProfile {

  public static final String FLAVOR_NAME = "generic";

  public static final List<String> BLOCKED_PREFIXES = Arrays.asList(
      // 主题图标包不是真实应用
      "com.android.theme.icon_pack.",
      // 避免 launcher 出现在自己的网格里
      "cn.modificator.launcher"
  );

  private DeviceProfile() {
  }
}
