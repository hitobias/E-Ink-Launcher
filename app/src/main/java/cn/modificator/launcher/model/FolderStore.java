package cn.modificator.launcher.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件夹持久化层：以单独的 SharedPreferences 文件 {@code app_folders} 存储 JSON。
 * <p>
 * JSON 结构：
 * <pre>
 * { "folders": [
 *     { "id": "uuid", "name": "名字", "packages": ["pkgA", "pkgB"] },
 *     ...
 * ] }
 * </pre>
 * <p>
 * 设计选择：
 * <ul>
 *   <li>使用 LinkedHashMap 保证 {@link #all()} 的顺序稳定。</li>
 *   <li>单一 key {@code data} 存全量 JSON，写时整体覆盖；MVP 量级足够。</li>
 *   <li>读失败或字段缺失时静默忽略，不抛出。</li>
 * </ul>
 */
public class FolderStore {

  private static final String PREF_FILE = "app_folders";
  private static final String KEY_DATA = "data";

  private static final String JSON_FOLDERS = "folders";
  private static final String JSON_ID = "id";
  private static final String JSON_NAME = "name";
  private static final String JSON_PACKAGES = "packages";

  private final SharedPreferences prefs;
  /** 内存索引：id -> AppFolder，顺序为读入/写入顺序。 */
  private final Map<String, AppFolder> folders = new LinkedHashMap<>();

  public FolderStore(Context ctx) {
    this.prefs = ctx.getApplicationContext()
        .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    load();
  }

  // =========================================================================
  // 读
  // =========================================================================

  /** 返回所有文件夹的快照（拷贝），按存储顺序。 */
  public List<AppFolder> all() {
    return new ArrayList<>(folders.values());
  }

  public AppFolder get(String id) {
    if (id == null) return null;
    return folders.get(id);
  }

  /** 找到包含给定包名的文件夹；如果不在任何文件夹中返回 null。 */
  public AppFolder findContaining(String pkg) {
    if (pkg == null) return null;
    for (AppFolder f : folders.values()) {
      if (f.packages.contains(pkg)) return f;
    }
    return null;
  }

  // =========================================================================
  // 写
  // =========================================================================

  /** 新增或更新单个文件夹并立即持久化。 */
  public void save(AppFolder folder) {
    if (folder == null || folder.id == null) return;
    folders.put(folder.id, folder);
    persist();
  }

  /** 删除指定文件夹并立即持久化；不存在时静默无操作。 */
  public void delete(String id) {
    if (id == null) return;
    if (folders.remove(id) != null) {
      persist();
    }
  }

  // =========================================================================
  // 内部 JSON I/O
  // =========================================================================

  private void load() {
    folders.clear();
    String raw = prefs.getString(KEY_DATA, null);
    if (raw == null || raw.isEmpty()) return;
    try {
      JSONObject root = new JSONObject(raw);
      JSONArray arr = root.optJSONArray(JSON_FOLDERS);
      if (arr == null) return;
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.optJSONObject(i);
        if (obj == null) continue;
        String id = obj.optString(JSON_ID, null);
        if (id == null || id.isEmpty()) continue;
        String name = obj.optString(JSON_NAME, "");
        AppFolder folder = new AppFolder(id, name);
        JSONArray pkgs = obj.optJSONArray(JSON_PACKAGES);
        if (pkgs != null) {
          for (int j = 0; j < pkgs.length(); j++) {
            String p = pkgs.optString(j, null);
            if (p != null && !p.isEmpty() && !folder.packages.contains(p)) {
              folder.packages.add(p);
            }
          }
        }
        folders.put(id, folder);
      }
    } catch (JSONException ignored) {
      // 损坏的数据：保留空索引，下次写会覆盖。
    }
  }

  private void persist() {
    try {
      JSONArray arr = new JSONArray();
      for (AppFolder folder : folders.values()) {
        JSONObject obj = new JSONObject();
        obj.put(JSON_ID, folder.id);
        obj.put(JSON_NAME, folder.name == null ? "" : folder.name);
        JSONArray pkgs = new JSONArray();
        for (String p : folder.packages) {
          pkgs.put(p);
        }
        obj.put(JSON_PACKAGES, pkgs);
        arr.put(obj);
      }
      JSONObject root = new JSONObject();
      root.put(JSON_FOLDERS, arr);
      prefs.edit().putString(KEY_DATA, root.toString()).apply();
    } catch (JSONException ignored) {
      // 序列化失败极不可能，忽略；下次写会重试。
    }
  }
}
