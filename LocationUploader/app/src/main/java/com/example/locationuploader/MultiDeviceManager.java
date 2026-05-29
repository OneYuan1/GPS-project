package com.example.locationuploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 多设备管理器
 */
public class MultiDeviceManager {
    private static final String TAG = "MultiDeviceManager";
    private static final String PREFS_NAME = "device_info";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_NODE_NAME = "node_name";
    private static final String KEY_DEVICE_NAME = "device_name";
    
    private static MultiDeviceManager instance;
    private Context context;
    private int deviceId;
    private int nodeName;
    private String deviceName;
    
    private MultiDeviceManager(Context context) {
        this.context = context.getApplicationContext();
        loadOrGenerateDeviceInfo();
    }
    
    public static synchronized MultiDeviceManager getInstance(Context context) {
        if (instance == null) {
            instance = new MultiDeviceManager(context);
        }
        return instance;
    }
    
    private void loadOrGenerateDeviceInfo() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        deviceId = prefs.getInt(KEY_DEVICE_ID, -1);
        nodeName = prefs.getInt(KEY_NODE_NAME, -1);
        deviceName = prefs.getString(KEY_DEVICE_NAME, null);
        
        if (deviceId == -1 || nodeName == -1 || deviceName == null) {
            generateDeviceInfo();
            saveDeviceInfo();
        }
        
        Log.i(TAG, "设备信息: ID=" + deviceId + ", NodeName=0x" + String.format("%08X", nodeName) + ", Name=" + deviceName);
    }
    
    private void generateDeviceInfo() {
        deviceId = generateUniqueDeviceId();
        nodeName = 0xD0000000 | deviceId;
        deviceName = "Android_Device_" + String.format("%07d", deviceId);
    }
    
    private int generateUniqueDeviceId() {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String imei = tm.getImei();
                    if (imei != null && !imei.isEmpty()) {
                        String last7 = imei.substring(Math.max(0, imei.length() - 7));
                        return Integer.parseInt(last7) & 0x7FFFFFFF;
                    }
                }
                
                String deviceId = tm.getDeviceId();
                if (deviceId != null && !deviceId.isEmpty()) {
                    String last7 = deviceId.substring(Math.max(0, deviceId.length() - 7));
                    return Integer.parseInt(last7) & 0x7FFFFFFF;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "获取设备ID失败: " + e.getMessage());
        }
        
        String deviceInfo = Build.MANUFACTURER + Build.MODEL + Build.SERIAL + Build.FINGERPRINT;
        int hash = deviceInfo.hashCode();
        return Math.abs(hash) % 10000000;
    }
    
    private void saveDeviceInfo() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_DEVICE_ID, deviceId);
        editor.putInt(KEY_NODE_NAME, nodeName);
        editor.putString(KEY_DEVICE_NAME, deviceName);
        editor.apply();
    }
    
    public int getDeviceId() {
        return deviceId;
    }
    
    public int getNodeName() {
        return nodeName;
    }
    
    public String getDeviceName() {
        return deviceName;
    }
    
    public String getDeviceSummary() {
        return String.format("设备ID: %d\n节点名称: 0x%08X\n设备名称: %s", 
                           deviceId, nodeName, deviceName);
    }
}
