import socket
import json
import datetime
import time
import logging
import threading
import struct
from datetime import timezone
from collections import defaultdict

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('server.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class LocationServer:
    def __init__(self, host='0.0.0.0', port=12345):
        self.host = host
        self.port = port
        self.server_socket = None
        self.clients = {}  # 存储客户端连接信息
        self.stats = defaultdict(int)  # 统计信息
        self.running = False
        
    def start(self):
        """启动服务器"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)  # 增加连接队列大小
            
            self.running = True
            logger.info(f" 服务器启动成功！")
            logger.info(f" 监听地址: {self.host}:{self.port}")
            logger.info(f" 等待客户端连接...")
            
            # 启动统计信息线程
            stats_thread = threading.Thread(target=self._print_stats, daemon=True)
            stats_thread.start()
            
            while self.running:
                try:
                    client_socket, addr = self.server_socket.accept()
                    client_thread = threading.Thread(
                        target=self._handle_client, 
                        args=(client_socket, addr),
                        daemon=True
                    )
                    client_thread.start()
                    
                except socket.error as e:
                    if self.running:
                        logger.error(f"接受连接时出错: {e}")
                        
        except Exception as e:
            logger.error(f"服务器启动失败: {e}")
        finally:
            self.stop()
    
    def stop(self):
        """停止服务器"""
        self.running = False
        if self.server_socket:
            self.server_socket.close()
        logger.info("🛑 服务器已停止")
    
    def _handle_client(self, client_socket, addr):
        """处理客户端连接"""
        client_id = f"{addr[0]}:{addr[1]}"
        self.clients[client_id] = {
            'socket': client_socket,
            'addr': addr,
            'connected_time': time.time(),
            'data_count': 0
        }
        
        logger.info(f"✅ 客户端 {client_id} 已连接")
        
        try:
            # 设置socket选项，提高处理速度
            client_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            client_socket.settimeout(5.0)  # 5秒超时
            
            while self.running:
                try:
                    raw_data = client_socket.recv(4096)
                    if not raw_data:
                        break
                    
                    self.stats['total_packets'] += 1
                    self.clients[client_id]['data_count'] += 1
                    
                    # 立即发送响应，避免客户端超时
                    try:
                        client_socket.send(b"OK")
                    except:
                        pass
                    
                    # 处理数据
                    self._process_data(raw_data, addr)
                    
                except socket.timeout:
                    # 超时检查连接是否还活着
                    try:
                        client_socket.send(b'ping')
                    except:
                        logger.info(f"客户端 {client_id} 连接已断开")
                        break
                        
        except Exception as e:
            logger.error(f"处理客户端 {client_id} 数据时出错: {e}")
        finally:
            self._cleanup_client(client_id)
    
    def _process_data(self, raw_data, addr):
        """处理接收到的数据"""
        try:
            # 检查是否为二进制数据
            if len(raw_data) >= 24 and int.from_bytes(raw_data[0:4], byteorder='little') == 0xAAAABBBB:
                self.stats['binary_packets'] += 1
                logger.info(f"📦 接收到二进制数据，长度: {len(raw_data)} 字节")
                location_data = self._parse_binary_data(raw_data)
                if location_data:
                    self._display_location_info(location_data, addr)
                else:
                    logger.warning(f"❌ 二进制数据解析失败")
            else:
                # 文本数据
                self.stats['text_packets'] += 1
                try:
                    data = raw_data.decode('utf-8')
                    logger.info(f"📝 接收到的文本数据: {data}")
                    
                    location_data = self._parse_location_data(data)
                    if location_data:
                        self._display_location_info(location_data, addr)
                    else:
                        logger.warning(f"❌ 文本数据解析失败: {data}")
                except UnicodeDecodeError:
                    logger.warning(f"❌ 无法解码的数据，长度: {len(raw_data)} 字节")
                    logger.warning(f"🔍 前16字节: {raw_data[:16].hex()}")
                    
        except Exception as e:
            logger.error(f"处理数据时出错: {e}")
    
    def _parse_location_data(self, data):
        """解析位置数据，支持多种格式"""
        try:
            # 检查是否为二进制格式（优先检查，因为二进制数据可能包含任何字节）
            if len(data) >= 24:  # 二进制数据包最小长度
                # 检查固定标识符
                if (len(data) >= 4 and int.from_bytes(data[0:4], byteorder='little') == 0xAAAABBBB):
                    return self._parse_binary_data(data)
            
            # 如果不是二进制格式，将字节数组转换为字符串
            try:
                data_str = data.decode('utf-8')
            except UnicodeDecodeError:
                logger.error(f"无法解码的数据，长度: {len(data)} 字节")
                return None
            
            # 检测数据格式
            if data_str.strip().startswith('{'):
                return self._parse_json_data(data_str)
            elif data_str.strip().startswith('$'):
                return self._parse_nmea_data(data_str)
            elif data_str.strip().startswith('<?xml'):
                return self._parse_xml_data(data_str)
            else:
                return self._parse_custom_format(data_str)
                
        except Exception as e:
            logger.error(f"解析位置数据时出错: {e}")
            return None

    def _parse_binary_data(self, data):
        """解析二进制数据"""
        try:
            if len(data) < 24:
                return None
                
            # 检查标识符 (TTI = 0xAAAABBBB)
            if int.from_bytes(data[0:4], byteorder='little') != 0xAAAABBBB:
                return None
                
            # 解析TCP_FrameHeader (20字节) - 对应gnss_data.h中的TCP_FrameHeader
            tti = int.from_bytes(data[0:4], byteorder='little')  # TTI: 0xAAAABBBB
            seq = int.from_bytes(data[4:8], byteorder='little')  # Number: 序号
            package_type = int.from_bytes(data[8:10], byteorder='little')  # Package_Type: 0x00BB
            package_len = int.from_bytes(data[10:12], byteorder='little')  # Package_len: 0x0130
            check_word = int.from_bytes(data[12:16], byteorder='little')  # Check_Word: 0x0000BBBB
            node_name = int.from_bytes(data[16:20], byteorder='little')  # node_name: 0xD0000001
            
            # 解析GNSS_Data (从第20字节开始)
            offset = 20
            
            # NAV-PVT协议数据部分 (7字节) - 对应gnss_data.h
            year = int.from_bytes(data[offset:offset+2], byteorder='little')  # year
            month = data[offset+2]  # month
            day = data[offset+3]    # day
            hour = data[offset+4]   # hour
            minute = data[offset+5] # minute
            second = data[offset+6] # second
            offset += 7
            
            # 位置信息 (24字节) - 对应gnss_data.h中的位置字段
            lon = struct.unpack('<d', data[offset:offset+8])[0]  # lon: 经度
            offset += 8
            lat = struct.unpack('<d', data[offset:offset+8])[0]  # lat: 纬度
            offset += 8
            height = struct.unpack('<d', data[offset:offset+8])[0]  # height: 高度
            offset += 8
            
            # 运动信息 (12字节) - 对应gnss_data.h中的运动字段
            ground_speed = struct.unpack('<f', data[offset:offset+4])[0]  # groundSpeed: 地面速度
            offset += 4
            heading = struct.unpack('<f', data[offset:offset+4])[0]  # heading: 对地航向
            offset += 4
            pdop = struct.unpack('<f', data[offset:offset+4])[0]  # PDOP: 位置精度因子
            offset += 4
            
            # 卫星数量 (1字节) - 对应gnss_data.h中的satelliteCount
            satellite_count = data[offset]
            offset += 1
            
            # NAV-SAT协议数据部分 - 卫星信息 (240字节 = 40×6字节)
            # 对应gnss_data.h中的satellites[MAX_SATELLITES]
            satellites_info = []
            for i in range(40):  # MAX_SATELLITES = 40
                if offset + 6 <= len(data):
                    gnss_id = data[offset]      # gnssId: 卫星系统
                    sv_id = data[offset+1]      # svId: 卫星ID
                    cnos = data[offset+2]       # cnos: 载噪比值
                    elev = struct.unpack('b', bytes([data[offset+3]]))[0]  # elev: 仰角 (signed char)
                    azim = int.from_bytes(data[offset+4:offset+6], byteorder='little')  # azim: 方位角
                    
                    if gnss_id > 0:  # 只记录有效的卫星
                        satellites_info.append({
                            'gnss_id': gnss_id,
                            'sv_id': sv_id,
                            'cnos': cnos,
                            'elev': elev,
                            'azim': azim
                        })
                    
                    offset += 6
            
            # 构建时间戳
            try:
                dt = datetime.datetime(year, month, day, hour, minute, second)
            except:
                dt = datetime.datetime.now()
            
            # 构建节点名称字符串
            node_name_str = f"Android_Device_{node_name & 0x7FFFFFFF:07d}"
            
            return {
                'node_name': node_name_str,
                'seq': seq,
                'timestamp': dt,
                'lat': lat,
                'lon': lon,
                'alt': height,  # 使用height字段
                'speed': ground_speed,  # 使用ground_speed字段
                'bearing': heading,  # 使用heading字段
                'accuracy': pdop,  # 使用PDOP作为精度
                'satellites': satellite_count,  # 使用satellite_count字段
                'satellites_info': satellites_info,  # 添加详细卫星信息
                'provider': 'gps',
                'format': 'binary',
                'tti': hex(tti),
                'package_type': hex(package_type),
                'package_len': hex(package_len),
                'check_word': hex(check_word)
            }
            
        except Exception as e:
            logger.error(f"解析二进制数据时出错: {e}")
            return None

    def _parse_json_data(self, data):
        """解析JSON格式数据"""
        try:
            json_data = json.loads(data)
            result = {}
            
            # 映射JSON字段到标准字段
            field_mapping = {
                'node': 'node_name',
                'sequence': 'seq',
                'timestamp': 'timestamp',
                'latitude': 'lat',
                'longitude': 'lon',
                'altitude': 'alt',
                'speed': 'speed',
                'bearing': 'bearing',
                'accuracy': 'accuracy',
                'satellites': 'satellites',
                'provider': 'provider'
            }
            
            for json_key, std_key in field_mapping.items():
                if json_key in json_data:
                    result[std_key] = json_data[json_key]
            
            if result:
                result['format'] = 'json'
                return result
                
        except Exception as e:
            logger.error(f"解析JSON数据时出错: {e}")
        
        return None

    def _parse_nmea_data(self, data):
        """解析NMEA格式数据"""
        try:
            # 简单的NMEA解析示例
            lines = data.strip().split('\n')
            result = {}
            
            for line in lines:
                if line.startswith('$GPGGA') or line.startswith('$GNRMC'):
                    parts = line.split(',')
                    if len(parts) >= 10:
                        # 解析基本位置信息
                        if parts[2] and parts[4]:  # 有经纬度数据
                            try:
                                lat_raw = float(parts[2])
                                lon_raw = float(parts[4])
                                
                                # 转换为度格式
                                lat_deg = int(lat_raw / 100)
                                lat_min = lat_raw % 100
                                result['lat'] = lat_deg + lat_min / 60
                                
                                lon_deg = int(lon_raw / 100)
                                lon_min = lon_raw % 100
                                result['lon'] = lon_deg + lon_min / 60
                                
                                if parts[9]:  # 海拔
                                    result['alt'] = float(parts[9])
                                    
                            except ValueError:
                                pass
                
                elif line.startswith('$GPGSV') or line.startswith('$GNGSV'):
                    # 卫星信息
                    parts = line.split(',')
                    if len(parts) >= 4:
                        try:
                            result['satellites'] = int(parts[3])
                        except ValueError:
                            pass
            
            if result:
                result['format'] = 'nmea'
                return result
                
        except Exception as e:
            logger.error(f"解析NMEA数据时出错: {e}")
        
        return None

    def _parse_xml_data(self, data):
        """解析XML格式数据"""
        try:
            # 简单的XML解析示例
            import xml.etree.ElementTree as ET
            
            root = ET.fromstring(data)
            result = {}
            
            # 查找常见的位置标签
            for elem in root.iter():
                tag = elem.tag.lower()
                if tag in ['latitude', 'lat']:
                    result['lat'] = float(elem.text)
                elif tag in ['longitude', 'lon', 'lng']:
                    result['lon'] = float(elem.text)
                elif tag in ['altitude', 'alt']:
                    result['alt'] = float(elem.text)
                elif tag in ['speed']:
                    result['speed'] = float(elem.text)
                elif tag in ['accuracy']:
                    result['accuracy'] = float(elem.text)
                elif tag in ['satellites']:
                    result['satellites'] = int(elem.text)
                elif tag in ['provider']:
                    result['provider'] = elem.text
                elif tag in ['node', 'device']:
                    result['node_name'] = elem.text
            
            if result:
                result['format'] = 'xml'
                return result
                
        except Exception as e:
            logger.error(f"解析XML数据时出错: {e}")
        
        return None

    def _parse_custom_format(self, data):
        """解析自定义格式数据"""
        try:
            # 解析类似 "Node: Android_Device_001 | Seq: 1 | Time: ..." 的格式
            parts = data.split('|')
            result = {}
            
            for part in parts:
                part = part.strip()
                if ':' in part:
                    key, value = part.split(':', 1)
                    key = key.strip()
                    value = value.strip()
                    
                    if key == 'Node':
                        result['node_name'] = value
                    elif key == 'Seq':
                        result['seq'] = int(value)
                    elif key == 'Time':
                        result['timestamp'] = value
                    elif key == 'Lat':
                        result['lat'] = float(value.replace('°', ''))
                    elif key == 'Lon':
                        result['lon'] = float(value.replace('°', ''))
                    elif key == 'Alt':
                        result['alt'] = float(value.replace('m', ''))
                    elif key == 'Speed':
                        result['speed'] = float(value.replace('m/s', ''))
                    elif key == 'Bearing':
                        result['bearing'] = float(value.replace('°', ''))
                    elif key == 'Accuracy':
                        result['accuracy'] = float(value.replace('m', ''))
                    elif key == 'Satellites':
                        result['satellites'] = int(value)
                    elif key == 'Provider':
                        result['provider'] = value
            
            if result:
                result['format'] = 'text'
                return result
                
        except Exception as e:
            logger.error(f"解析自定义格式数据时出错: {e}")
        
        return None

    def _display_location_info(self, location_data, addr):
        """显示位置信息 - 增强多设备支持"""
        try:
            if not location_data:
                return
                
            print("\n" + "="*80)
            print(f"Send_Buf_Data from {addr[0]}:{addr[1]}")
            print("="*80)
            
            # 显示设备信息
            if 'node_name' in location_data:
                device_name = location_data['node_name']
                print(f"设备名称: {device_name}")
                
                # 提取设备ID
                if 'node_name_int' in location_data:
                    device_id = location_data['node_name_int'] & 0x7FFFFFFF
                    print(f"设备ID: {device_id:07d}")
                    print(f"NodeName: 0x{location_data['node_name_int']:08X}")
                print()
            
            # 严格按照gnss_data.h结构体格式显示
            print("TCP_FrameHeader {")
            if 'tti' in location_data:
                print(f"    TTI: {location_data['tti']}")
            if 'seq' in location_data:
                print(f"    Number: {location_data['seq']}")
            if 'package_type' in location_data:
                print(f"    Package_Type: {location_data['package_type']}")
            if 'package_len' in location_data:
                print(f"    Package_len: {location_data['package_len']}")
            if 'check_word' in location_data:
                print(f"    Check_Word: {location_data['check_word']}")
            if 'node_name' in location_data:
                print(f"    node_name: {location_data['node_name']}")
            print("}")
            print()
            
            print("GNSS_Data {")
            print("    // NAV-PVT协议数据部分")
            if 'timestamp' in location_data:
                print(f"    year: {location_data['timestamp'].year}")
                print(f"    month: {location_data['timestamp'].month}")
                print(f"    day: {location_data['timestamp'].day}")
                print(f"    hour: {location_data['timestamp'].hour}")
                print(f"    minute: {location_data['timestamp'].minute}")
                print(f"    second: {location_data['timestamp'].second}")
            if 'lon' in location_data:
                print(f"    lon: {location_data['lon']:.8f}")
            if 'lat' in location_data:
                print(f"    lat: {location_data['lat']:.8f}")
            if 'alt' in location_data:
                print(f"    height: {location_data['alt']:.3f}")
            if 'speed' in location_data:
                print(f"    groundSpeed: {location_data['speed']:.3f}")
            if 'bearing' in location_data:
                print(f"    heading: {location_data['bearing']:.3f}")
            if 'accuracy' in location_data:
                print(f"    PDOP: {location_data['accuracy']:.3f}")
            if 'satellites' in location_data:
                print(f"    satelliteCount: {location_data['satellites']}")
            print()
            
            print("    // NAV-SAT协议数据部分")
            print("    satellites[MAX_SATELLITES] {")
            if 'satellites_info' in location_data and location_data['satellites_info']:
                for i, sat in enumerate(location_data['satellites_info']):
                    print(f"        [{i}]: {{ gnssId: {sat['gnss_id']}, svId: {sat['sv_id']}, cnos: {sat['cnos']}, elev: {sat['elev']}, azim: {sat['azim']} }}")
            print("    }")
            print("}")
            print("="*80)

        except Exception as e:
            logger.error(f"显示位置信息时出错: {e}")
    
    def _cleanup_client(self, client_id):
        """清理客户端连接"""
        if client_id in self.clients:
            try:
                self.clients[client_id]['socket'].close()
            except:
                pass
            del self.clients[client_id]
            logger.info(f"❌ 客户端 {client_id} 已断开连接")
    
    def _print_stats(self):
        """定期打印统计信息"""
        while self.running:
            time.sleep(60)  # 每分钟打印一次
            if self.running:
                print("\n" + "📊"*20)
                print("📈 服务器统计信息")
                print("📊"*20)
                print(f"🔗 当前连接数: {len(self.clients)}")
                print(f"📦 总数据包: {self.stats['total_packets']}")
                print(f"📝 文本数据包: {self.stats['text_packets']}")
                print(f"🔢 二进制数据包: {self.stats['binary_packets']}")
                print("📊"*20 + "\n")

def main():
    """主函数"""
    server = LocationServer()
    
    try:
        server.start()
    except KeyboardInterrupt:
        logger.info("收到中断信号，正在关闭服务器...")
        server.stop()

if __name__ == "__main__":
    main()