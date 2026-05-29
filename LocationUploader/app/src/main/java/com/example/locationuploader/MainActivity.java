package com.example.locationuploader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Socket;
import android.content.Intent;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long MIN_TIME_BETWEEN_UPDATES = 2000; // 2秒
    private static final float MIN_DISTANCE_CHANGE = 1; // 1米
    private static final String TAG = "MainActivity";

    private TextView locationText;
    private TextView gnssStatusText; // 新增：GNSS状态显示
    private Button getLocationButton, startUploadButton, stopUploadButton, networkConfigButton, remoteConfigButton;
    private LocationManager locationManager;
    private GNSSStatusListener gnssStatusListener; // 新增：GNSS状态监听器
    private NetworkLocationHelper networkLocationHelper; // 新增：网络定位辅助类
    private MultiDeviceManager deviceManager; // 设备管理器
    private Location currentLocation;
    private boolean isRequestingLocation = false;
    private boolean isUploading = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int locationAttempts = 0;
    private static final int MAX_ATTEMPTS = 60;
    private static final float ACCURACY_THRESHOLD = 100.0f; // 降低精度要求到100米
    private static final int UPLOAD_INTERVAL = 5000; // 5秒上传一次

    private Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUploading) {
                Location locationForUpload = getLatestOrPlaceholderLocation();
                uploadLocation(locationForUpload);
                handler.postDelayed(this, UPLOAD_INTERVAL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationText = findViewById(R.id.locationText);
        gnssStatusText = findViewById(R.id.gnssStatusText); // 新增：GNSS状态显示
        getLocationButton = findViewById(R.id.getLocationButton);
        startUploadButton = findViewById(R.id.startUploadButton);
        stopUploadButton = findViewById(R.id.stopUploadButton);
        networkConfigButton = findViewById(R.id.networkConfigButton);
        remoteConfigButton = findViewById(R.id.remoteConfigButton);

        // 获取LocationManager实例
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        // 初始化设备管理器
        deviceManager = MultiDeviceManager.getInstance(this);
        
        // 初始化GNSS状态监听器
        gnssStatusListener = new GNSSStatusListener(locationManager);
        gnssStatusListener.setDataCallback(new GNSSStatusListener.GNSSDataCallback() {
            @Override
            public void onGNSSDataUpdated(List<GNSSData.SatelliteInfo> satellites) {
                updateGNSSStatusDisplay(satellites);
                // 同步更新位置显示，确保GNSS增强信息与GNSS状态一致
                if (currentLocation != null) {
                    updateLocationDisplay(currentLocation);
                }
            }
        });
        
        // 初始化网络定位辅助类（已禁用网络定位，仅保留代码以便将来启用）
        // networkLocationHelper = new NetworkLocationHelper(this);
        // networkLocationHelper.setCallback(new NetworkLocationHelper.NetworkLocationCallback() {
        //     @Override
        //     public void onLocationReceived(Location location) {
        //         runOnUiThread(() -> {
        //             if (currentLocation != null &&
        //                 LocationManager.GPS_PROVIDER.equals(currentLocation.getProvider()) &&
        //                 currentLocation.getAccuracy() <= 30.0f) {
        //                 Log.d(TAG, "GPS定位精度更高，保持GPS结果");
        //                 Toast.makeText(MainActivity.this,
        //                     "地图API定位成功，但GPS精度更高", Toast.LENGTH_SHORT).show();
        //             } else {
        //                 currentLocation = location;
        //                 updateLocationDisplay(location);
        //                 updateButtonStates();
        //                 Toast.makeText(MainActivity.this,
        //                     "地图API定位成功: " + location.getProvider() + " (精度" +
        //                     String.format("%.0fm", location.getAccuracy()) + ")",
        //                     Toast.LENGTH_SHORT).show();
        //             }
        //         });
        //     }
        //     @Override
        //     public void onLocationFailed(String error) {
        //         runOnUiThread(() -> {
        //             Log.w(TAG, "网络定位失败: " + error);
        //             if (currentLocation == null ||
        //                 !LocationManager.GPS_PROVIDER.equals(currentLocation.getProvider())) {
        //                 Toast.makeText(MainActivity.this,
        //                     "地图API定位失败: " + error, Toast.LENGTH_LONG).show();
        //             }
        //         });
        //     }
        // });

        getLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation();
            }
        });

        startUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLocation == null) {
                    Toast.makeText(MainActivity.this, "未解算到GPS，上传占位数据以监测压制", Toast.LENGTH_SHORT).show();
                }
                startContinuousUpload();
            }
        });

        stopUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopContinuousUpload();
            }
        });

        networkConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNetworkConfigDialog();
            }
        });

        remoteConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRemoteConfigDialog();
            }
        });

        // 初始状态设置
        updateButtonStates();
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        // 启动GNSS监听
        startGNSSListening();

        // 检查GPS状态（仅使用GPS，不使用网络定位）
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            locationText.setText("GPS已禁用\n请在设置中开启GPS");
            Toast.makeText(this, "请开启GPS", Toast.LENGTH_LONG).show();
            return;
        }

        locationAttempts = 0;
        String statusText = "正在获取位置信息...\n";
        if (gpsEnabled) statusText += "✓ GPS已开启\n";
        statusText += "✓ 实时精度更新已启用\n";
        statusText += "请等待位置信息...\n\n";
        
        // 显示网络信息（已禁用，仅保留注释）
        // if (networkLocationHelper != null) {
        //     statusText += "WiFi信息: " + networkLocationHelper.getWifiInfo() + "\n";
        //     statusText += "基站信息: " + networkLocationHelper.getCellInfo();
        // }
        
        locationText.setText(statusText);
        
        // 启动GPS定位
        startLocationUpdates();
        
        // 地图API定位功能已禁用（仅使用GPS）
        // 如需启用，请：
        // 1. 在NetworkLocationHelper.java中配置API密钥
        // 2. 取消注释以下代码：
        // if (networkLocationHelper != null) {
        //     networkLocationHelper.getNetworkLocation();
        // }
    }
    
    /**
     * 获取网络位置信息
     */
    // private void getNetworkLocation() {
    //     if (networkLocationHelper != null) {
    //         locationText.setText("正在通过地图API获取位置信息...\n请稍候...");
    //         networkLocationHelper.getNetworkLocation();
    //     } else {
    //         Toast.makeText(this, "网络定位功能未初始化", Toast.LENGTH_SHORT).show();
    //     }
    // }

    // 获取最后已知位置
    private Location getLastKnownLocation() {
        Location bestLocation = null;
        try {
            // 获取所有位置提供者
            for (String provider : locationManager.getAllProviders()) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    continue;
                }

                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) continue;

                if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                    bestLocation = location;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
        }
        return bestLocation;
    }

    // 启动位置更新
    private void startLocationUpdates() {
        if (isRequestingLocation) return;

        try {
            // 创建位置请求标准 - 优化以获取速度和方向
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE); // 高精度
            criteria.setPowerRequirement(Criteria.POWER_HIGH); // 高功耗模式
            criteria.setAltitudeRequired(true);
            criteria.setBearingRequired(true); // 要求方向信息
            criteria.setSpeedRequired(true);   // 要求速度信息
            criteria.setCostAllowed(true);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);

            // 仅使用GPS提供者
            String provider = LocationManager.GPS_PROVIDER;

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // 仅请求GPS位置提供者
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            100,  // 100毫秒更新一次，提高响应速度
                            0,    // 0米变化时更新，强制更新
                            this,
                            Looper.getMainLooper()
                    );
                    Log.d(TAG, "GPS位置提供者已启动（高精度模式，100ms间隔）");
                }
                // 如需启用网络位置提供者，请恢复以下代码
                // if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                //     locationManager.requestLocationUpdates(
                //             LocationManager.NETWORK_PROVIDER,
                //             200,
                //             0,
                //             this,
                //             Looper.getMainLooper()
                //     );
                //     Log.d(TAG, "网络位置提供者已启动（备用模式，200ms间隔）");
                // }
                
                isRequestingLocation = true;

                // 设置超时检查 - 增加到60秒
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isRequestingLocation) {
                            locationText.setText("位置获取超时\n请检查GPS是否开启\n或尝试在室外使用");
                            stopLocationUpdates();
                            Toast.makeText(MainActivity.this, "位置获取超时，请重试", Toast.LENGTH_LONG).show();
                        }
                    }
                }, 60000); // 60秒超时
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
            locationText.setText("权限错误: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException: " + e.getMessage());
            locationText.setText("参数错误: " + e.getMessage());
        }
    }

    // 停止位置更新
    private void stopLocationUpdates() {
        if (isRequestingLocation) {
            locationManager.removeUpdates(this);
            isRequestingLocation = false;
            Log.d(TAG, "位置更新已停止");
        }
    }

    private void updateButtonStates() {
        startUploadButton.setEnabled(!isUploading);
        stopUploadButton.setEnabled(isUploading);
        updateConfigStatus();
    }

    // 更新配置状态显示
    private void updateConfigStatus() {
        SharedPreferences prefs = getSharedPreferences("LocationUploader", MODE_PRIVATE);
        String remoteUrl = prefs.getString("remote_server_url", "");
        String uploadMethod = prefs.getString("upload_method", "local");
        
        String statusText = "";
        if (!remoteUrl.isEmpty() && !uploadMethod.equals("local")) {
            // 远程配置
            int port = prefs.getInt("remote_server_port", 8080);
            statusText = "当前配置: 远程传输\n方式: " + uploadMethod.toUpperCase() + "\n服务器: " + remoteUrl + ":" + port;
        } else {
            // 本地配置
            String localIp = prefs.getString("server_host", "192.168.1.116");
            int localPort = prefs.getInt("server_port", 12345);
            statusText = "当前配置: 本地传输\n方式: TCP\n服务器: " + localIp + ":" + localPort;
        }
        
        // 固定显示配置状态，避免布局下移
        String currentText = locationText.getText().toString();
        if (currentText.contains("当前配置:")) {
            // 更新配置状态部分，保持固定位置
            String[] parts = currentText.split("当前配置:");
            if (parts.length > 0) {
                // 移除可能存在的多余换行符，保持固定格式
                String baseText = parts[0].trim();
                if (!baseText.endsWith("\n")) {
                    baseText += "\n";
                }
                locationText.setText(baseText + statusText);
            }
        } else if (currentLocation != null) {
            // 只在有位置信息时添加配置状态
            locationText.setText(currentText + "\n\n" + statusText);
        }
    }

    private void startContinuousUpload() {
        isUploading = true;
        updateButtonStates();
        Toast.makeText(this, "开始上传位置数据", Toast.LENGTH_SHORT).show();
        
        // 启动GNSS监听
        startGNSSListening();
        
        // 启动前台服务
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction("START_UPLOAD");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 立即上传一次
        uploadLocation(getLatestOrPlaceholderLocation());
        
        // 设置定时上传（作为备用）
        handler.postDelayed(uploadRunnable, UPLOAD_INTERVAL);
    }

    private void stopContinuousUpload() {
        isUploading = false;
        updateButtonStates();
        
        // 停止GNSS监听
        stopGNSSListening();
        
        // 停止前台服务
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction("STOP_UPLOAD");
        stopService(serviceIntent);
        
        handler.removeCallbacks(uploadRunnable);
        Toast.makeText(this, "停止上传", Toast.LENGTH_SHORT).show();
    }

    private Location getLatestOrPlaceholderLocation() {
        if (currentLocation != null) {
            long timeDiff = System.currentTimeMillis() - currentLocation.getTime();
            if (timeDiff < 30000) {
                return currentLocation;
            } else {
                Log.d(TAG, "当前位置超过30秒，改用占位数据: " + timeDiff + "ms");
            }
        }
        return createSuppressedPlaceholderLocation();
    }

    private Location createSuppressedPlaceholderLocation() {
        Location placeholder = new Location(LocationManager.GPS_PROVIDER);
        placeholder.setLatitude(0.0);
        placeholder.setLongitude(0.0);
        placeholder.setAltitude(0.0);
        placeholder.setAccuracy(0.0f);
        placeholder.setSpeed(0.0f);
        placeholder.setBearing(0.0f);
        placeholder.setTime(System.currentTimeMillis());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            placeholder.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        Bundle extras = new Bundle();
        extras.putBoolean("is_suppressed_placeholder", true);
        placeholder.setExtras(extras);
        return placeholder;
    }

    private void uploadLocation(Location location) {
        // 检查是否配置了远程传输
        SharedPreferences prefs = getSharedPreferences("LocationUploader", MODE_PRIVATE);
        String remoteUrl = prefs.getString("remote_server_url", "");
        String uploadMethod = prefs.getString("upload_method", "local");
        
        Log.d(TAG, String.format("上传位置 - 远程URL: %s, 传输方式: %s", remoteUrl, uploadMethod));
        
        // 只有当明确配置了远程传输且选择了HTTP/HTTPS/UDP时才使用远程传输
        if (!remoteUrl.isEmpty() && !uploadMethod.equals("local")) {
            // 使用远程传输
            new RemoteUploadTask(this, uploadMethod).execute(location);
        } else {
            // 使用本地传输 - 默认使用二进制格式
            new UploadLocationTask(this, "binary").execute(location);
        }
    }

    private void updateLocationDisplay(Location location) {
        // 创建GNSS数据对象，使用真实GNSS数据
        GNSSData gnssData = GNSSData.fromLocation(location, locationManager);
        
        // 如果有真实的GNSS数据，使用真实数据
        if (gnssStatusListener != null && gnssStatusListener.isListening()) {
            List<GNSSData.SatelliteInfo> realSatellites = gnssStatusListener.getCurrentSatellites();
            if (!realSatellites.isEmpty()) {
                gnssData.setSatellites(realSatellites);
                // 计算真实的平均载噪比
                double avgCNR = gnssStatusListener.getAverageCNR();
                gnssData.setCnr(avgCNR);
            }
        }
        
        // 获取速度和方向信息，明确显示数据状态
        String speedInfo = location.hasSpeed() ? String.format("%.2f m/s", location.getSpeed()) : "0.00 m/s (静止)";
        String bearingInfo = location.hasBearing() ? String.format("%.1f°", location.getBearing()) : "0.0° (静止)";
        String accuracyInfo = String.format("%.1fm", location.getAccuracy());
        
        // 添加精度等级说明
        String accuracyLevel = "";
        if (location.getAccuracy() <= 5.0f) accuracyLevel = " (极高精度)";
        else if (location.getAccuracy() <= 10.0f) accuracyLevel = " (高精度)";
        else if (location.getAccuracy() <= 20.0f) accuracyLevel = " (中等精度)";
        else if (location.getAccuracy() <= 50.0f) accuracyLevel = " (低精度)";
        else accuracyLevel = " (极低精度)";
        
        // 添加定位方式说明
        String providerInfo = "";
        if ("gps".equals(location.getProvider()) || LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            providerInfo = "GPS卫星定位";
        } else if ("amap".equals(location.getProvider())) {
            providerInfo = "高德地图API定位";
        } else if ("baidu".equals(location.getProvider())) {
            providerInfo = "百度地图API定位";
        } else if ("network".equals(location.getProvider()) || LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
            providerInfo = "网络定位";
        } else {
            providerInfo = location.getProvider();
        }
        
        // 添加高度信息显示（保留3位小数）
        String altitudeInfo = location.hasAltitude() ? String.format("%.3f m", location.getAltitude()) : "0.000 m (无数据)";
        
        // 获取设备编号
        int deviceId = deviceManager != null ? deviceManager.getDeviceId() : 0;
        String deviceIdInfo = String.format("D/0/%d", deviceId);
        
        String locationInfo = String.format("位置信息:\n设备编号: %s\n纬度: %.6f\n经度: %.6f\n高度: %s\n精度: %s%s\n速度: %s\n方向: %s\n定位方式: %s\n\nGNSS卫星信息:\n卫星数量: %d颗\n载噪比: %.1fdB-Hz\nPDOP: %.2f\n定位状态: %s",
                deviceIdInfo,
                location.getLatitude(), location.getLongitude(),
                altitudeInfo,
                accuracyInfo, accuracyLevel,
                speedInfo,
                bearingInfo,
                providerInfo,
                gnssData.getNumSv(),
                gnssData.getCnr(),
                gnssData.getPdop(),
                gnssData.getStatus());
        locationText.setText(locationInfo);
        updateButtonStates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation();
            } else {
                Toast.makeText(this, "需要位置权限才能获取位置信息", Toast.LENGTH_SHORT).show();
                locationText.setText("权限被拒绝\n请在设置中手动授予位置权限");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates(); // 防止后台持续请求
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopContinuousUpload();
        stopLocationUpdates();
        if (gnssStatusListener != null) {
            gnssStatusListener.stopListening();
        }
        // if (networkLocationHelper != null) {
        //     networkLocationHelper.release();
        // }
    }

    // 显示本地网络配置对话框
    private void showNetworkConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("本地网络配置");

        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // IP地址输入框
        TextView ipLabel = new TextView(this);
        ipLabel.setText("服务器IP地址:");
        layout.addView(ipLabel);

        final EditText ipInput = new EditText(this);
        ipInput.setHint("例如: 192.168.1.116");
        // 加载当前配置的IP地址
        SharedPreferences prefs = getSharedPreferences("LocationUploader", MODE_PRIVATE);
        String currentIp = prefs.getString("server_host", "192.168.1.116");
        ipInput.setText(currentIp);
        layout.addView(ipInput);

        // 端口输入框
        TextView portLabel = new TextView(this);
        portLabel.setText("端口号:");
        layout.addView(portLabel);

        final EditText portInput = new EditText(this);
        portInput.setHint("例如: 12345");
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int currentPort = prefs.getInt("server_port", 12345);
        portInput.setText(String.valueOf(currentPort));
        layout.addView(portInput);

        builder.setView(layout);

        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String ip = ipInput.getText().toString().trim();
                String portStr = portInput.getText().toString().trim();
                
                if (ip.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入有效的IP地址和端口", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    int port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        Toast.makeText(MainActivity.this, "端口号必须在1-65535之间", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 保存配置
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("server_host", ip);
                    editor.putInt("server_port", port);
                    editor.apply();

                    Toast.makeText(MainActivity.this, "本地网络配置已保存", Toast.LENGTH_SHORT).show();
                    
                            // 显示当前配置
        String configInfo = "本地配置:\n服务器: " + ip + ":" + port + "\n传输方式: TCP";
        locationText.setText(configInfo);
                    
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", null);
        builder.setNeutralButton("测试连接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String ip = ipInput.getText().toString().trim();
                String portStr = portInput.getText().toString().trim();
                
                if (ip.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入有效的IP地址和端口", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    int port = Integer.parseInt(portStr);
                    // 在新线程中测试连接
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            testConnection(ip, port);
                        }
                    }).start();
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.show();
    }

    // 测试网络连接
    private void testConnection(final String ip, final int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(ip, port), 5000);
            socket.close();
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "连接成功: " + ip + ":" + port, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // 显示远程配置对话框
    private void showRemoteConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("远程网络配置");

        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 传输方式固定为TCP
        TextView methodLabel = new TextView(this);
        methodLabel.setText("传输方式: TCP");
        methodLabel.setTextColor(android.graphics.Color.GRAY);
        layout.addView(methodLabel);

        final String[] selectedMethod = {"TCP"};

        // 服务器地址输入框
        TextView urlLabel = new TextView(this);
        urlLabel.setText("服务器地址:");
        layout.addView(urlLabel);

        final EditText urlInput = new EditText(this);
        urlInput.setHint("例如: your-server.com 或 203.0.113.1");
        SharedPreferences prefs = getSharedPreferences("LocationUploader", MODE_PRIVATE);
        String currentUrl = prefs.getString("remote_server_url", "");
        urlInput.setText(currentUrl);
        layout.addView(urlInput);

        // 端口输入框
        TextView portLabel = new TextView(this);
        portLabel.setText("端口号:");
        layout.addView(portLabel);

        final EditText portInput = new EditText(this);
        portInput.setHint("例如: 8080");
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int currentPort = prefs.getInt("remote_server_port", 8080);
        portInput.setText(String.valueOf(currentPort));
        layout.addView(portInput);

        // SSL选项
        final android.widget.CheckBox sslCheckBox = new android.widget.CheckBox(this);
        sslCheckBox.setText("使用SSL加密");
        boolean useSSL = prefs.getBoolean("use_ssl", false);
        sslCheckBox.setChecked(useSSL);
        layout.addView(sslCheckBox);

        // 清除配置按钮
        android.widget.Button clearButton = new android.widget.Button(this);
        clearButton.setText("清除远程配置");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 清除远程配置，切换回本地传输
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove("remote_server_url");
                editor.remove("remote_server_port");
                editor.putString("upload_method", "local");
                editor.remove("use_ssl");
                editor.apply();
                
                Toast.makeText(MainActivity.this, "已清除远程配置，切换回本地传输", Toast.LENGTH_SHORT).show();
                updateConfigStatus();
            }
        });
        layout.addView(clearButton);

        builder.setView(layout);

        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String url = urlInput.getText().toString().trim();
                String portStr = portInput.getText().toString().trim();

                if (url.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入有效的服务器地址和端口", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    int port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        Toast.makeText(MainActivity.this, "端口号必须在1-65535之间", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 保存配置
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("remote_server_url", url);
                    editor.putInt("remote_server_port", port);
                    editor.putString("upload_method", selectedMethod[0].toLowerCase());
                    editor.putBoolean("use_ssl", sslCheckBox.isChecked());
                    editor.apply();

                    Toast.makeText(MainActivity.this, "远程配置已保存", Toast.LENGTH_SHORT).show();

                    // 显示当前配置
                    String configInfo = "远程配置:\n方式: " + selectedMethod[0] + "\n服务器: " + url + ":" + port + "\n传输方式: " + selectedMethod[0].toUpperCase();
                    locationText.setText(configInfo);

                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", null);
        builder.setNeutralButton("测试连接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String url = urlInput.getText().toString().trim();
                String portStr = portInput.getText().toString().trim();

                if (url.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入有效的服务器地址和端口", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    int port = Integer.parseInt(portStr);
                    // 在新线程中测试连接
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            testRemoteConnection(url, port, selectedMethod[0]);
                        }
                    }).start();
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.show();
    }

    // 测试远程连接 - 仅支持TCP
    private void testRemoteConnection(final String url, final int port, final String method) {
        try {
            // 只支持TCP测试
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(url, port), 5000);
            socket.close();
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "TCP连接成功: " + url + ":" + port, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            final String error = "TCP连接失败: " + e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // LocationListener 接口方法
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            locationAttempts++;
            Log.d(TAG, "位置更新 #" + locationAttempts + ", 精度: " + location.getAccuracy() + "m, 提供者: " + location.getProvider());
            
            // 实时更新位置信息，不等待高精度
            currentLocation = location;
            updateLocationDisplay(location);
            
            // 检查位置精度，降低要求到100米
            if (location.getAccuracy() <= ACCURACY_THRESHOLD) {
                stopLocationUpdates(); // 获取高精度后停止请求
                Toast.makeText(this, "GPS高精度定位成功！(精度" + 
                    String.format("%.1fm", location.getAccuracy()) + ")", Toast.LENGTH_SHORT).show();
            } else if (locationAttempts >= MAX_ATTEMPTS) {
                // 达到最大尝试次数，停止请求
                stopLocationUpdates();
                Toast.makeText(this, "GPS定位达到最大尝试次数，使用当前最佳位置", Toast.LENGTH_SHORT).show();
            } else {
                // 实时显示当前精度，不覆盖位置信息
                String currentText = locationText.getText().toString();
                if (!currentText.contains("GPS实时精度")) {
                    locationText.setText(currentText + "\n\nGPS实时精度: " + String.format("%.1fm (尝试 %d/%d)", 
                        location.getAccuracy(), locationAttempts, MAX_ATTEMPTS));
                } else {
                    // 更新精度信息
                    String[] lines = currentText.split("\n");
                    StringBuilder newText = new StringBuilder();
                    for (String line : lines) {
                        if (!line.startsWith("GPS实时精度")) {
                            newText.append(line).append("\n");
                        }
                    }
                    newText.append("GPS实时精度: " + String.format("%.1fm (尝试 %d/%d)", 
                        location.getAccuracy(), locationAttempts, MAX_ATTEMPTS));
                    locationText.setText(newText.toString());
                }
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "位置提供者状态改变: " + provider + ", 状态: " + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "位置提供者已启用: " + provider);
        Toast.makeText(this, "位置提供者已启用: " + provider, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "位置提供者已禁用: " + provider);
        Toast.makeText(this, "位置提供者已禁用: " + provider, Toast.LENGTH_SHORT).show();
    }
    
    // 新增：更新GNSS状态显示
    private void updateGNSSStatusDisplay(List<GNSSData.SatelliteInfo> satellites) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gnssStatusText != null) {
                    StringBuilder status = new StringBuilder();
                    status.append("GNSS状态:\n");
                    
                    if (satellites.isEmpty()) {
                        status.append("无卫星信号\n");
                        status.append("建议: 移至室外或靠近窗户");
                    } else {
                        int totalSatellites = satellites.size();
                        int beidouCount = 0;
                        int gpsCount = 0;
                        int usedInFixCount = 0;
                        double totalCNR = 0.0;
                        int strongSignals = 0;
                        int mediumSignals = 0;
                        int weakSignals = 0;
                        
                        for (GNSSData.SatelliteInfo sat : satellites) {
                            if (sat.gnssId == 7) beidouCount++; // 北斗
                            if (sat.gnssId == 1) gpsCount++; // GPS
                            if (sat.usedInFix) usedInFixCount++;
                            totalCNR += sat.cnos;
                            
                            // 统计信号强度分布
                            if (sat.cnos > 30) strongSignals++;
                            else if (sat.cnos > 20) mediumSignals++;
                            else weakSignals++;
                        }
                        
                        double avgCNR = totalCNR / totalSatellites;
                        
                        status.append(String.format("总卫星数: %d\n", totalSatellites));
                        status.append(String.format("北斗卫星: %d\n", beidouCount));
                        status.append(String.format("GPS卫星: %d\n", gpsCount));
                        status.append(String.format("用于定位: %d\n", usedInFixCount));
                        status.append(String.format("平均载噪比: %.1f dB-Hz\n", avgCNR));
                        status.append(String.format("信号分布: 强%d/中%d/弱%d", strongSignals, mediumSignals, weakSignals));
                        
                        // 添加信号质量建议
                        if (weakSignals > strongSignals + mediumSignals) {
                            status.append("\n建议: 移至室外或靠近窗户");
                        } else if (totalSatellites < 5) {
                            status.append("\n建议: 等待更多卫星信号");
                        }
                    }
                    
                    gnssStatusText.setText(status.toString());
                }
            }
        });
    }
    
    // 新增：启动GNSS监听
    private void startGNSSListening() {
        if (gnssStatusListener != null && gnssStatusListener.isSupported()) {
            boolean started = gnssStatusListener.startListening();
            if (started) {
                Log.d(TAG, "GNSS监听已启动");
            } else {
                Log.w(TAG, "GNSS监听启动失败");
            }
        }
    }
    
    // 新增：停止GNSS监听
    private void stopGNSSListening() {
        if (gnssStatusListener != null) {
            gnssStatusListener.stopListening();
            Log.d(TAG, "GNSS监听已停止");
        }
    }
    

}