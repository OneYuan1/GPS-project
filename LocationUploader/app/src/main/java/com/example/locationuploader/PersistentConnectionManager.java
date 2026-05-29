package com.example.locationuploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class PersistentConnectionManager {
    private static final String TAG = "PersistentConnectionManager";
    private Context context;
    private Socket persistentSocket = null;
    private OutputStream outputStream = null;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private String serverHost;
    private int serverPort;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT = 3000; // 减少连接超时
    private static final int SOCKET_TIMEOUT = 5000;  // 减少socket超时

    public PersistentConnectionManager(Context context) {
        this.context = context;
        initializeConnection();
    }

    private void initializeConnection() {
        SharedPreferences prefs = context.getSharedPreferences("LocationUploader", Context.MODE_PRIVATE);
        serverHost = prefs.getString("server_host", "192.168.1.116");
        serverPort = prefs.getInt("server_port", 12345);
    }

    public synchronized boolean connectToServer() {
        try {
            if (isConnected.get() && persistentSocket != null && !persistentSocket.isClosed()) {
                return true; // 已经连接
            }

            Log.d(TAG, "正在连接到服务器: " + serverHost + ":" + serverPort);
            persistentSocket = new Socket();
            persistentSocket.connect(new java.net.InetSocketAddress(serverHost, serverPort), CONNECT_TIMEOUT);
            persistentSocket.setSoTimeout(SOCKET_TIMEOUT);
            persistentSocket.setTcpNoDelay(true); // 禁用Nagle算法，减少延迟
            
            outputStream = persistentSocket.getOutputStream();
            isConnected.set(true);
            reconnectAttempts = 0;
            
            Log.d(TAG, "成功连接到服务器");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "连接服务器失败: " + e.getMessage());
            isConnected.set(false);
            closeConnection();
            return false;
        }
    }

    public synchronized void closeConnection() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (persistentSocket != null) {
                persistentSocket.close();
                persistentSocket = null;
            }
            isConnected.set(false);
        } catch (Exception e) {
            Log.e(TAG, "关闭连接时出错: " + e.getMessage());
        }
    }

    public synchronized boolean sendData(byte[] data) {
        try {
            // 确保连接
            if (!isConnected.get()) {
                if (!connectToServer()) {
                    return false;
                }
            }

            // 检查连接是否仍然有效
            if (persistentSocket == null || persistentSocket.isClosed() || !persistentSocket.isConnected()) {
                Log.w(TAG, "连接已断开，重新连接");
                isConnected.set(false);
                if (!connectToServer()) {
                    return false;
                }
            }

            // 发送数据
            outputStream.write(data);
            outputStream.flush();
            Log.d(TAG, "数据已发送，长度: " + data.length + " 字节");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "发送数据失败: " + e.getMessage());
            // 标记连接为断开状态
            isConnected.set(false);
            
            // 尝试重连
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                Log.d(TAG, "尝试重连，第 " + reconnectAttempts + " 次");
                closeConnection();
                if (connectToServer()) {
                    return sendData(data); // 重连成功，重新发送数据
                }
            }
            return false;
        }
    }

    public synchronized boolean isConnected() {
        try {
            return isConnected.get() && persistentSocket != null && !persistentSocket.isClosed() && persistentSocket.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "检查连接状态时出错: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        Log.d(TAG, "断开持久连接");
        closeConnection();
    }
}
