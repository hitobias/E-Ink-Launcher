package cn.modificator.launcher.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.R;
import cn.modificator.launcher.model.IconCache;

/**
 * 渲染底部快速啟動 Dock。
 *
 * 設計權衡：
 * - Dock 視作獨立元件——它與主網格 adapter 是並排展示，而非從中過濾。
 *   主網格仍包含全部 app，避免用戶釘住後找不到原本位置。
 * - 每次 refresh() 都重新 inflate 子 view：dock 上限 5 個，重建成本可忽略，
 *   省下保留/回收的狀態管理複雜度。
 * - 不直接依賴 Launcher Activity；用 OnDockAction 回調把點擊/長按外送。
 */
public final class DockManager {

  public interface OnDockAction {
    void onLaunch(String packageName, ResolveInfo info);
    void onLongPress(String packageName, ResolveInfo info);
  }

  private final LinearLayout dockBar;
  private final Config config;
  private final IconCache iconCache;
  private final PackageManager pm;
  private OnDockAction listener;

  public DockManager(LinearLayout dockBar, Config config, IconCache iconCache, PackageManager pm) {
    this.dockBar = dockBar;
    this.config = config;
    this.iconCache = iconCache;
    this.pm = pm;
  }

  public void setListener(OnDockAction listener) {
    this.listener = listener;
  }

  /**
   * 重新渲染 dock：讀 config，移除已解除安裝的包名，按釘住順序填入。
   * 釘住 app 全部解除安裝時隱藏 dock；toggle 關閉時亦隱藏。
   */
  public void refresh() {
    dockBar.removeAllViews();

    if (!config.isDockEnabled()) {
      dockBar.setVisibility(View.GONE);
      return;
    }

    List<String> pinned = new ArrayList<>(config.getDockApps());
    if (pinned.isEmpty()) {
      dockBar.setVisibility(View.GONE);
      return;
    }

    // 解析 ResolveInfo 同時剔除已不存在的包名，並就地寫回 config。
    List<ResolveInfo> resolved = new ArrayList<>();
    Iterator<String> it = pinned.iterator();
    boolean changed = false;
    while (it.hasNext()) {
      String pkg = it.next();
      ResolveInfo info = resolveLauncherInfo(pkg);
      if (info == null) {
        it.remove();
        changed = true;
      } else {
        resolved.add(info);
      }
    }
    if (changed) {
      config.setDockApps(pinned);
    }
    if (resolved.isEmpty()) {
      dockBar.setVisibility(View.GONE);
      return;
    }

    dockBar.setVisibility(View.VISIBLE);
    LayoutInflater inflater = LayoutInflater.from(dockBar.getContext());
    for (int i = 0; i < resolved.size(); i++) {
      final ResolveInfo info = resolved.get(i);
      final String pkg = pinned.get(i);
      View item = inflater.inflate(R.layout.dock_item, dockBar, false);
      ImageView icon = item.findViewById(R.id.dockItemIcon);
      TextView label = item.findViewById(R.id.dockItemLabel);
      icon.setImageDrawable(iconCache.getIcon(pkg, info, pm));
      CharSequence name = config.getRename(pkg);
      if (name == null || name.length() == 0) {
        name = iconCache.getLabel(pkg, info, pm);
      }
      label.setText(name);
      item.setContentDescription(name);
      item.setOnClickListener(v -> {
        if (listener != null) listener.onLaunch(pkg, info);
      });
      item.setOnLongClickListener(v -> {
        if (listener != null) listener.onLongPress(pkg, info);
        return true;
      });
      dockBar.addView(item);
    }
  }

  private ResolveInfo resolveLauncherInfo(String pkg) {
    Intent intent = pm.getLaunchIntentForPackage(pkg);
    if (intent == null) return null;
    return pm.resolveActivity(intent, 0);
  }
}
