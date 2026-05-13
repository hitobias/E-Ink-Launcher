package cn.modificator.launcher.controllers;

import android.content.Intent;

/**
 * 处理 manifest 中静态声明的 app shortcut 进入。
 * 把 action 解析后委派给 {@link Callbacks}。
 */
public class ShortcutDispatcher {

  public static final String ACTION_SHORTCUT_LOCK =
      "cn.modificator.launcher.action.SHORTCUT_LOCK";
  public static final String ACTION_SHORTCUT_SEARCH =
      "cn.modificator.launcher.action.SHORTCUT_SEARCH";
  public static final String ACTION_SHORTCUT_SETTINGS =
      "cn.modificator.launcher.action.SHORTCUT_SETTINGS";

  public interface Callbacks {
    void onLockShortcut();
    void onSearchShortcut();
    void onSettingsShortcut();
  }

  private ShortcutDispatcher() {
  }

  public static void handle(Intent intent, Callbacks cb) {
    if (intent == null || intent.getAction() == null || cb == null) return;
    switch (intent.getAction()) {
      case ACTION_SHORTCUT_LOCK:
        cb.onLockShortcut();
        break;
      case ACTION_SHORTCUT_SEARCH:
        cb.onSearchShortcut();
        break;
      case ACTION_SHORTCUT_SETTINGS:
        cb.onSettingsShortcut();
        break;
      default:
        break;
    }
  }
}
