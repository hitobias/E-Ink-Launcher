package cn.modificator.launcher.model;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort e-ink fast / partial refresh helper.
 *
 * <p>Attempts to invoke known EPD vendor SDKs via reflection so we don't need
 * compile-time dependencies. Returns {@code false} when no vendor API accepts
 * the request, allowing the caller to fall back to a manual black/white flash.
 */
public final class EpdRefresh {

  private static final String TAG = "EpdRefresh";

  // ConcurrentHashMap 不接受 null value，所以負快取另存在 *_MISSES Set 裡。
  // 直接 put(key, null) 會抛 NullPointerException，已造成 Supernote 啟動 crash。
  private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
  private static final Set<String> METHOD_MISSES = ConcurrentHashMap.newKeySet();
  private static final Map<String, Object> ENUM_CACHE = new ConcurrentHashMap<>();
  private static final Set<String> ENUM_MISSES = ConcurrentHashMap.newKeySet();

  private EpdRefresh() {}

  /**
   * Attempt a fast/partial refresh of the given view.
   *
   * @return true if a vendor API accepted the request, false otherwise.
   */
  public static boolean fast(View target) {
    if (target == null) return false;
    target.invalidate();
    return tryOnyxClassic(target, true)
        || tryOnyxNew(target, true)
        || trySupernote(target, true)
        || tryMiui(target, true);
  }

  /**
   * Attempt a full GC16 / A1 refresh (clears ghosting).
   *
   * @return true if a vendor API accepted the request, false otherwise.
   */
  public static boolean full(View target) {
    if (target == null) return false;
    target.invalidate();
    return tryOnyxClassic(target, false)
        || tryOnyxNew(target, false)
        || trySupernote(target, false)
        || tryMiui(target, false);
  }

  // ===========================================================================
  // Vendor attempts
  // ===========================================================================

  /**
   * Onyx Boox classic SDK:
   * com.onyx.android.sdk.api.device.epd.EpdController#invalidate(View, UpdateMode)
   * with UpdateMode.A2 (fast) or UpdateMode.GC16 (full).
   */
  private static boolean tryOnyxClassic(View view, boolean fast) {
    final String klass = "com.onyx.android.sdk.api.device.epd.EpdController";
    final String enumKlass = "com.onyx.android.sdk.api.device.epd.UpdateMode";
    final String enumName = fast ? "A2" : "GC16";

    Object mode = resolveEnumConstant(enumKlass, enumName);
    if (mode == null) return false;

    Class<?> updateModeCls = mode.getClass();
    Method m = resolveMethod(klass, "invalidate", View.class, updateModeCls);
    if (m == null) {
      m = resolveMethod(klass, "invalidate", View.class, updateModeCls.getSuperclass());
    }
    if (m == null) return false;

    try {
      m.invoke(null, view, mode);
      return true;
    } catch (Throwable t) {
      Log.d(TAG, "OnyxClassic invoke failed", t);
      return false;
    }
  }

  /**
   * Onyx Boox new SDK:
   * com.onyx.android.sdk.utils.EpdUtils#applyA2Update(View) (fast)
   * com.onyx.android.sdk.utils.EpdUtils#applyGCUpdate(View) (full)
   */
  private static boolean tryOnyxNew(View view, boolean fast) {
    final String klass = "com.onyx.android.sdk.utils.EpdUtils";
    final String method = fast ? "applyA2Update" : "applyGCUpdate";
    Method m = resolveMethod(klass, method, View.class);
    if (m == null) return false;
    try {
      m.invoke(null, view);
      return true;
    } catch (Throwable t) {
      Log.d(TAG, "OnyxNew invoke failed", t);
      return false;
    }
  }

  /**
   * Supernote / Ratta — undocumented best-effort attempts.
   */
  private static boolean trySupernote(View view, boolean fast) {
    if (invokeRefresh("com.ratta.supernote.background.EpdManager", "refresh", view)) return true;
    if (invokeRefresh("com.ratta.supernote.api.EpdManager", "refresh", view)) return true;

    Method m = resolveMethod(
        "com.ratta.supernote.sdk.EpdController", "invalidate", View.class, int.class);
    if (m != null) {
      try {
        m.invoke(null, view, fast ? 1 : 0);
        return true;
      } catch (Throwable t) {
        Log.d(TAG, "Supernote EpdController invoke failed", t);
      }
    }
    return false;
  }

  /**
   * MIUI EpdHelper: com.miui.epd.MiuiEpdHelper#requestRefresh(View, int)
   */
  private static boolean tryMiui(View view, boolean fast) {
    Method m = resolveMethod("com.miui.epd.MiuiEpdHelper", "requestRefresh", View.class, int.class);
    if (m == null) return false;
    try {
      m.invoke(null, view, fast ? 1 : 0);
      return true;
    } catch (Throwable t) {
      Log.d(TAG, "MIUI invoke failed", t);
      return false;
    }
  }

  // ===========================================================================
  // Reflection helpers (cached)
  // ===========================================================================

  private static boolean invokeRefresh(String className, String methodName, View view) {
    Method m = resolveMethod(className, methodName, View.class);
    if (m == null) return false;
    try {
      m.invoke(null, view);
      return true;
    } catch (Throwable t) {
      Log.d(TAG, className + "#" + methodName + " invoke failed", t);
      return false;
    }
  }

  private static Method resolveMethod(String className, String methodName, Class<?>... params) {
    String key = cacheKey(className, methodName, params);
    Method cached = METHOD_CACHE.get(key);
    if (cached != null) return cached;
    if (METHOD_MISSES.contains(key)) return null;

    try {
      Class<?> cls = Class.forName(className);
      Method m = cls.getMethod(methodName, params);
      m.setAccessible(true);
      METHOD_CACHE.put(key, m);
      return m;
    } catch (ClassNotFoundException e) {
      Log.d(TAG, "Class not found: " + className);
    } catch (NoSuchMethodException e) {
      Log.d(TAG, "Method not found: " + className + "#" + methodName);
    } catch (Throwable t) {
      Log.d(TAG, "Reflection error resolving " + className + "#" + methodName, t);
    }
    METHOD_MISSES.add(key);
    return null;
  }

  private static Object resolveEnumConstant(String enumClassName, String constantName) {
    String key = enumClassName + "#" + constantName;
    Object cached = ENUM_CACHE.get(key);
    if (cached != null) return cached;
    if (ENUM_MISSES.contains(key)) return null;

    try {
      Class<?> cls = Class.forName(enumClassName);
      Field f = cls.getField(constantName);
      Object value = f.get(null);
      if (value != null) {
        ENUM_CACHE.put(key, value);
        return value;
      }
    } catch (ClassNotFoundException e) {
      Log.d(TAG, "Enum class not found: " + enumClassName);
    } catch (NoSuchFieldException e) {
      Log.d(TAG, "Enum constant not found: " + key);
    } catch (Throwable t) {
      Log.d(TAG, "Reflection error resolving enum " + key, t);
    }
    ENUM_MISSES.add(key);
    return null;
  }

  private static String cacheKey(String className, String methodName, Class<?>... params) {
    StringBuilder sb = new StringBuilder(className).append('#').append(methodName).append('(');
    for (int i = 0; i < params.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(params[i] == null ? "null" : params[i].getName());
    }
    return sb.append(')').toString();
  }
}
