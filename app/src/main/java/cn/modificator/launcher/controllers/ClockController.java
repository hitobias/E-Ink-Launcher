package cn.modificator.launcher.controllers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import cn.modificator.launcher.Utils;

/**
 * 时钟显示：监听 {@link Intent#ACTION_TIME_TICK}（每分钟广播），更新 textClock。
 * 中文 locale 下显示"凌晨/上午/下午/晚上"等时段词。
 */
public class ClockController {

  private final Context context;
  private final TextView clockText;
  private final boolean isChinaLocale;
  private final Calendar calendar = Calendar.getInstance();

  private boolean registered;
  private SimpleDateFormat cached24h;
  private SimpleDateFormat cached12h;

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent intent) {
      update();
    }
  };

  public ClockController(Context context, TextView clockText, boolean isChinaLocale) {
    this.context = context.getApplicationContext();
    this.clockText = clockText;
    this.isChinaLocale = isChinaLocale;
  }

  public void start() {
    if (registered) return;
    Utils.registerReceiverCompat(context, receiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    registered = true;
    update();
  }

  public void stop() {
    if (!registered) return;
    try {
      context.unregisterReceiver(receiver);
    } catch (IllegalArgumentException ignored) {
    }
    registered = false;
  }

  public void update() {
    if (clockText == null) return;
    boolean is24Hour = DateFormat.is24HourFormat(context);
    calendar.setTimeInMillis(System.currentTimeMillis());

    SimpleDateFormat fmt = pickFormat(is24Hour);
    String text = fmt.format(calendar.getTime());
    if (!is24Hour && isChinaLocale) {
      text = Utils.getAMPMCNString(calendar.get(Calendar.HOUR), calendar.get(Calendar.AM_PM)) + text;
    }
    clockText.setText(text);
  }

  private SimpleDateFormat pickFormat(boolean is24Hour) {
    if (is24Hour) {
      if (cached24h == null) {
        cached24h = new SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.getDefault());
      }
      return cached24h;
    }
    if (cached12h == null) {
      String pattern = isChinaLocale ? "yyyy-MM-dd hh:mm EEEE" : "yyyy-MM-dd hh:mm a EEEE";
      cached12h = new SimpleDateFormat(pattern, Locale.getDefault());
    }
    return cached12h;
  }
}
