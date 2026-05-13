package cn.modificator.launcher.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 可观察的 float 值，替代已废弃的 {@link java.util.Observable}。
 * 仅当值真正变化时通知监听者。
 */
public class ObservableFloat {

  /** 值变化监听器。 */
  public interface Listener {
    void onValueChanged(float value);
  }

  private final List<Listener> listeners = new ArrayList<>();
  private float value;
  private boolean hasValue;

  public ObservableFloat() {
  }

  public ObservableFloat(float initial) {
    this.value = initial;
    this.hasValue = true;
  }

  public float get() {
    return value;
  }

  public void set(float newValue) {
    if (hasValue && Float.compare(value, newValue) == 0) {
      return;
    }
    value = newValue;
    hasValue = true;
    for (Listener listener : listeners) {
      listener.onValueChanged(newValue);
    }
  }

  public void addListener(Listener listener) {
    if (listener == null || listeners.contains(listener)) return;
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void clearListeners() {
    listeners.clear();
  }
}
