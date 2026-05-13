package cn.modificator.launcher.controllers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.view.View;
import android.widget.TextView;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.widgets.BatteryView;

/**
 * 监听 {@link Intent#ACTION_BATTERY_CHANGED}，把电量 / 状态 / 健康渲染到 UI。
 * 节流：相同数值不重复更新（充电时广播频繁，避免墨水屏冗余刷屏）。
 */
public class BatteryController {

  private final Context context;
  private final BatteryView progress;
  private final TextView statusLabel;

  private boolean registered;
  private int lastLevel = Integer.MIN_VALUE;
  private int lastStatus = Integer.MIN_VALUE;
  private int lastHealth = Integer.MIN_VALUE;

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent intent) {
      handle(intent);
    }
  };

  public BatteryController(Context context, BatteryView progress, TextView statusLabel) {
    this.context = context.getApplicationContext();
    this.progress = progress;
    this.statusLabel = statusLabel;
  }

  public void start() {
    if (registered) return;
    Utils.registerReceiverCompat(context, receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    registered = true;
  }

  public void stop() {
    if (!registered) return;
    try {
      context.unregisterReceiver(receiver);
    } catch (IllegalArgumentException ignored) {
    }
    registered = false;
  }

  private void handle(Intent intent) {
    int rawLevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int status = intent.getIntExtra("status", -1);
    int health = intent.getIntExtra("health", -1);

    int level = (rawLevel >= 0 && scale > 0) ? (rawLevel * 100) / scale : -1;

    if (level == lastLevel && status == lastStatus && health == lastHealth) return;
    lastLevel = level;
    lastStatus = status;
    lastHealth = health;

    progress.setProgress(level);
    statusLabel.setVisibility(View.VISIBLE);

    if (BatteryManager.BATTERY_HEALTH_OVERHEAT == health) {
      statusLabel.setText(R.string.battery_heat);
      return;
    }

    switch (status) {
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
        statusLabel.setText(R.string.battery_unknown);
        break;
      case BatteryManager.BATTERY_STATUS_CHARGING:
        statusLabel.setText(R.string.battery_charging);
        break;
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        if (level < 15) {
          statusLabel.setText(R.string.battery_low);
        } else {
          statusLabel.setVisibility(View.GONE);
        }
        break;
      case BatteryManager.BATTERY_STATUS_FULL:
        statusLabel.setText(R.string.battery_full);
        break;
      default:
        statusLabel.setText(R.string.battery_wtf);
        break;
    }
  }
}
