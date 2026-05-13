package cn.modificator.launcher.model;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程级内存压力回调注册中心。{@link cn.modificator.launcher.App#onTrimMemory(int)}
 * 收到系统通知后转发给所有已注册的 {@link Listener}。
 */
public final class MemoryListenerRegistry {

  public interface Listener {
    /** @param level 与 {@link android.content.ComponentCallbacks2} 中的 TRIM_MEMORY_* 一致 */
    void onTrimMemory(int level);
  }

  private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

  private MemoryListenerRegistry() {
  }

  public static void register(Listener l) {
    if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
  }

  public static void unregister(Listener l) {
    if (l != null) LISTENERS.remove(l);
  }

  public static void dispatchTrim(int level) {
    for (Listener l : LISTENERS) {
      try {
        l.onTrimMemory(level);
      } catch (Throwable ignored) {
      }
    }
  }
}
