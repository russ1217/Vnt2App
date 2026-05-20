package top.wherewego.vnt2_app.vpn;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;

import io.flutter.plugin.common.MethodChannel;
import top.wherewego.vnt2_app.FlutterMethodChannel;
import top.wherewego.vnt2_app.MainActivity;
import top.wherewego.vnt2_app.MyTileService;
import top.wherewego.vnt2_app.VntWidget;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";
    private static ParcelFileDescriptor vpnInterface;
    private volatile static MyVpnService vpnService;

    public static DeviceConfig pendingConfig;


    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        vpnService = this;
        new Thread(() -> {
            try {
                int fd = startVpn(pendingConfig);
                FlutterMethodChannel.callSuccess(fd);
            } catch (SecurityException e) {
                Log.e(TAG, "VPN conflict - another VPN is active", e);
                FlutterMethodChannel.callError("检测到其他 VPN 正在运行，请先断开其他 VPN 后重试", e);
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start VPN: " + pendingConfig.toString(), e);
                FlutterMethodChannel.callError("启动 VPN 失败: " + e.getMessage(), e);
                stopSelf();
            }
        }).start();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Listen for connectivity updates
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        super.onCreate();
    }

    public static void stopVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MyTileService.setState(false);
        }
        // 更新小组件状态为未连接
        if (vpnService != null) {
            VntWidget.updateAllWidgets(vpnService);
            // 更新通知栏状态
            top.wherewego.vnt2_app.VntNotificationService.updateNotification(vpnService);
        }
        FlutterMethodChannel.stopVnt();
        if (vpnService != null) {
            vpnService.stopSelf();
        }
        // 现在可以安全地关闭 vpnInterface
        // 因为 Rust 层使用的是 dup() 复制的 fd,两者互不干扰
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing existing VPN interface", e);
            }
        }
    }

    private int startVpn(DeviceConfig config) throws PackageManager.NameNotFoundException {
        // 先关闭可能存在的旧 VPN 接口
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing old VPN interface", e);
            }
        }
        
        Builder builder = new Builder();
        String ip = IpUtils.intToIpAddress(config.virtualIp);
        int prefixLength = IpUtils.subnetMaskToPrefixLength(config.virtualNetmask);
        String ipRoute = IpUtils.intToIpAddress(config.virtualGateway & config.virtualNetmask);
        builder
                .allowFamily(OsConstants.AF_INET)
                .allowFamily(OsConstants.AF_INET6)
                .setBlocking(false)
                .setMtu(config.mtu)
                .addAddress(ip, prefixLength)
                // 自己的流量不走网卡
                .addDisallowedApplication("top.wherewego.vnt2_app")
                .addRoute(ipRoute, prefixLength);
        if (config.externalRoute != null) {
            for (DeviceConfig.Route routeItem : config.externalRoute) {
                int routePrefixLength = IpUtils.subnetMaskToPrefixLength(routeItem.netmask);
                String routeDest = IpUtils.intToIpAddress(routeItem.destination);
                builder.addRoute(routeDest, routePrefixLength);
            }
        }
        try {
            vpnInterface = builder.setSession("VNT")
                    .establish();
            if (vpnInterface == null) {
                // establish() 返回 null 说明有其他 VPN 正在运行
                Log.e(TAG, "VPN establish failed - another VPN may be active");
                throw new SecurityException("无法建立 VPN 连接。请先断开其他 VPN 应用，然后重试。");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - another VPN is active", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Error establishing VPN interface", e);
            throw e;
        }
        
        // 重要：使用 dup() 复制文件描述符
        // 这样 Rust 层拥有副本的所有权，可以安全关闭而不影响 ParcelFileDescriptor
        // 这解决了 Android 16 fdsan 检测到的所有权冲突问题
        FileDescriptor originalFd = vpnInterface.getFileDescriptor();
        try {
            FileDescriptor duplicatedFd = Os.dup(originalFd);
            int fdInt = getFileDescriptorInt(duplicatedFd);
            Log.i(TAG, "Duplicated VPN fd: " + fdInt);
            return fdInt;
        } catch (android.system.ErrnoException e) {
            Log.e(TAG, "Failed to duplicate VPN fd", e);
            throw new RuntimeException("Failed to duplicate VPN file descriptor", e);
        }
    }

    private int getFileDescriptorInt(FileDescriptor fd) {
        try {
            java.lang.reflect.Field field = fd.getClass().getDeclaredField("descriptor");
            field.setAccessible(true);
            return field.getInt(fd);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file descriptor int", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }
}
