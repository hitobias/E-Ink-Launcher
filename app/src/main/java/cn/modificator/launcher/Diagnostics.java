package cn.modificator.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;

import java.util.Locale;

/**
 * 收集设备 / app 诊断信息字符串。
 * 由"关于"对话框长按 app 名字触发，方便用户提报问题时附带。
 */
public final class Diagnostics {

  private Diagnostics() {
  }

  public static String collect(Context context) {
    StringBuilder sb = new StringBuilder();
    sb.append("App: ").append(appVersion(context)).append('\n');
    sb.append("Locale: ").append(Locale.getDefault()).append('\n');
    sb.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
    sb.append("Model: ").append(Build.MODEL).append('\n');
    sb.append("Product: ").append(Build.PRODUCT).append('\n');
    sb.append("Device: ").append(Build.DEVICE).append('\n');
    sb.append("Fingerprint: ").append(Build.FINGERPRINT).append('\n');
    sb.append("Android: ").append(Build.VERSION.RELEASE)
        .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
    sb.append("Default home: ")
        .append(LauncherDefaultHelper.isDefaultHome(context)).append('\n');
    sb.append(memoryLine());
    return sb.toString();
  }

  private static String appVersion(Context ctx) {
    try {
      PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
      long longCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
          ? pi.getLongVersionCode()
          : pi.versionCode;
      return pi.versionName + " (" + longCode + ")";
    } catch (PackageManager.NameNotFoundException e) {
      return "?";
    }
  }

  private static String memoryLine() {
    Runtime r = Runtime.getRuntime();
    long total = r.totalMemory();
    long free = r.freeMemory();
    long max = r.maxMemory();
    long nativeHeap = Debug.getNativeHeapAllocatedSize();
    return String.format(Locale.US,
        "Memory: used=%dMB free=%dMB max=%dMB native=%dMB\n",
        (total - free) / (1024 * 1024),
        free / (1024 * 1024),
        max / (1024 * 1024),
        nativeHeap / (1024 * 1024));
  }
}
