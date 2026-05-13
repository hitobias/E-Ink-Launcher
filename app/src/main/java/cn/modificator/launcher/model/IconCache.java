package cn.modificator.launcher.model;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用图标、标签的内存缓存，以及自定义图标替换映射管理。
 * <p>
 * 自定义图标查找顺序：
 * <ol>
 *   <li>App 专属外部目录 {@code <files>/Documents/E-Ink Launcher/icon}
 *       —— Android 10+ 推荐位置，无需权限</li>
 *   <li>公共 Documents 目录（旧版兼容）</li>
 * </ol>
 */
public class IconCache {

  private static final String ICON_DIR_REL = "E-Ink Launcher" + File.separator + "icon";

  private final LruCache<String, Drawable> drawableCache = new LruCache<>(150);
  private final LruCache<String, CharSequence> labelCache = new LruCache<>(300);
  private final ExecutorService loadExecutor =
      Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "IconLoader");
        t.setDaemon(true);
        return t;
      });
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Map<String, File> customIconMap = new HashMap<>();
  private boolean dirty = true;

  // =========================================================================
  // 自定义图标
  // =========================================================================

  public void markDirty() {
    dirty = true;
  }

  /**
   * 如有必要，重新扫描自定义图标目录。
   *
   * @param context        用于解析 app 专属外部目录的上下文（可为 null，退回到公共目录）
   * @param showCustomIcon 用户是否启用"显示自定义图标"（true 表示禁用替换）
   * @return true 表示执行了实际扫描
   */
  public boolean refreshCustomIcons(Context context, boolean showCustomIcon) {
    if (!dirty) return false;
    customIconMap.clear();

    if (!showCustomIcon) {
      File appScoped = getAppScopedIconDirectory(context);
      File publicDir = getPublicIconDirectory();
      scanInto(appScoped);
      scanInto(publicDir);
    }
    dirty = false;
    return true;
  }

  /** 旧签名重载，保留 API 兼容。 */
  public boolean refreshCustomIcons(boolean hasExternalStorage, boolean showCustomIcon) {
    return refreshCustomIcons(null, showCustomIcon);
  }

  private void scanInto(File dir) {
    if (dir == null) return;
    if (!dir.exists()) {
      try {
        if (!dir.mkdirs()) return;
      } catch (Exception ignored) {
        return;
      }
    }
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (!file.isFile()) continue;
      String name = file.getName();
      int dot = name.lastIndexOf('.');
      if (dot <= 0) continue;
      String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
      // 仅接受常见图片格式，防止用户把任意文件丢进目录导致解码崩溃。
      if (!"png".equals(ext) && !"jpg".equals(ext) && !"jpeg".equals(ext)
          && !"webp".equals(ext) && !"bmp".equals(ext)) {
        continue;
      }
      String key = name.substring(0, dot);
      if (!customIconMap.containsKey(key)) {
        customIconMap.put(key, file);
      }
    }
  }

  private static File getAppScopedIconDirectory(Context context) {
    if (context == null) return null;
    File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    return base != null ? new File(base, ICON_DIR_REL) : null;
  }

  private static File getPublicIconDirectory() {
    try {
      return new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
          ICON_DIR_REL);
    } catch (Exception ignored) {
      return null;
    }
  }

  public File getCustomIcon(String packageName) {
    return customIconMap.get(packageName);
  }

  public Map<String, File> getCustomIconMap() {
    return Collections.unmodifiableMap(customIconMap);
  }

  // =========================================================================
  // 应用图标 & 标签缓存
  // =========================================================================

  public Drawable getIcon(String packageName, ResolveInfo info, PackageManager pm) {
    Drawable cached = drawableCache.get(packageName);
    if (cached != null) return cached;
    Drawable d = info.loadIcon(pm);
    if (d != null) drawableCache.put(packageName, d);
    return d;
  }

  public CharSequence getLabel(String packageName, ResolveInfo info, PackageManager pm) {
    CharSequence cached = labelCache.get(packageName);
    if (cached != null) return cached;
    CharSequence l = info.loadLabel(pm);
    labelCache.put(packageName, l);
    return l;
  }

  public void clearAppCache() {
    drawableCache.evictAll();
    labelCache.evictAll();
  }

  /** Convenience for binders: load icon into ImageView asynchronously with recycle guard. */
  public void loadIconInto(ImageView target, String packageName, ResolveInfo info,
                           PackageManager pm, Object expectedTag) {
    Drawable cached = drawableCache.get(packageName);
    if (cached != null) {
      target.setImageDrawable(cached);
      return;
    }
    target.setImageDrawable(null);
    loadExecutor.execute(() -> {
      Drawable d;
      try {
        d = info.loadIcon(pm);
      } catch (Exception e) {
        d = null;
      }
      if (d == null) return;
      drawableCache.put(packageName, d);
      final Drawable result = d;
      mainHandler.post(() -> {
        if (target.getTag() == expectedTag) {
          target.setImageDrawable(result);
        }
      });
    });
  }
}
