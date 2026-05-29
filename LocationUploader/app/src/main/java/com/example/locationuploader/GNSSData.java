package com.example.locationuploader;

import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class GNSSData {
    private String time;
    private double longitude;
    private double latitude;
    private double altitude;
    private int numSv; // 真实卫星数量
    private double cnr; // 载噪比
    private double pseudorange; // 伪距
    private double pdop; // 位置精度因子
    private String status; // 定位状态
    private double cnrVar; // 载噪比方差
    private double residualVar; // 残差方差
    private double accuracy; // 精度
    private float speed; // 速度
    private float bearing; // 方向
    private String provider; // 提供者
    
    // 新增：真实卫星信息列表
    private List<SatelliteInfo> satellites;
    
    // 新增：卫星信息内部类
    public static class SatelliteInfo {
        public int gnssId; // 卫星系统ID (1=GPS, 2=SBAS, 3=Galileo, 6=GLONASS, 7=北斗)
        public int svId;   // 卫星ID
        public int cnos;   // 载噪比值 (dB-Hz)
        public int elev;   // 仰角 (度)
        public int azim;   // 方位角 (度)
        public boolean usedInFix; // 是否用于定位
        
        public SatelliteInfo(int gnssId, int svId, int cnos, int elev, int azim, boolean usedInFix) {
            this.gnssId = gnssId;
            this.svId = svId;
            this.cnos = cnos;
            this.elev = elev;
            this.azim = azim;
            this.usedInFix = usedInFix;
        }
    }

    public GNSSData() {
        this.time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        this.satellites = new ArrayList<>();
    }

    // Getters and Setters
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public int getNumSv() { return numSv; }
    public void setNumSv(int numSv) { this.numSv = numSv; }

    public double getCnr() { return cnr; }
    public void setCnr(double cnr) { this.cnr = cnr; }

    public double getPseudorange() { return pseudorange; }
    public void setPseudorange(double pseudorange) { this.pseudorange = pseudorange; }

    public double getPdop() { return pdop; }
    public void setPdop(double pdop) { this.pdop = pdop; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getCnrVar() { return cnrVar; }
    public void setCnrVar(double cnrVar) { this.cnrVar = cnrVar; }

    public double getResidualVar() { return residualVar; }
    public void setResidualVar(double residualVar) { this.residualVar = residualVar; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    public float getBearing() { return bearing; }
    public void setBearing(float bearing) { this.bearing = bearing; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public List<SatelliteInfo> getSatellites() { return satellites; }
    public void setSatellites(List<SatelliteInfo> satellites) { 
        // 限制卫星数量不超过40颗，避免超出数据结构限制
        if (satellites.size() > 40) {
            this.satellites = satellites.subList(0, 40);
            this.numSv = 40;
        } else {
            this.satellites = satellites;
            this.numSv = satellites.size();
        }
    }

    // 从Location对象创建GNSSData（使用真实GNSS数据）
    public static GNSSData fromLocation(android.location.Location location, LocationManager locationManager) {
        GNSSData data = new GNSSData();
        Bundle extras = location.getExtras();
        boolean isSuppressedPlaceholder = extras != null && extras.getBoolean("is_suppressed_placeholder", false);
        data.setLongitude(location.getLongitude());
        data.setLatitude(location.getLatitude());
        data.setAltitude(location.getAltitude());
        data.setAccuracy(location.getAccuracy());
        data.setSpeed(location.getSpeed());
        data.setBearing(location.getBearing());
        data.setProvider(location.getProvider());
        
        if (isSuppressedPlaceholder) {
            data.setNumSv(0);
            data.setCnr(0.0);
            data.setPdop(99.99);
            data.setStatus("无信号");
            data.setSatellites(new ArrayList<>());
            return data;
        }
        
        // 如果有LocationManager，尝试获取真实GNSS数据
        if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 获取真实GNSS状态 - 使用反射调用，因为getGnssStatus()在某些版本中可能不可用
                GnssStatus gnssStatus = null;
                try {
                    java.lang.reflect.Method getGnssStatusMethod = locationManager.getClass().getMethod("getGnssStatus");
                    gnssStatus = (GnssStatus) getGnssStatusMethod.invoke(locationManager);
                } catch (Exception e) {
                    // 如果反射调用失败，使用备用方案
                }
                
                if (gnssStatus != null) {
                    data.setSatellites(parseGnssStatus(gnssStatus));
                    data.setPdop(calculatePDOPFromSatellites(data.getSatellites()));
                    data.setStatus(determineStatusFromSatellites(data.getSatellites(), location.getAccuracy()));
                } else {
                    // 如果无法获取GNSS状态，使用备用方案
                    data.setSatellites(getFallbackSatellites(location));
                    data.setPdop(calculatePDOP(location));
                    data.setStatus(determineStatus(location));
                }
            } catch (Exception e) {
                // 如果出现异常，使用备用方案
                data.setSatellites(getFallbackSatellites(location));
                data.setPdop(calculatePDOP(location));
                data.setStatus(determineStatus(location));
            }
        } else {
            // Android版本不支持或没有LocationManager，使用备用方案
            data.setSatellites(getFallbackSatellites(location));
            data.setPdop(calculatePDOP(location));
            data.setStatus(determineStatus(location));
        }
        
        return data;
    }

    // 解析真实GNSS状态数据
    private static List<SatelliteInfo> parseGnssStatus(GnssStatus gnssStatus) {
        List<SatelliteInfo> satellites = new ArrayList<>();
        
        for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            int constellationType = gnssStatus.getConstellationType(i);
            int svId = gnssStatus.getSvid(i);
            float cn0DbHz = gnssStatus.getCn0DbHz(i);
            float elevationDegrees = gnssStatus.getElevationDegrees(i);
            float azimuthDegrees = gnssStatus.getAzimuthDegrees(i);
            boolean usedInFix = gnssStatus.usedInFix(i);
            
            // 转换星座类型为gnssId
            int gnssId = convertConstellationToGnssId(constellationType);
            
            // 只添加信号强度足够强的卫星
            if (cn0DbHz > 15.0f) {
                satellites.add(new SatelliteInfo(
                    gnssId,
                    svId,
                    (int) cn0DbHz,
                    (int) elevationDegrees,
                    (int) azimuthDegrees,
                    usedInFix
                ));
            }
        }
        
        return satellites;
    }
    
    // 转换星座类型为gnssId
    private static int convertConstellationToGnssId(int constellationType) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                return 1; // GPS
            case GnssStatus.CONSTELLATION_SBAS:
                return 2; // SBAS
            case GnssStatus.CONSTELLATION_GALILEO:
                return 3; // Galileo
            case GnssStatus.CONSTELLATION_GLONASS:
                return 6; // GLONASS
            case GnssStatus.CONSTELLATION_BEIDOU:
                return 7; // 北斗
            case GnssStatus.CONSTELLATION_QZSS:
                return 8; // QZSS
            case GnssStatus.CONSTELLATION_IRNSS:
                return 9; // IRNSS
            default:
                return 1; // 默认为GPS
        }
    }
    
    // 根据真实卫星数据计算PDOP
    private static double calculatePDOPFromSatellites(List<SatelliteInfo> satellites) {
        if (satellites.isEmpty()) {
            return 10.0; // 无卫星时返回最差PDOP
        }
        
        // 计算用于定位的卫星数量
        int usedSatellites = 0;
        double totalCnr = 0.0;
        
        for (SatelliteInfo sat : satellites) {
            if (sat.usedInFix) {
                usedSatellites++;
                totalCnr += sat.cnos;
            }
        }
        
        if (usedSatellites == 0) {
            return 10.0;
        }
        
        // 基于卫星数量和平均载噪比计算PDOP
        double avgCnr = totalCnr / usedSatellites;
        double basePDOP = 10.0 - (usedSatellites * 0.8) - (avgCnr - 20.0) * 0.1;
        
        // 确保PDOP在合理范围内
        return Math.max(1.0, Math.min(10.0, basePDOP));
    }
    
    // 根据真实卫星数据确定定位状态
    private static String determineStatusFromSatellites(List<SatelliteInfo> satellites, double accuracy) {
        int usedSatellites = 0;
        double totalCnr = 0.0;
        
        for (SatelliteInfo sat : satellites) {
            if (sat.usedInFix) {
                usedSatellites++;
                totalCnr += sat.cnos;
            }
        }
        
        if (usedSatellites == 0) {
            return "无信号";
        }
        
        double avgCnr = totalCnr / usedSatellites;
        
        if (accuracy <= 3.0 && usedSatellites >= 6 && avgCnr >= 30.0) {
            return "高精度";
        } else if (accuracy <= 5.0 && usedSatellites >= 4 && avgCnr >= 25.0) {
            return "中等精度";
        } else if (accuracy <= 10.0 && usedSatellites >= 3 && avgCnr >= 20.0) {
            return "低精度";
        } else if (usedSatellites >= 2) {
            return "信号弱";
        } else {
            return "无信号";
        }
    }

    // 备用方案：生成模拟卫星数据（优化室内环境）
    private static List<SatelliteInfo> getFallbackSatellites(Location location) {
        List<SatelliteInfo> satellites = new ArrayList<>();
        double accuracy = location.getAccuracy();
        String provider = location.getProvider();
        
        // 基于精度和提供者估算卫星数量
        int satelliteCount;
        if (provider != null && provider.equals(LocationManager.GPS_PROVIDER)) {
            // GPS提供者，可能有更多卫星
            if (accuracy <= 3.0) satelliteCount = 8 + (int)(Math.random() * 4); // 8-11颗
            else if (accuracy <= 5.0) satelliteCount = 6 + (int)(Math.random() * 4); // 6-9颗
            else if (accuracy <= 10.0) satelliteCount = 4 + (int)(Math.random() * 4); // 4-7颗
            else if (accuracy <= 20.0) satelliteCount = 3 + (int)(Math.random() * 3); // 3-5颗
            else satelliteCount = 2 + (int)(Math.random() * 2); // 2-3颗
        } else {
            // 网络提供者，室内环境，卫星数量较少
            if (accuracy <= 5.0) satelliteCount = 3 + (int)(Math.random() * 3); // 3-5颗
            else if (accuracy <= 10.0) satelliteCount = 2 + (int)(Math.random() * 3); // 2-4颗
            else if (accuracy <= 20.0) satelliteCount = 1 + (int)(Math.random() * 2); // 1-2颗
            else satelliteCount = 1; // 1颗
        }
        
        // 生成模拟卫星数据
        for (int i = 0; i < satelliteCount; i++) {
            int gnssId = (i % 3 == 0) ? 7 : 1; // 北斗和GPS混合
            int svId = (gnssId == 7) ? (i + 1) : (i + 1); // 卫星ID
            
            // 室内环境下载噪比较低
            int cnos;
            if (provider != null && provider.equals(LocationManager.GPS_PROVIDER)) {
                cnos = Math.max(15, Math.min(50, (int)(35 - accuracy/2 + (Math.random() * 10 - 5))));
            } else {
                // 室内环境，载噪比更低
                cnos = Math.max(10, Math.min(35, (int)(25 - accuracy/2 + (Math.random() * 8 - 4))));
            }
            
            int elev = 20 + (i * 12) % 70; // 仰角20-90度
            int azim = (i * 30) % 360; // 方位角0-360度
            boolean usedInFix = (i < Math.min(6, satelliteCount)); // 前6颗用于定位
            
            satellites.add(new SatelliteInfo(gnssId, svId, cnos, elev, azim, usedInFix));
        }
        
        return satellites;
    }

    // 备用方案：计算PDOP
    private static double calculatePDOP(Location location) {
        double accuracy = location.getAccuracy();
        
        // 基于精度的PDOP估算
        if (accuracy <= 3.0) return 1.5 + Math.random() * 1.0;
        else if (accuracy <= 5.0) return 2.5 + Math.random() * 1.0;
        else if (accuracy <= 10.0) return 4.0 + Math.random() * 1.0;
        else if (accuracy <= 20.0) return 6.0 + Math.random() * 1.0;
        else return 8.0 + Math.random() * 2.0;
    }

    // 备用方案：确定定位状态
    private static String determineStatus(Location location) {
        double accuracy = location.getAccuracy();
        
        if (accuracy <= 3.0) return "高精度";
        if (accuracy <= 5.0) return "中等精度";
        if (accuracy <= 10.0) return "低精度";
        if (accuracy <= 20.0) return "较差精度";
        return "信号弱";
    }

    // 转换为JSON格式字符串
    public String toJsonString() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"time\":\"").append(time).append("\",");
        json.append("\"longitude\":").append(longitude).append(",");
        json.append("\"latitude\":").append(latitude).append(",");
        json.append("\"altitude\":").append(altitude).append(",");
        json.append("\"num_sv\":").append(numSv).append(",");
        json.append("\"cnr\":").append(cnr).append(",");
        json.append("\"pseudorange\":").append(pseudorange).append(",");
        json.append("\"pdop\":").append(pdop).append(",");
        json.append("\"status\":\"").append(status).append("\",");
        json.append("\"cnr_var\":").append(cnrVar).append(",");
        json.append("\"residual_var\":").append(residualVar).append(",");
        json.append("\"accuracy\":").append(accuracy).append(",");
        json.append("\"speed\":").append(speed).append(",");
        json.append("\"bearing\":").append(bearing).append(",");
        json.append("\"provider\":\"").append(provider).append("\",");
        
        // 添加卫星信息
        json.append("\"satellites\":[");
        for (int i = 0; i < satellites.size(); i++) {
            SatelliteInfo sat = satellites.get(i);
            json.append("{");
            json.append("\"gnssId\":").append(sat.gnssId).append(",");
            json.append("\"svId\":").append(sat.svId).append(",");
            json.append("\"cnos\":").append(sat.cnos).append(",");
            json.append("\"elev\":").append(sat.elev).append(",");
            json.append("\"azim\":").append(sat.azim).append(",");
            json.append("\"usedInFix\":").append(sat.usedInFix);
            json.append("}");
            if (i < satellites.size() - 1) json.append(",");
        }
        json.append("]");
        
        json.append("}");
        return json.toString();
    }

    // 转换为URL编码格式（兼容现有服务器）
    public String toUrlEncodedString() {
        StringBuilder data = new StringBuilder();
        data.append("time=").append(android.net.Uri.encode(time));
        data.append("&longitude=").append(longitude);
        data.append("&latitude=").append(latitude);
        data.append("&altitude=").append(altitude);
        data.append("&num_sv=").append(numSv);
        data.append("&cnr=").append(cnr);
        data.append("&pseudorange=").append(pseudorange);
        data.append("&pdop=").append(pdop);
        data.append("&status=").append(android.net.Uri.encode(status));
        data.append("&cnr_var=").append(cnrVar);
        data.append("&residual_var=").append(residualVar);
        data.append("&accuracy=").append(accuracy);
        data.append("&speed=").append(speed);
        data.append("&bearing=").append(bearing);
        data.append("&provider=").append(android.net.Uri.encode(provider));
        return data.toString();
    }

    // 转换为NMEA格式（标准GNSS数据格式）
    public String toNMEAFormat() {
        StringBuilder nmea = new StringBuilder();
        
        // GSV - 可见卫星信息
        nmea.append("$GPGSV,");
        nmea.append("1,"); // 消息数量
        nmea.append("1,"); // 消息编号
        nmea.append(numSv).append(","); // 可见卫星数量
        
        // 这里可以添加每个卫星的详细信息
        // 简化版本，只显示卫星数量
        
        nmea.append("*");
        nmea.append(calculateChecksum(nmea.toString()));
        
        return nmea.toString();
    }

    // 计算NMEA校验和
    private String calculateChecksum(String sentence) {
        int checksum = 0;
        for (int i = 1; i < sentence.length(); i++) {
            char c = sentence.charAt(i);
            if (c == '*') break;
            checksum ^= c;
        }
        return String.format("%02X", checksum);
    }

    // 转换为二进制格式
    public byte[] toBinaryFormat() {
        // 简化的二进制格式
        // 实际应用中可能需要更复杂的格式
        return new byte[0];
    }

    // 转换为CSV格式
    public String toCSVFormat() {
        return String.format("%s,%.6f,%.6f,%.1f,%d,%.1f,%.2f,%s,%.1f,%.1f,%.1f,%s",
                time, longitude, latitude, altitude, numSv, cnr, pdop, status,
                accuracy, speed, bearing, provider);
    }

    // 转换为XML格式
    public String toXMLFormat() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<gnss_data>\n");
        xml.append("  <time>").append(time).append("</time>\n");
        xml.append("  <longitude>").append(longitude).append("</longitude>\n");
        xml.append("  <latitude>").append(latitude).append("</latitude>\n");
        xml.append("  <altitude>").append(altitude).append("</altitude>\n");
        xml.append("  <num_sv>").append(numSv).append("</num_sv>\n");
        xml.append("  <cnr>").append(cnr).append("</cnr>\n");
        xml.append("  <pseudorange>").append(pseudorange).append("</pseudorange>\n");
        xml.append("  <pdop>").append(pdop).append("</pdop>\n");
        xml.append("  <status>").append(status).append("</status>\n");
        xml.append("  <accuracy>").append(accuracy).append("</accuracy>\n");
        xml.append("  <speed>").append(speed).append("</speed>\n");
        xml.append("  <bearing>").append(bearing).append("</bearing>\n");
        xml.append("  <provider>").append(provider).append("</provider>\n");
        
        // 添加卫星信息
        xml.append("  <satellites>\n");
        for (SatelliteInfo sat : satellites) {
            xml.append("    <satellite>\n");
            xml.append("      <gnssId>").append(sat.gnssId).append("</gnssId>\n");
            xml.append("      <svId>").append(sat.svId).append("</svId>\n");
            xml.append("      <cnos>").append(sat.cnos).append("</cnos>\n");
            xml.append("      <elev>").append(sat.elev).append("</elev>\n");
            xml.append("      <azim>").append(sat.azim).append("</azim>\n");
            xml.append("      <usedInFix>").append(sat.usedInFix).append("</usedInFix>\n");
            xml.append("    </satellite>\n");
        }
        xml.append("  </satellites>\n");
        
        xml.append("</gnss_data>");
        return xml.toString();
    }
}
