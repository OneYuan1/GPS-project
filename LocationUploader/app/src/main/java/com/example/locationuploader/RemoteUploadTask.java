package com.example.locationuploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 远程上传任务 - 支持公网传输
 * 支持多种传输方式：HTTP、HTTPS、WebSocket、UDP
 */
public class RemoteUploadTask extends AsyncTask<Location, Void, String> {
    private static final String TAG = "RemoteUploadTask";
    private Context context;
    private String uploadMethod = "http"; // http, https, websocket, udp
    private String serverUrl = "";
    private int serverPort = 80;
    private boolean useSSL = false;

    public RemoteUploadTask(Context context) {
        this.context = context;
        // 修复：不设置默认的uploadMethod，让loadConfig()从SharedPreferences读取
        loadConfig();
    }

    public RemoteUploadTask(Context context, String uploadMethod) {
        this.context = context;
        this.uploadMethod = uploadMethod;
        loadConfig();
    }

    private void loadConfig() {
        SharedPreferences prefs = context.getSharedPreferences("LocationUploader", Context.MODE_PRIVATE);
        serverUrl = prefs.getString("remote_server_url", "");
        serverPort = prefs.getInt("remote_server_port", 80);
        useSSL = prefs.getBoolean("use_ssl", false);
        
        // 修复：从SharedPreferences读取传输方式
        String savedMethod = prefs.getString("upload_method", "tcp");
        // 如果构造函数没有传入uploadMethod（使用默认值"http"），则使用SharedPreferences中保存的值
        if (uploadMethod.equals("http")) {
            uploadMethod = savedMethod;
        }
        
        Log.d(TAG, String.format("加载配置 - 传输方式: %s, 服务器: %s:%d, SSL: %s", 
            uploadMethod, serverUrl, serverPort, useSSL));
    }

    @Override
    protected String doInBackground(Location... locations) {
        Location location = locations[0];
        
        try {
            switch (uploadMethod.toLowerCase()) {
                case "tcp":
                    return uploadViaTCP(location);
                case "http":
                case "https":
                    return uploadViaHttp(location);
                case "websocket":
                    return uploadViaWebSocket(location);
                case "udp":
                    return uploadViaUDP(location);
                default:
                    return uploadViaTCP(location); // 默认使用TCP
            }
        } catch (Exception e) {
            Log.e(TAG, "远程上传失败: " + e.getMessage(), e);
            return "远程上传失败: " + e.getMessage();
        }
    }

    /**
     * HTTP/HTTPS上传
     */
    private String uploadViaHttp(Location location) throws Exception {
        if (serverUrl.isEmpty()) {
            throw new Exception("未配置远程服务器地址");
        }

        // 构建完整的URL
        String protocol = useSSL ? "https" : "http";
        String fullUrl = protocol + "://" + serverUrl + ":" + serverPort + "/upload";
        
        Log.d(TAG, "上传到: " + fullUrl);

        // 创建GNSS数据
        GNSSData gnssData = GNSSData.fromLocation(location, null);
        
        // 优先使用二进制格式，如果服务器不支持则回退到JSON
        byte[] dataToSend;
        String contentType;
        
        try {
            // 尝试创建二进制数据包
            BinaryDataPacket packet = new BinaryDataPacket(context);
            dataToSend = packet.createPacket(location, gnssData);
            if (dataToSend != null) {
                contentType = "application/octet-stream";
                Log.d(TAG, "使用二进制格式上传，数据长度: " + dataToSend.length + " 字节");
            } else {
                // 回退到JSON格式
                String jsonData = gnssData.toJsonString();
                dataToSend = jsonData.getBytes(StandardCharsets.UTF_8);
                contentType = "application/json";
                Log.d(TAG, "回退到JSON格式上传");
            }
        } catch (Exception e) {
            // 回退到JSON格式
            String jsonData = gnssData.toJsonString();
            dataToSend = jsonData.getBytes(StandardCharsets.UTF_8);
            contentType = "application/json";
            Log.d(TAG, "二进制格式创建失败，回退到JSON格式: " + e.getMessage());
        }

        // 创建HTTP连接
        URL url = new URL(fullUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("User-Agent", "LocationUploader/1.0");
        connection.setDoOutput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        
        // 修复HTTP明文传输问题 - 允许HTTP传输
        if (connection instanceof javax.net.ssl.HttpsURLConnection) {
            // HTTPS连接，设置SSL配置
            javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection) connection;
            httpsConnection.setSSLSocketFactory(javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory());
        }

        // 发送数据
        try (OutputStream os = connection.getOutputStream()) {
            os.write(dataToSend, 0, dataToSend.length);
        }

        // 获取响应
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return "远程上传成功";
        } else {
            return "远程上传失败，响应码: " + responseCode;
        }
    }

    /**
     * WebSocket上传（基础实现）
     */
    private String uploadViaWebSocket(Location location) throws Exception {
        // 这里需要实现WebSocket客户端
        // 由于Android WebSocket实现较复杂，这里提供基础框架
        Log.d(TAG, "WebSocket上传功能待实现");
        return "WebSocket上传功能待实现";
    }

    /**
     * TCP上传
     */
    private String uploadViaTCP(Location location) throws Exception {
        if (serverUrl.isEmpty()) {
            throw new Exception("未配置远程服务器地址");
        }

        Log.d(TAG, "TCP上传到: " + serverUrl + ":" + serverPort);

        // 创建TCP socket
        java.net.Socket socket = new java.net.Socket();
        socket.setSoTimeout(10000); // 10秒超时
        socket.connect(new java.net.InetSocketAddress(serverUrl, serverPort), 10000);

        // 创建GNSS数据
        GNSSData gnssData = GNSSData.fromLocation(location, null);
        
        // 优先使用二进制格式
        byte[] dataToSend;
        try {
            // 尝试创建二进制数据包
            BinaryDataPacket packet = new BinaryDataPacket(context);
            dataToSend = packet.createPacket(location, gnssData);
            if (dataToSend == null) {
                throw new Exception("二进制数据包创建失败");
            }
            Log.d(TAG, "TCP使用二进制格式上传，数据长度: " + dataToSend.length + " 字节");
        } catch (Exception e) {
            // 回退到JSON格式
            String jsonData = gnssData.toJsonString();
            dataToSend = jsonData.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "TCP回退到JSON格式上传: " + e.getMessage());
        }

        // 发送数据
        try (java.io.OutputStream os = socket.getOutputStream()) {
            os.write(dataToSend, 0, dataToSend.length);
            os.flush();
        }

        // 关闭连接
        socket.close();

        return "TCP远程上传成功";
    }

    /**
     * UDP上传
     */
    private String uploadViaUDP(Location location) throws Exception {
        if (serverUrl.isEmpty()) {
            throw new Exception("未配置远程服务器地址");
        }

        // 创建UDP socket
        java.net.DatagramSocket socket = new java.net.DatagramSocket();
        socket.setSoTimeout(5000);

        // 准备数据
        GNSSData gnssData = GNSSData.fromLocation(location, null);
        String jsonData = gnssData.toJsonString();
        byte[] data = jsonData.getBytes(StandardCharsets.UTF_8);

        // 创建数据包
        java.net.DatagramPacket packet = new java.net.DatagramPacket(
            data, data.length, 
            java.net.InetAddress.getByName(serverUrl), serverPort
        );

        // 发送数据
        socket.send(packet);
        socket.close();

        return "UDP远程上传成功";
    }

    @Override
    protected void onPostExecute(String result) {
        Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "上传结果: " + result);
    }
}
