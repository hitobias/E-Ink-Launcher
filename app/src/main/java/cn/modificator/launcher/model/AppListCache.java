package cn.modificator.launcher.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SharedPreferences-backed cache of the most recently seen launcher entries.
 *
 * <p>Storage format: a single {@code Set<String>} entry where each element is
 * {@code "pkg/activityName"}. The slash is illegal in Java package and class
 * names, so it never collides with normal characters in either field.</p>
 *
 * <p>Writes are serialized on a single background executor to keep cold-start
 * latency low on the UI thread.</p>
 */
public class AppListCache {

  private static final String PREFS_FILE = "app_list_cache";
  private static final String KEY_ENTRIES = "entries";
  private static final String SEPARATOR = "/";

  private final SharedPreferences prefs;
  private final ExecutorService writeExecutor;

  public AppListCache(Context context) {
    this.prefs = context.getApplicationContext()
        .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    this.writeExecutor = Executors.newSingleThreadExecutor();
  }

  /**
   * Read the cached entries from disk.
   *
   * @return a list of {@code {pkg, activityName}} pairs; never {@code null},
   *     but possibly empty if nothing has been cached or entries are malformed.
   */
  public List<String[]> read() {
    Set<String> stored = prefs.getStringSet(KEY_ENTRIES, Collections.<String>emptySet());
    if (stored == null || stored.isEmpty()) {
      return new ArrayList<>();
    }
    List<String[]> result = new ArrayList<>(stored.size());
    for (String entry : stored) {
      if (entry == null) continue;
      int idx = entry.indexOf(SEPARATOR);
      if (idx <= 0 || idx >= entry.length() - 1) continue;
      String pkg = entry.substring(0, idx);
      String activity = entry.substring(idx + 1);
      result.add(new String[] { pkg, activity });
    }
    return result;
  }

  /**
   * Serialize the package + activity names of the given list to disk on a
   * background thread.
   */
  public void writeAsync(final List<ResolveInfo> list) {
    if (list == null) return;
    // Snapshot the names on the calling thread so the background task does
    // not race with subsequent mutations of the caller's list.
    final Set<String> snapshot = new TreeSet<>();
    for (ResolveInfo ri : list) {
      if (ri == null || ri.activityInfo == null) continue;
      String pkg = ri.activityInfo.packageName;
      String activity = ri.activityInfo.name;
      if (pkg == null || activity == null) continue;
      snapshot.add(pkg + SEPARATOR + activity);
    }
    writeExecutor.execute(new Runnable() {
      @Override
      public void run() {
        // Pass a fresh HashSet to SharedPreferences; storing a TreeSet directly
        // is permitted but the docs only guarantee Set semantics.
        prefs.edit().putStringSet(KEY_ENTRIES, new HashSet<>(snapshot)).apply();
      }
    });
  }

  /** Clear all cached entries. */
  public void clear() {
    writeExecutor.execute(new Runnable() {
      @Override
      public void run() {
        prefs.edit().remove(KEY_ENTRIES).apply();
      }
    });
  }
}
