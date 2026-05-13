package cn.modificator.launcher.model;

import android.graphics.Typeface;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局字体管理器，单例。
 * 保存当前 Typeface 并在变化时通知监听者。
 */
public final class FontManager {

  public interface TypefaceListener {
    void onTypefaceChanged(Typeface typeface);
  }

  private static volatile Typeface current = Typeface.DEFAULT;
  private static final CopyOnWriteArrayList<TypefaceListener> listeners = new CopyOnWriteArrayList<>();

  private FontManager() {
  }

  public static Typeface get() {
    return current;
  }

  public static boolean load(File font) {
    if (font == null || !font.exists() || !font.isFile()) {
      return false;
    }
    Typeface tf;
    try {
      tf = Typeface.createFromFile(font);
    } catch (RuntimeException e) {
      return false;
    }
    if (tf == null || tf == Typeface.DEFAULT) {
      return false;
    }
    setCurrent(tf);
    return true;
  }

  public static void clear() {
    setCurrent(Typeface.DEFAULT);
  }

  public static void addListener(TypefaceListener l) {
    if (l == null) return;
    listeners.addIfAbsent(l);
  }

  public static void removeListener(TypefaceListener l) {
    if (l == null) return;
    listeners.remove(l);
  }

  private static void setCurrent(Typeface tf) {
    if (tf == current) return;
    current = tf;
    for (TypefaceListener l : listeners) {
      l.onTypefaceChanged(tf);
    }
  }
}
