package cn.modificator.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * 应用内强制 locale，不依赖系统语言。
 * <p>
 * 用法：在 {@code attachBaseContext(Context)} 中调用 {@link #wrap(Context, String)}，
 * 把 base context 包成已套用 locale 的版本。Locale 改变时调用 {@link android.app.Activity#recreate()}。
 */
public final class LocaleManager {

  /** 空字符串代表"跟随系统"。 */
  public static final String FOLLOW_SYSTEM = "";

  private LocaleManager() {
  }

  /**
   * 用 {@code tag} 指定的 locale 包装 base context。
   *
   * @param base 原 Context（通常来自 {@code attachBaseContext}）
   * @param tag  BCP-47 语言标签，如 {@code zh-CN}、{@code zh-TW}、{@code en}；空串/null = 跟随系统
   * @return 包装后的 Context；若 tag 为空则返回原 base
   */
  public static Context wrap(Context base, String tag) {
    if (base == null) return null;
    if (tag == null || tag.isEmpty()) return base;

    Locale locale = Locale.forLanguageTag(tag);
    if (locale == null || locale.toString().isEmpty()) return base;

    Locale.setDefault(locale);
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(locale);
    config.setLocales(new LocaleList(locale));
    config.setLayoutDirection(locale);
    return base.createConfigurationContext(config);
  }

  /**
   * 把进程的默认 Locale 设为 tag 指定的值（不更改 Context）。
   * 用于 Application 初始化时让 SimpleDateFormat 等也使用同一 locale。
   */
  public static void applyToProcess(String tag) {
    if (tag == null || tag.isEmpty()) return;
    Locale locale = Locale.forLanguageTag(tag);
    if (locale != null && !locale.toString().isEmpty()) {
      Locale.setDefault(locale);
    }
  }
}
