package cn.modificator.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 全局异常捕获处理器。
 */
public class CrashCapture implements Thread.UncaughtExceptionHandler {

  private static final String TAG = "CrashCapture";
  private static final CrashCapture INSTANCE = new CrashCapture();
  private static final SimpleDateFormat DATE_FORMAT =
      new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());

  private Thread.UncaughtExceptionHandler defaultHandler;
  private Context appContext;
  private final Map<String, String> deviceInfo = new HashMap<>();

  private CrashCapture() {
  }

  public static CrashCapture getInstance() {
    return INSTANCE;
  }

  public void init(Context context) {
    appContext = context.getApplicationContext();
    defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    Log.e(TAG, "Uncaught exception on thread " + thread.getName(), ex);

    String logFile = null;
    try {
      collectDeviceInfo();
      logFile = saveCrashInfo(ex);
    } catch (Throwable collectError) {
      Log.e(TAG, "Failed to record crash details", collectError);
    }

    try {
      Intent crashIntent = new Intent(appContext, CrashDetailPage.class);
      crashIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      if (!TextUtils.isEmpty(logFile)) {
        crashIntent.putExtra("crashFile", logFile);
      }
      appContext.startActivity(crashIntent);
    } catch (Throwable startError) {
      Log.e(TAG, "Failed to start CrashDetailPage", startError);
      if (defaultHandler != null) {
        defaultHandler.uncaughtException(thread, ex);
        return;
      }
    }

    android.os.Process.killProcess(android.os.Process.myPid());
    System.exit(10);
  }

  private void collectDeviceInfo() {
    try {
      PackageManager pm = appContext.getPackageManager();
      PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
      if (pi != null) {
        deviceInfo.put("versionName", pi.versionName != null ? pi.versionName : "null");
        long longCode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ? pi.getLongVersionCode()
            : pi.versionCode;
        deviceInfo.put("versionCode", String.valueOf(longCode));
      }
    } catch (PackageManager.NameNotFoundException ignored) {
    }
    deviceInfo.put("osVersion", Build.VERSION.RELEASE);
    deviceInfo.put("sdkCode", String.valueOf(Build.VERSION.SDK_INT));
    deviceInfo.put("FINGERPRINT", Build.FINGERPRINT);
    deviceInfo.put("DISPLAY", Build.DISPLAY);
  }

  private String saveCrashInfo(Throwable ex) {
    StringBuilder sb = new StringBuilder();

    for (Map.Entry<String, String> entry : deviceInfo.entrySet()) {
      sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\r\n");
    }

    StringWriter writer = new StringWriter();
    PrintWriter pw = new PrintWriter(writer);
    ex.printStackTrace(pw);
    Throwable cause = ex.getCause();
    while (cause != null) {
      cause.printStackTrace(pw);
      cause = cause.getCause();
    }
    pw.close();
    sb.append(writer);

    String fileName = "crash-" + BuildConfig.VERSION_NAME
        + "-" + Build.DEVICE
        + "-" + Build.PRODUCT
        + "-" + Build.TYPE
        + "-" + DATE_FORMAT.format(new Date())
        + "-" + System.currentTimeMillis() + ".log";

    File dir = appContext.getExternalFilesDir("crash");
    if (dir == null) return null;

    if (!dir.exists() && !dir.mkdirs()) {
      Log.w(TAG, "Failed to create crash dir " + dir);
    }

    try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
      fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      return fileName;
    } catch (IOException e) {
      Log.e(TAG, "Failed to write crash log", e);
      return null;
    }
  }
}
