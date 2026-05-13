package cn.modificator.launcher.model;

import java.util.Arrays;
import java.util.List;

/**
 * Supernote 版（supernote flavor）的设备特化常量。
 * <p>
 * 在通用屏蔽前缀基础上额外过滤掉 Chauvet OS 的内部服务 / 工厂测试 / 旧 launcher，
 * 让网格只显示用户真正会用到的应用。
 */
public final class DeviceProfile {

  public static final String FLAVOR_NAME = "supernote";

  public static final List<String> BLOCKED_PREFIXES = Arrays.asList(
      "com.android.theme.icon_pack.",
      "cn.modificator.launcher",
      // Supernote 内部测试 / 服务
      "com.ratta.supernote.supernotefactorytest",
      "com.ratta.supernote.background",
      "com.ratta.supernote.setupwizard",
      "com.ratta.supernote.serverlink",
      "com.ratta.supernote.knowledge",
      // SupernoteLauncher 在我们装上后已无用
      "com.ratta.supernote.launcher"
  );

  private DeviceProfile() {
  }
}
