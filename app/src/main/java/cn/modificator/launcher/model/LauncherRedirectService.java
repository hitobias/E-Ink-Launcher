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
    // 限制 packageNames：系統只把這些包的事件送來，CPU 友好。
    info.packageNames = WATCHED_PACKAGES.toArray(new String[0]);
    setServiceInfo(info);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null) return;
    if (config != null && !config.isLauncherRedirectEnabled()) return;
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

    CharSequence pkg = event.getPackageName();
    if (TextUtils.isEmpty(pkg)) return;
    if (!WATCHED_PACKAGES.contains(pkg.toString())) return;

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
