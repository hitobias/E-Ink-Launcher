package cn.modificator.launcher.widgets;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.AppFolder;
import cn.modificator.launcher.model.FolderStore;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.BluetoothControl;
import cn.modificator.launcher.model.NotificationCounter;
import cn.modificator.launcher.model.WifiControl;

/**
 * 负责将应用数据绑定到 {@link LauncherAdapter.ItemViewHolder}，
 * 以及处理点击/长按/卸载/隐藏等交互事件。
 * <p>
 * 与 {@link LauncherAdapter} 协作：Adapter 管理 ViewHolder 池和数据列表，
 * Binder 处理"怎样把数据画到 View 上"以及"用户点了之后做什么"。
 */
public class AppItemBinder {

  // =========================================================================
  // 回调接口
  // =========================================================================

  /** 宿主实现此接口以响应用户交互。 */
  public interface Callback {
    /** 普通模式下点击应用 */
    void onItemClick(ResolveInfo info);

    /** 长按应用 */
    void onItemLongClick(View anchor, ResolveInfo info);

    /** 管理模式下点击卸载 */
    void onItemDeleteClick(ResolveInfo info);

    /** 管理模式下切换隐藏状态 */
    void onItemHideToggle(String packageName, boolean hidden);
  }

  // =========================================================================
  // 字段
  // =========================================================================

  private final PackageManager packageManager;
  private final Set<String> hideAppPkg = new HashSet<>();

  private Callback callback;
  private IconCache iconCache;
  private FolderStore folderStore;
  private boolean isDelete = false;
  private boolean notificationBadgeEnabled = false;

  // 当前绑定的数据（由 Adapter 在 bindAll 时传入）
  private List<ResolveInfo> dataRef;

  // =========================================================================
  // 构造器
  // =========================================================================

  public AppItemBinder(PackageManager pm) {
    this.packageManager = pm;
  }

  // =========================================================================
  // 外部依赖
  // =========================================================================

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  public void setIconCache(IconCache iconCache) {
    this.iconCache = iconCache;
  }

  /** 注入文件夹存储；渲染文件夹格子时需要。 */
  public void setFolderStore(FolderStore folderStore) {
    this.folderStore = folderStore;
  }

  /** 切换通知角标功能开关。仅影响后续绑定；调用方需触发 refreshDisplay 让视图重绘。 */
  public void setNotificationBadgeEnabled(boolean enabled) {
    this.notificationBadgeEnabled = enabled;
  }

  public boolean isNotificationBadgeEnabled() {
    return notificationBadgeEnabled;
  }

  // =========================================================================
  // 管理模式
  // =========================================================================

  public void setDelete(boolean delete) {
    this.isDelete = delete;
  }

  public boolean isDelete() {
    return isDelete;
  }

  // =========================================================================
  // 隐藏应用
  // =========================================================================

  public void setHideAppPkg(Set<String> pkgs) {
    hideAppPkg.clear();
    hideAppPkg.addAll(pkgs);
  }

  public Set<String> getHideAppPkg() {
    return hideAppPkg;
  }

  // =========================================================================
  // 绑定入口（包级可见，由 LauncherAdapter 调用）
  // =========================================================================

  /**
   * 绑定所有 ViewHolder。
   *
   * @param holders ViewHolder 列表
   * @param data    当前页要显示的应用列表
   */
  void bindAll(List<LauncherAdapter.ItemViewHolder> holders, List<ResolveInfo> data) {
    this.dataRef = data;
    Map<String, File> customIcons = iconCache != null ? iconCache.getCustomIconMap() : null;
    WifiControl.bind(null, customIcons);
    BluetoothControl.bind(null, customIcons);

    for (int i = 0; i < holders.size(); i++) {
      LauncherAdapter.ItemViewHolder holder = holders.get(i);
      if (i < data.size()) {
        bindItem(holder, i, customIcons);
      } else {
        clearItem(holder);
      }
    }
  }

  /**
   * 仅绑定指定位置的 ViewHolder（增量绑定）。
   */
  void bindOnly(List<LauncherAdapter.ItemViewHolder> holders,
                List<ResolveInfo> data, Set<Integer> positions) {
    this.dataRef = data;
    Map<String, File> customIcons = iconCache != null ? iconCache.getCustomIconMap() : null;
    for (Integer pos : positions) {
      if (pos < 0 || pos >= holders.size()) continue;
      LauncherAdapter.ItemViewHolder holder = holders.get(pos);
      if (pos < data.size()) {
        bindItem(holder, pos, customIcons);
      } else {
        clearItem(holder);
      }
    }
    // updateDeleteState only for changed positions
    updateDeleteState(holders, data);
  }

  /**
   * 更新所有 ViewHolder 的管理模式状态（删除/隐藏按钮可见性）。
   */
  void updateDeleteState(List<LauncherAdapter.ItemViewHolder> holders, List<ResolveInfo> data) {
    for (int i = 0; i < holders.size() && i < data.size(); i++) {
      LauncherAdapter.ItemViewHolder holder = holders.get(i);

      if (!isDelete) {
        holder.menuContainer.setVisibility(View.GONE);
        continue;
      }

      holder.menuContainer.setVisibility(View.VISIBLE);
      String pkg = data.get(i).activityInfo.packageName;

      boolean canDelete = false;
      if (!AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)
          && !AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)
          && !AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(pkg)
          && AppDataCenter.folderIdFromPackage(pkg) == null) {
        try {
          canDelete = (packageManager.getPackageInfo(pkg, 0).applicationInfo.flags
              & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
      }

      holder.menuDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
      holder.menuHide.setSelected(hideAppPkg.contains(pkg));
    }
  }

  // =========================================================================
  // 单项绑定
  // =========================================================================

  private void bindItem(LauncherAdapter.ItemViewHolder holder, int position,
                         Map<String, File> customIcons) {
    ResolveInfo info = dataRef.get(position);
    String pkg = info.activityInfo.packageName;

    // —— 标签 tag (ResolveInfo 作为标识，避免 int 自动装箱) ——
    holder.itemView.setTag(info);
    holder.appImage.setTag(info);
    holder.menuDelete.setTag(info);
    holder.menuHide.setTag(info);

    // —— 图标 & 标签 ——
    String folderId = AppDataCenter.folderIdFromPackage(pkg);
    if (folderId != null && folderStore != null) {
      bindFolderItem(holder, folderId);
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)) {
      WifiControl.bind(holder.itemView, customIcons);
    } else if (AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(pkg)) {
      BluetoothControl.bind(holder.itemView, customIcons);
    } else if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, pkg, R.drawable.ic_onekeylock, customIcons);
      CharSequence newLabel = holder.itemView.getResources().getString(R.string.item_lockscreen);
      if (!TextUtils.equals(holder.appName.getText(), newLabel)) {
        holder.appName.setText(newLabel);
      }
    } else {
      loadIcon(holder.appImage, pkg, info, customIcons);
      CharSequence newLabel = iconCache != null
          ? iconCache.getLabel(pkg, info, packageManager)
          : info.loadLabel(packageManager);
      if (!TextUtils.equals(holder.appName.getText(), newLabel)) {
        holder.appName.setText(newLabel);
      }
    }

    // —— 监听器（复用单例监听器，通过 tag 获取 ResolveInfo） ——
    holder.itemView.setOnClickListener(clickListener);
    holder.itemView.setOnLongClickListener(longClickListener);
    holder.menuDelete.setOnClickListener(deleteClickListener);
    holder.menuHide.setOnClickListener(hideClickListener);

    holder.itemView.setVisibility(View.VISIBLE);
    holder.itemView.setAlpha(1);
  }

  private void clearItem(LauncherAdapter.ItemViewHolder holder) {
    if (!TextUtils.equals(holder.appName.getText(), "")) {
      holder.appName.setText("");
    }
    holder.appImage.setImageDrawable(null);
    holder.itemView.setOnClickListener(null);
    holder.itemView.setOnLongClickListener(null);
    holder.menuDelete.setOnClickListener(null);
    holder.menuHide.setOnClickListener(null);
    holder.itemView.setTag(null);
    holder.appImage.setTag(null);
    holder.menuDelete.setTag(null);
    holder.menuHide.setTag(null);
    holder.itemView.setAlpha(0);
  }

  // =========================================================================
  // 文件夹渲染
  // =========================================================================

  /**
   * 用合成图标和文件夹名渲染 ViewHolder。
   * 安静地处理「文件夹已被删除」「成员包已卸载」等异常状态。
   */
  private void bindFolderItem(LauncherAdapter.ItemViewHolder holder, String folderId) {
    AppFolder folder = folderStore != null ? folderStore.get(folderId) : null;
    String label;
    List<Drawable> miniIcons = new ArrayList<>(4);
    if (folder == null) {
      label = holder.itemView.getResources().getString(R.string.folder_default_name);
    } else {
      label = !folder.name.isEmpty()
          ? folder.name
          : holder.itemView.getResources().getString(R.string.folder_default_name);
      // 收集前 4 个仍可解析的成员图标。
      for (String memberPkg : folder.packages) {
        if (miniIcons.size() >= 4) break;
        Drawable d = resolveMiniIcon(memberPkg);
        if (d != null) miniIcons.add(d);
      }
    }
    holder.appImage.setImageDrawable(new FolderIconDrawable(miniIcons));
    if (!TextUtils.equals(holder.appName.getText(), label)) {
      holder.appName.setText(label);
    }
  }

  /**
   * 解析单个成员包名的图标 drawable，供文件夹合成图标使用。
   * 失败（已卸载等）返回 null，文件夹会少一格。
   */
  private Drawable resolveMiniIcon(String pkg) {
    if (pkg == null || pkg.isEmpty()) return null;
    try {
      return packageManager.getApplicationIcon(pkg);
    } catch (PackageManager.NameNotFoundException e) {
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  // =========================================================================
  // 图标加载
  // =========================================================================

  private void loadIcon(ImageView iv, String pkg, int defaultRes,
                         Map<String, File> customIcons) {
    File custom = customIcons != null ? customIcons.get(pkg) : null;
    if (custom != null) {
      iv.setImageURI(Uri.fromFile(custom));
    } else {
      iv.setImageResource(defaultRes);
    }
    applyBadgeIfNeeded(iv, pkg);
  }

  private void loadIcon(ImageView iv, String pkg, ResolveInfo info,
                         Map<String, File> customIcons) {
    File custom = customIcons != null ? customIcons.get(pkg) : null;
    if (custom != null) {
      iv.setImageURI(Uri.fromFile(custom));
      applyBadgeIfNeeded(iv, pkg);
      return;
    }
    int badgeCount = badgeCountFor(pkg);
    if (badgeCount > 0 && iconCache != null) {
      // 有角标时走同步路径：从缓存里取基础 drawable 再装饰，
      // 避免 loadIconInto 的异步回调把未装饰的图覆盖回来。
      Drawable base = iconCache.getIcon(pkg, info, packageManager);
      Drawable decorated = IconBadge.decorate(iv.getResources(), base, badgeCount);
      iv.setImageDrawable(decorated != null ? decorated : base);
      return;
    }
    if (iconCache != null) {
      iconCache.loadIconInto(iv, pkg, info, packageManager, info);
    } else {
      iv.setImageDrawable(info.loadIcon(packageManager));
    }
  }

  /**
   * 在已加载到 ImageView 的 drawable 上应用角标。
   * 仅用于"自定义图标 / 默认资源"分支，这两种是同步设置的。
   */
  private void applyBadgeIfNeeded(ImageView iv, String pkg) {
    int count = badgeCountFor(pkg);
    if (count <= 0) return;
    Drawable current = iv.getDrawable();
    if (current == null) return;
    Drawable decorated = IconBadge.decorate(iv.getResources(), current, count);
    if (decorated != null && decorated != current) {
      iv.setImageDrawable(decorated);
    }
  }

  private int badgeCountFor(String pkg) {
    if (!notificationBadgeEnabled) return 0;
    return NotificationCounter.getCount(pkg);
  }

  // =========================================================================
  // 点击监听器（单例 + tag 传递 ResolveInfo）
  // =========================================================================

  private final View.OnClickListener clickListener = v -> {
    if (callback == null) return;
    ResolveInfo info = (ResolveInfo) v.getTag();
    if (info == null) return;
    if (isDelete) {
      callback.onItemDeleteClick(info);
    } else {
      callback.onItemClick(info);
    }
  };

  private final View.OnLongClickListener longClickListener = v -> {
    if (callback == null) return false;
    ResolveInfo info = (ResolveInfo) v.getTag();
    if (info == null) return false;
    callback.onItemLongClick(v, info);
    return true;
  };

  private final View.OnClickListener deleteClickListener = v -> {
    if (callback == null) return;
    ResolveInfo info = (ResolveInfo) v.getTag();
    if (info == null) return;
    callback.onItemDeleteClick(info);
  };

  private final View.OnClickListener hideClickListener = v -> {
    ResolveInfo info = (ResolveInfo) v.getTag();
    if (info == null) return;
    String pkg = info.activityInfo.packageName;
    boolean hidden;
    if (hideAppPkg.contains(pkg)) {
      hideAppPkg.remove(pkg);
      hidden = false;
    } else {
      hideAppPkg.add(pkg);
      hidden = true;
    }
    v.setSelected(hidden);
    if (callback != null) {
      callback.onItemHideToggle(pkg, hidden);
    }
  };
}
