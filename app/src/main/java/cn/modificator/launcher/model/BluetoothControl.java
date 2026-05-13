package cn.modificator.launcher.model;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.widgets.ObserverFontTextView;
import cn.modificator.launcher.widgets.RatioImageView;

/**
 * 蓝牙状态管理及 UI 绑定。
 * 行为：
 * <ul>
 *   <li>{@link #onClickBluetoothItem()} — API &lt; 31 直接 toggle；新版跳转设置面板</li>
 *   <li>{@link #onLongClickBluetoothItem()} — 进入系统蓝牙设置</li>
 *   <li>状态变化（开 / 关 / 切换中）通过 {@link BluetoothAdapter#ACTION_STATE_CHANGED} 广播实时更新</li>
 * </ul>
 */
public class BluetoothControl {

  private static final String BT_ON_RES_NAME = "E-ink_Launcher.BluetoothOn";
  private static final String BT_OFF_RES_NAME = "E-ink_Launcher.BluetoothOff";

  private final Context appContext;
  private final BluetoothAdapter adapter;
  private final boolean hasBluetooth;

  private ObserverFontTextView appName;
  private RatioImageView appImage;

  private int showNameRes;
  private int showIconRes;
  private Map<String, File> iconReplaceMap;

  private static BluetoothControl instance;

  /** 持久化"上次连接尝试"时间戳：MAC → epoch millis。 */
  private static final String LAST_CONNECT_PREFS = "bt_last_connect";

  public static void init(Context context) {
    instance = new BluetoothControl(context.getApplicationContext());
  }

  /** 当前设备是否具备蓝牙硬件。无 BT 的设备不需要显示虚拟图标。 */
  public static boolean isSupported() {
    return instance != null && instance.hasBluetooth;
  }

  private BluetoothControl(Context context) {
    appContext = context;
    PackageManager pm = context.getPackageManager();
    hasBluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

    BluetoothAdapter resolved = null;
    if (hasBluetooth) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          BluetoothManager bm = context.getSystemService(BluetoothManager.class);
          if (bm != null) resolved = bm.getAdapter();
        } else {
          resolved = BluetoothAdapter.getDefaultAdapter();
        }
      } catch (Exception ignored) {
      }
    }
    adapter = resolved;

    if (adapter != null) {
      applyState(adapter.getState());
    } else {
      showNameRes = R.string.bt_status_off;
      showIconRes = R.drawable.bt_off;
    }

    if (hasBluetooth) {
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      Utils.registerReceiverCompat(appContext, stateReceiver, filter);
    }
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

  private void updateStatus() {
    if (appName == null) return;
    appName.setText(showNameRes);

    String fileName = showIconRes == R.drawable.bt_on ? BT_ON_RES_NAME : BT_OFF_RES_NAME;
    File replaceFile = iconReplaceMap != null ? iconReplaceMap.get(fileName) : null;
    if (appImage != null) {
      if (replaceFile != null) {
        appImage.setImageURI(Uri.fromFile(replaceFile));
      } else {
        appImage.setImageResource(showIconRes);
      }
    }
  }

  public static void onClickBluetoothItem() {
    if (instance == null) return;
    if (!instance.hasBluetooth || instance.adapter == null) {
      openSettings();
      return;
    }
    // API 31+ 普通应用不能直接 enable/disable；跳到设置面板更稳。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      openSettings();
      return;
    }
    try {
      if (instance.adapter.isEnabled()) {
        instance.adapter.disable();
      } else {
        instance.adapter.enable();
      }
    } catch (SecurityException ignored) {
      openSettings();
    }
  }

  public static void onLongClickBluetoothItem() {
    openSettings();
  }

  /** 返回已配对设备集合；适配器关闭、未授权或不支持时返回空集。 */
  public static Set<BluetoothDevice> getBondedDevices() {
    if (instance == null || instance.adapter == null) return Collections.emptySet();
    if (!instance.adapter.isEnabled()) return Collections.emptySet();
    try {
      Set<BluetoothDevice> bonded = instance.adapter.getBondedDevices();
      return bonded != null ? bonded : Collections.emptySet();
    } catch (SecurityException e) {
      return Collections.emptySet();
    }
  }

  /** 当前蓝牙适配器是否打开。 */
  public static boolean isEnabled() {
    return instance != null && instance.adapter != null && instance.adapter.isEnabled();
  }

  /**
   * 判断指定设备是否当前已连接。
   * 优先反射 {@code BluetoothDevice.isConnected()}（隐藏 API，覆盖所有 profile），
   * 失败时退回到 {@code BluetoothManager.getConnectionState(device, GATT)}（覆盖 BLE）。
   */
  public static boolean isDeviceConnected(BluetoothDevice device) {
    if (device == null) return false;
    try {
      Method m = device.getClass().getMethod("isConnected");
      Object r = m.invoke(device);
      if (r instanceof Boolean) return (Boolean) r;
    } catch (Throwable ignored) {
    }
    if (instance == null || instance.appContext == null) return false;
    try {
      BluetoothManager bm = (BluetoothManager)
          instance.appContext.getSystemService(Context.BLUETOOTH_SERVICE);
      if (bm == null) return false;
      int state = bm.getConnectionState(device, BluetoothProfile.GATT);
      return state == BluetoothProfile.STATE_CONNECTED;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** 公开供 Launcher 跳转设置。 */
  public static void launchSettings() {
    openSettings();
  }

  /** 返回上次对该 MAC 发起 tryConnect 的时间戳；从未连接过返回 0。 */
  public static long getLastConnectMs(BluetoothDevice device) {
    if (instance == null || device == null) return 0;
    try {
      return instance.appContext
          .getSharedPreferences(LAST_CONNECT_PREFS, Context.MODE_PRIVATE)
          .getLong(device.getAddress(), 0);
    } catch (Exception ignored) {
      return 0;
    }
  }

  private static void recordConnectAttempt(BluetoothDevice device) {
    if (instance == null || device == null) return;
    try {
      instance.appContext
          .getSharedPreferences(LAST_CONNECT_PREFS, Context.MODE_PRIVATE)
          .edit()
          .putLong(device.getAddress(), System.currentTimeMillis())
          .apply();
    } catch (Exception ignored) {
    }
  }

  /**
   * 尝试通过 BluetoothProfile 反射连接已配对设备。
   * 非 system 应用大多数情况会失败（hidden API 限制），失败时回调 onFailed 让调用方降级到打开设置。
   */
  public static void tryConnect(BluetoothDevice device, Runnable onFailed) {
    if (instance == null || instance.adapter == null || device == null) {
      if (onFailed != null) onFailed.run();
      return;
    }
    recordConnectAttempt(device);
    int profile = pickProfileFor(device);
    if (profile == -1) {
      if (onFailed != null) onFailed.run();
      return;
    }
    final Runnable failure = onFailed != null ? onFailed : () -> {};
    try {
      boolean started = instance.adapter.getProfileProxy(
          instance.appContext,
          new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int p, BluetoothProfile proxy) {
              boolean ok = false;
              try {
                Method m = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                Object r = m.invoke(proxy, device);
                ok = !(r instanceof Boolean) || (Boolean) r;
              } catch (Throwable t) {
                Log.w("BluetoothControl",
                    "Profile.connect reflection failed: " + t.getMessage());
              }
              try {
                instance.adapter.closeProfileProxy(p, proxy);
              } catch (Exception ignored) {
              }
              if (!ok) {
                new Handler(Looper.getMainLooper()).post(failure);
              }
            }

            @Override
            public void onServiceDisconnected(int p) {
            }
          },
          profile);
      if (!started) failure.run();
    } catch (Exception e) {
      failure.run();
    }
  }

  /** 推断设备应当走哪个 BluetoothProfile（基于 Major Device Class）。 */
  private static int pickProfileFor(BluetoothDevice device) {
    try {
      BluetoothClass bc = device.getBluetoothClass();
      if (bc == null) return BluetoothProfile.A2DP;
      int major = bc.getMajorDeviceClass();
      if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) return BluetoothProfile.A2DP;
      if (major == BluetoothClass.Device.Major.PHONE) return BluetoothProfile.HEADSET;
      if (major == BluetoothClass.Device.Major.PERIPHERAL) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? BluetoothProfile.HID_DEVICE
            : 4 /* HEADSET_CLIENT 兜底 */;
      }
      // Wearable / Computer / Imaging / Misc：A2DP 兜底
      return BluetoothProfile.A2DP;
    } catch (SecurityException e) {
      return -1;
    }
  }

  private static void openSettings() {
    if (instance == null) return;
    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      instance.appContext.startActivity(intent);
    } catch (Exception ignored) {
    }
  }

  private void applyState(int state) {
    switch (state) {
      case BluetoothAdapter.STATE_OFF:
        showNameRes = R.string.bt_status_off;
        showIconRes = R.drawable.bt_off;
        break;
      case BluetoothAdapter.STATE_TURNING_OFF:
        showNameRes = R.string.bt_status_closing;
        showIconRes = R.drawable.bt_on;
        break;
      case BluetoothAdapter.STATE_TURNING_ON:
        showNameRes = R.string.bt_status_opening;
        showIconRes = R.drawable.bt_off;
        break;
      case BluetoothAdapter.STATE_ON:
        showNameRes = R.string.bt_status_on;
        showIconRes = R.drawable.bt_on;
        break;
      default:
        showNameRes = R.string.bt_status_off;
        showIconRes = R.drawable.bt_off;
        break;
    }
  }

  private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
      int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
      applyState(state);
      if (appName != null) {
        appName.post(() -> updateStatus());
      }
    }
  };
}
