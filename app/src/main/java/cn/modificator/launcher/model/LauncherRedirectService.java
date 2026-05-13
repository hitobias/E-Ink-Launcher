package cn.modificator.launcher.model;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.List;

import cn.modificator.launcher.Config;

/**
 * 監聽 OEM hardwired 啟動器（如 Supernote 的右側滑條目標）並轉發回本 launcher。
 *
 * <p>背景：Supernote 把右側滑條手勢綁在 component name
 * {@code com.ratta.supernote.launcher/.MainActivity}。停用該包後手勢失效，
 * 啟用又會去到原廠 launcher 而非本 app。
 *
 * <p>解法：
 * <ol>
 *   <li>用戶在系統 Apps 設定中重新啟用 SupernoteLauncher。</li>
 *   <li>用戶授予本服務無障礙權限。</li>
 *   <li>滑條手勢觸發 → 原廠 launcher 來到前台 → 本服務收到
 *       {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} → 立即
 *       {@code startActivity(CATEGORY_HOME)} 跳回 E-Ink-Launcher。</li>
 * </ol>
 *
 * <p>抑制連發：300ms 內忽略重複事件，避免在快速連續切換時無限循環。
 */
public final class LauncherRedirectService extends AccessibilityService {

  private static final String TAG = "LauncherRedirect";

  /** 受監聽的 launcher 包名；命中即觸發轉發。 */
  public static final List<String> WATCHED_PACKAGES = Arrays.asList(
      "com.ratta.supernote.launcher"
  );

  private static final long DEBOUNCE_MS = 300L;
  /** 環形緩衝大小：保留最近 N 次前台變更供診斷。 */
  private static final int RING_SIZE = 30;
  /** 最近觀察到的 (timestamp, packageName) — 由所有 instance 共享給 Diagnostics 讀取。 */
  private static final java.util.ArrayDeque<long[]> EVENT_TIMES = new java.util.ArrayDeque<>();
  private static final java.util.ArrayDeque<String> EVENT_PKGS = new java.util.ArrayDeque<>();

  private long lastRedirectAt = 0L;
  private Config config;

  @Override
  public void onCreate() {
    super.onCreate();
    config = new Config(this);
  }

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) info = new AccessibilityServiceInfo();
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
    info.notificationTimeout = 50;
    // 接收所有包的事件：診斷需要看用戶實際觸發的是哪個 package；
    // 真正轉發仍只對 WATCHED_PACKAGES 命中，CPU 影響可忽略（窗口切換頻率低）。
    info.packageNames = null;
    setServiceInfo(info);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null) return;
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    CharSequence pkg = event.getPackageName();
    if (TextUtils.isEmpty(pkg)) return;

    String pkgStr = pkg.toString();
    recordEvent(pkgStr);

    if (config != null && !config.isLauncherRedirectEnabled()) return;
    if (!WATCHED_PACKAGES.contains(pkgStr)) return;

    long now = SystemClock.uptimeMillis();
    if (now - lastRedirectAt < DEBOUNCE_MS) return;
    lastRedirectAt = now;

    Intent home = new Intent(Intent.ACTION_MAIN);
    home.addCategory(Intent.CATEGORY_HOME);
    home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    try {
      startActivity(home);
    } catch (Exception e) {
      Log.w(TAG, "HOME intent failed", e);
    }
  }

  private static synchronized void recordEvent(String pkg) {
    long ts = System.currentTimeMillis();
    EVENT_TIMES.addLast(new long[]{ts});
    EVENT_PKGS.addLast(pkg);
    while (EVENT_PKGS.size() > RING_SIZE) {
      EVENT_TIMES.removeFirst();
      EVENT_PKGS.removeFirst();
    }
  }

  /** 返回最近觀察到的窗口切換事件，最新的在最後。 */
  public static synchronized java.util.List<String> recentEvents() {
    java.util.ArrayList<String> out = new java.util.ArrayList<>(EVENT_PKGS.size());
    java.util.Iterator<long[]> tIt = EVENT_TIMES.iterator();
    java.util.Iterator<String> pIt = EVENT_PKGS.iterator();
    while (tIt.hasNext() && pIt.hasNext()) {
      long ts = tIt.next()[0];
      String pkg = pIt.next();
      out.add(formatTime(ts) + "  " + pkg);
    }
    return out;
  }

  private static String formatTime(long epochMs) {
    java.text.SimpleDateFormat f =
        new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
    return f.format(new java.util.Date(epochMs));
  }

  @Override
  public void onInterrupt() {
    // 系統要求實作但本服務無 ongoing 任務需中斷。
  }

  // ---------------------------------------------------------------------------
  // 啟用狀態查詢（給設定頁用）
  // ---------------------------------------------------------------------------

  /**
   * 本服務是否已在系統「無障礙服務」清單中被啟用。
   *
   * <p>讀 {@link Settings.Secure#ENABLED_ACCESSIBILITY_SERVICES}，逐項比對
   * component name；任何 IllegalState/SecurityException 視為未啟用。
   */
  public static boolean isEnabled(Context context) {
    String enabled = Settings.Secure.getString(
        context.getContentResolver(),
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    if (TextUtils.isEmpty(enabled)) return false;
    ComponentName self = new ComponentName(
        context.getApplicationContext(),
        LauncherRedirectService.class);
    String flat = self.flattenToString();
    String flatShort = self.flattenToShortString();
    for (String entry : enabled.split(":")) {
      if (flat.equalsIgnoreCase(entry) || flatShort.equalsIgnoreCase(entry)) {
        return true;
      }
    }
    return false;
  }
}
