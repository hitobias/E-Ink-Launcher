package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Launcher 配置的 JSON 导出 / 导入。
 * 用于换设备或备份。FTP / BT 连接历史等设备相关字段不包含。
 */
public final class ConfigBackup {

  private static final String PREFS_FILE = "launcherPropertyFile";
  private static final int FORMAT_VERSION = 1;

  private ConfigBackup() {
  }

  public static void exportTo(Context ctx, OutputStream out) throws IOException, JSONException {
    SharedPreferences prefs = ctx.getApplicationContext()
        .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    JSONObject root = new JSONObject();
    root.put("version", FORMAT_VERSION);
    JSONObject body = new JSONObject();
    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
      Object v = e.getValue();
      if (v instanceof Set) {
        JSONArray arr = new JSONArray();
        for (Object item : (Set<?>) v) arr.put(item != null ? item.toString() : "");
        body.put(e.getKey(), arr);
      } else if (v == null) {
        body.put(e.getKey(), JSONObject.NULL);
      } else {
        body.put(e.getKey(), v);
      }
    }
    root.put("prefs", body);

    try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
      w.write(root.toString(2));
    }
  }

  /**
   * 从 InputStream 读取 JSON 并写回 SharedPreferences。
   * 旧值会被清空。读取或解析失败时抛出对应异常。
   */
  public static void importFrom(Context ctx, InputStream in) throws IOException, JSONException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(
        new InputStreamReader(in, StandardCharsets.UTF_8))) {
      char[] buf = new char[4096];
      int n;
      while ((n = r.read(buf)) > 0) {
        sb.append(buf, 0, n);
        if (sb.length() > 256 * 1024) {
          throw new IOException("Config file too large");
        }
      }
    }

    JSONObject root = new JSONObject(sb.toString());
    if (!root.has("prefs")) throw new JSONException("Missing prefs object");
    JSONObject body = root.getJSONObject("prefs");

    SharedPreferences prefs = ctx.getApplicationContext()
        .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    SharedPreferences.Editor ed = prefs.edit();
    ed.clear();
    java.util.Iterator<String> keys = body.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object v = body.get(key);
      if (v instanceof JSONArray) {
        JSONArray arr = (JSONArray) v;
        Set<String> set = new HashSet<>(arr.length());
        for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
        ed.putStringSet(key, set);
      } else if (v instanceof Boolean) {
        ed.putBoolean(key, (Boolean) v);
      } else if (v instanceof Integer) {
        ed.putInt(key, (Integer) v);
      } else if (v instanceof Long) {
        ed.putLong(key, (Long) v);
      } else if (v instanceof Double || v instanceof Float) {
        ed.putFloat(key, ((Number) v).floatValue());
      } else if (v instanceof String) {
        ed.putString(key, (String) v);
      }
      // 其他类型（JSONObject.NULL 等）忽略
    }
    ed.apply();
  }
}
