package cn.modificator.launcher.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用文件夹模型：单格文件夹，最多在图标里显示 4 个成员的迷你图标拼接。
 * <p>
 * 字段是公开可变的，仅用于在 {@link FolderStore} 内修改后回写。
 * 调用方不应直接持有并 mutate 远处取到的实例：
 * 修改请使用 {@link FolderStore#save(AppFolder)}。
 */
public class AppFolder {

  /** 唯一 ID（UUID 字符串），创建后不变。 */
  public final String id;

  /** 用户可见名字。允许为空字符串（界面会回退到默认名）。 */
  public String name;

  /** 成员包名，按用户加入顺序保留。 */
  public final List<String> packages = new ArrayList<>();

  public AppFolder(String id, String name) {
    this.id = id;
    this.name = name == null ? "" : name;
  }
}
