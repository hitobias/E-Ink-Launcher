package cn.modificator.launcher;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;

import java.io.File;

import cn.modificator.launcher.model.FontManager;
import cn.modificator.launcher.model.MemoryListenerRegistry;

public class App extends Application {

  @Override
  protected void attachBaseContext(Context base) {
    String localeTag = new Config(base).getLocaleTag();
    super.attachBaseContext(LocaleManager.wrap(base, localeTag));
    LocaleManager.applyToProcess(localeTag);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    CrashCapture.getInstance().init(this);
    Config config = new Config(this);
    String fontPath = config.getFontPath();
    if (fontPath != null) {
      File file = new File(fontPath);
      if (file.exists() && !FontManager.load(file)) {
        config.clearFontPath();
      }
    }
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    // 系统提示内存吃紧时主动清理 icon / label 缓存，避免 OOM。
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
      MemoryListenerRegistry.dispatchTrim(level);
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    MemoryListenerRegistry.dispatchTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
  }
}
