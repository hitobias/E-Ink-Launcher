package cn.modificator.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.core.graphics.drawable.DrawableCompat;

import java.text.DecimalFormat;
import java.util.Calendar;

/**
 * 通用工具类。
 */
public class Utils {

  private static final String[] SIZE_UNITS = {"bytes", "KB", "MB", "GB", "TB"};
  private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("####.00");
  private static final double SIZE_THRESHOLD = 0.8;

  private static final String[] CN_AM_PM = {
      "凌晨", "黎明", "早晨", "上午", "中午", "下午", "晚上", "深夜"
  };

  private Utils() {
  }

  public static Drawable tintDrawable(Drawable drawable, ColorStateList colors) {
    if (drawable == null) return null;
    final Drawable wrappedDrawable = DrawableCompat.wrap(drawable);
    DrawableCompat.setTintList(wrappedDrawable, colors);
    return wrappedDrawable;
  }

  public static String getReadableFileSize(long size) {
    if (size < 1024 * SIZE_THRESHOLD) {
      return size + SIZE_UNITS[0];
    } else if (size < 1024L * 1024 * SIZE_THRESHOLD) {
      return SIZE_FORMAT.format(size / 1024f) + SIZE_UNITS[1];
    } else if (size < 1024L * 1024 * 1024 * SIZE_THRESHOLD) {
      return SIZE_FORMAT.format(size / 1024f / 1024f) + SIZE_UNITS[2];
    } else if (size < 1024L * 1024 * 1024 * 1024 * SIZE_THRESHOLD) {
      return SIZE_FORMAT.format(size / 1024f / 1024f / 1024f) + SIZE_UNITS[3];
    } else {
      return SIZE_FORMAT.format(size / 1024f / 1024f / 1024f / 1024f) + SIZE_UNITS[4];
    }
  }

  public static int dp2Px(Context context, float dp) {
    final float scale = context.getResources().getDisplayMetrics().density;
    return (int) (dp * scale + 0.5f);
  }

  public static String getAMPMCNString(int hours, int ampm) {
    if (ampm == Calendar.AM) {
      if (hours < 5) return CN_AM_PM[0];
      if (hours < 7) return CN_AM_PM[1];
      if (hours < 9) return CN_AM_PM[2];
      if (hours < 12) return CN_AM_PM[3];
      return CN_AM_PM[0];
    } else {
      if (hours == 0 || hours == 12) return CN_AM_PM[4];
      if (hours < 6) return CN_AM_PM[5];
      if (hours <= 9) return CN_AM_PM[6];
      return CN_AM_PM[7];
    }
  }

  /**
   * 默认按系统广播注册（exported），适用于电池/时间/包变更等系统级 action。
   */
  public static void registerReceiverCompat(Context context, BroadcastReceiver receiver,
                                            IntentFilter filter) {
    registerReceiverCompat(context, receiver, filter, /* exported= */ true);
  }

  /**
   * 兼容 Android 13+ 必须显式声明 exported/not-exported 的广播注册。
   *
   * @param exported true 当 filter 中包含系统广播 action；
   *                 false 当只接收应用内部 action（更安全）。
   */
  public static void registerReceiverCompat(Context context, BroadcastReceiver receiver,
                                            IntentFilter filter, boolean exported) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      int flag = exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED;
      context.registerReceiver(receiver, filter, flag);
    } else {
      context.registerReceiver(receiver, filter);
    }
  }
}
