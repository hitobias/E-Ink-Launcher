package cn.modificator.launcher.controllers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import cn.modificator.launcher.R;
import cn.modificator.launcher.model.AdminReceiver;

/**
 * 一键锁屏 + 设备管理员引导 + 电源菜单（关机/重启）。
 */
public class DeviceAdminController {

  private final Activity activity;
  private final DevicePolicyManager policyManager;

  public DeviceAdminController(Activity activity) {
    this.activity = activity;
    this.policyManager = (DevicePolicyManager)
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
  }

  public void lockScreen() {
    try {
      if (policyManager != null
          && policyManager.isAdminActive(new ComponentName(activity, AdminReceiver.class))) {
        policyManager.lockNow();
      } else {
        requestDeviceAdmin();
      }
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void requestDeviceAdmin() {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
        new ComponentName(activity, AdminReceiver.class));
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
        activity.getString(R.string.launch_devicemanager_failed));
    activity.startActivity(intent);
  }

  private void showDeviceAdminDialog() {
    new AlertDialog.Builder(activity)
        .setTitle(R.string.launch_failed)
        .setMessage(R.string.launch_devicemanager_failed)
        .setPositiveButton(R.string.launch_devicemanager, (dialog, which) -> {
          try {
            Intent intent = Intent.parseUri(
                "intent:#Intent;component=com.android.settings/.DeviceAdminSettings;end",
                Intent.URI_INTENT_SCHEME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
          } catch (Exception ignored) {
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  /**
   * 显示关机/重启菜单。仅在 isSystemApp 为 true 时才弹出（普通安装无相应权限）。
   */
  public void showPowerMenu(boolean isSystemApp) {
    if (!isSystemApp) return;
    new AlertDialog.Builder(activity)
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, (dialog, which) -> {
          if (which == 0) {
            Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
              activity.startActivity(intent);
            } catch (Exception ignored) {
            }
          } else {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            try {
              if (pm != null) pm.reboot(null);
            } catch (SecurityException ignored) {
              // 普通安装没 REBOOT 权限
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }
}
