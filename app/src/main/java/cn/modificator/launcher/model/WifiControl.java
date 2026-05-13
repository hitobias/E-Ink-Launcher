package cn.modificator.launcher.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import java.io.File;
import java.util.Map;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.widgets.ObserverFontTextView;
import cn.modificator.launcher.widgets.RatioImageView;

/**
 * WiFi 状态管理及 UI 绑定。
 * 通过 {@link #init(Context)} 初始化单例，{@link #bind(View, Map)} 绑定视图。
 */
public class WifiControl {

  private static final String WIFI_ON_RES_NAME = "E-ink_Launcher.WifiOn";
  private static final String WIFI_OFF_RES_NAME = "E-ink_Launcher.WifiOff";

  private ObserverFontTextView appName;
  private RatioImageView appImage;
  private final WifiManager wifiManager;
  private final ConnectivityManager connectivityManager;
  private final Context appContext;

  private int showNameRes;
  private int showIconRes;
  private String connectWifiName;
  private Map<String, File> iconReplaceMap;

  private static WifiControl instance;

  public static void init(Context context) {
    instance = new WifiControl(context.getApplicationContext());
  }

  private WifiControl(Context context) {
    appContext = context;
    wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
    connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

    if (wifiManager != null) {
      applyWifiState(wifiManager.getWifiState());
    }

    IntentFilter wifiStateFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    Utils.registerReceiverCompat(appContext, wifiStateReceiver, wifiStateFilter);

    registerNetworkCallback();
  }

  public static void bind(View view, Map<String, File> iconReplaceMap) {
    if (instance == null) return;
    if (view == null) {
      instance.appImage = null;
      instance.appName = null;
      return;
    }
    instance.iconReplaceMap = iconReplaceMap;
    instance.appName = view.findViewById(R.id.appName);
    instance.appImage = view.findViewById(R.id.appImage);
    instance.updateStatus();
  }

  public static void reloadWifiName() {
    if (instance == null) return;
    if (instance.showNameRes == R.string.wifi_status_connected) {
      instance.connectWifiName = formatSsid(instance.readCurrentSsid());
      instance.updateStatus();
    }
  }

  private void updateStatus() {
    if (appName == null) return;

    appName.setText(appContext.getString(showNameRes, connectWifiName));

    String fileName = showIconRes == R.drawable.wifi_on ? WIFI_ON_RES_NAME : WIFI_OFF_RES_NAME;
    File replaceFile = iconReplaceMap != null ? iconReplaceMap.get(fileName) : null;
    if (appImage != null) {
      if (replaceFile != null) {
        appImage.setImageURI(Uri.fromFile(replaceFile));
      } else {
        appImage.setImageResource(showIconRes);
      }
    }
  }

  public static void onClickWifiItem() {
    if (instance == null || instance.wifiManager == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // 普通应用自 API 29 起无法直接切换 WiFi，跳转到设置面板。
      Intent panel = new Intent(Settings.Panel.ACTION_WIFI);
      panel.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        instance.appContext.startActivity(panel);
      } catch (Exception ignored) {
      }
      return;
    }
    int state = instance.wifiManager.getWifiState();
    boolean isEnabled = (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_ENABLED);
    try {
      instance.wifiManager.setWifiEnabled(!isEnabled);
    } catch (SecurityException ignored) {
    }
  }

  public static void onLongClickWifiItem() {
    if (instance == null) return;
    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    instance.appContext.startActivity(intent);
  }

  private void applyWifiState(int wifiState) {
    switch (wifiState) {
      case WifiManager.WIFI_STATE_DISABLED:
        showNameRes = R.string.wifi_status_off;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_DISABLING:
        showNameRes = R.string.wifi_status_closing;
        showIconRes = R.drawable.wifi_on;
        break;
      case WifiManager.WIFI_STATE_ENABLING:
        showNameRes = R.string.wifi_status_opening;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_ENABLED:
        showNameRes = R.string.wifi_status_on;
        showIconRes = R.drawable.wifi_on;
        break;
    }
  }

  private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
        applyWifiState(wifiState);
        updateStatus();
      }
    }
  };

  private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
    @Override
    public void onAvailable(@androidx.annotation.NonNull Network network) {
      onNetworkChanged(network, /* available= */ true);
    }

    @Override
    public void onLost(@androidx.annotation.NonNull Network network) {
      onNetworkChanged(network, /* available= */ false);
    }

    @Override
    public void onCapabilitiesChanged(@androidx.annotation.NonNull Network network,
                                       @androidx.annotation.NonNull NetworkCapabilities caps) {
      if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        showNameRes = R.string.wifi_status_connected;
        connectWifiName = formatSsid(extractSsid(caps));
        post(() -> updateStatus());
      }
    }

    @Override
    public void onLinkPropertiesChanged(@androidx.annotation.NonNull Network network,
                                         @androidx.annotation.NonNull LinkProperties properties) {
      // No-op; capabilities path handles SSID changes.
    }
  };

  private void onNetworkChanged(Network network, boolean available) {
    if (available) {
      showNameRes = R.string.wifi_status_connected;
      connectWifiName = formatSsid(readCurrentSsid());
    } else {
      showNameRes = R.string.wifi_status_disconnected;
      connectWifiName = null;
    }
    post(() -> updateStatus());
  }

  private void registerNetworkCallback() {
    if (connectivityManager == null) return;
    NetworkRequest request = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build();
    try {
      connectivityManager.registerNetworkCallback(request, networkCallback);
    } catch (Exception ignored) {
    }
  }

  private void post(Runnable r) {
    if (appName != null) {
      appName.post(r);
    } else if (appImage != null) {
      appImage.post(r);
    } else {
      r.run();
    }
  }

  private String readCurrentSsid() {
    if (wifiManager == null) return null;
    WifiInfo info = wifiManager.getConnectionInfo();
    return info != null ? info.getSSID() : null;
  }

  private static String extractSsid(NetworkCapabilities caps) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Object info = caps.getTransportInfo();
      if (info instanceof WifiInfo) {
        return ((WifiInfo) info).getSSID();
      }
    }
    return null;
  }

  private static String formatSsid(String raw) {
    if (TextUtils.isEmpty(raw)) return null;
    String cleaned = raw.replace("\"", "");
    if (TextUtils.isEmpty(cleaned) || cleaned.contains("<unknown") || "0x".equalsIgnoreCase(cleaned)) {
      return null;
    }
    return "\n" + cleaned;
  }
}
