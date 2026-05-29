#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LocationUploader 远程服务器
支持HTTP、HTTPS、WebSocket、UDP等多种协议
可以部署在公网服务器上，实现真正的远程传输
"""

import socket
import json
import datetime
import time
import threading
import ssl
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('remote_server.log'),
        logging.StreamHandler()
    ]
)

class LocationDataHandler(BaseHTTPRequestHandler):
    """HTTP请求处理器"""
    
    def do_POST(self):
        """处理POST请求"""
        try:
            # 获取请求内容长度
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            # 解析JSON数据
            location_data = json.loads(post_data.decode('utf-8'))
            
            # 记录客户端信息
            client_ip = self.client_address[0]
            logging.info(f"收到来自 {client_ip} 的HTTP POST请求")
            
            # 处理位置数据
            self.process_location_data(location_data, client_ip)
            
            # 返回成功响应
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {"status": "success", "message": "数据接收成功"}
            self.wfile.write(json.dumps(response).encode('utf-8'))
            
        except Exception as e:
            logging.error(f"处理HTTP请求时出错: {e}")
            self.send_response(500)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {"status": "error", "message": str(e)}
            self.wfile.write(json.dumps(response).encode('utf-8'))
    
    def do_GET(self):
        """处理GET请求"""
        try:
            # 解析URL参数
            parsed_url = urlparse(self.path)
            params = parse_qs(parsed_url.query)
            
            # 如果是位置数据上传
            if parsed_url.path == '/upload' and params:
                location_data = {}
                for key, value in params.items():
                    location_data[key] = value[0] if value else ''
                
                client_ip = self.client_address[0]
                logging.info(f"收到来自 {client_ip} 的HTTP GET请求")
                
                # 处理位置数据
                self.process_location_data(location_data, client_ip)
                
                # 返回成功响应
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {"status": "success", "message": "数据接收成功"}
                self.wfile.write(json.dumps(response).encode('utf-8'))
            else:
                # 返回服务器状态
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {
                    "status": "running",
                    "server": "LocationUploader Remote Server",
                    "version": "1.0",
                    "time": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                }
                self.wfile.write(json.dumps(response, indent=2).encode('utf-8'))
                
        except Exception as e:
            logging.error(f"处理HTTP请求时出错: {e}")
            self.send_response(500)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {"status": "error", "message": str(e)}
            self.wfile.write(json.dumps(response).encode('utf-8'))
    
    def process_location_data(self, location_data, client_ip):
        """处理位置数据"""
        try:
            # 格式化时间
            time_value = location_data.get('time', '')
            formatted_time = self.format_time(time_value)
            
            # 显示位置信息
            print(f"\n📍 远程数据 - 来自 {client_ip}")
            print("=" * 60)
            print(f"⏰ 时间: {formatted_time}")
            print(f"🌍 位置: {location_data.get('latitude', 'N/A')}, {location_data.get('longitude', 'N/A')}")
            
            # 精度
            accuracy = location_data.get('accuracy', 'N/A')
            if accuracy != 'N/A':
                try:
                    accuracy = float(accuracy)
                    print(f"🎯 精度: {accuracy:.1f} 米")
                except ValueError:
                    print(f"🎯 精度: {accuracy}")
            
            # GNSS参数
            print("\n🛰️ GNSS专业参数:")
            print("-" * 30)
            
            num_sv = location_data.get('num_sv', 'N/A')
            if num_sv != 'N/A' and str(num_sv) != '0':
                try:
                    num_sv = int(num_sv)
                    if num_sv > 0:
                        print(f"📡 卫星数量: {num_sv} 颗")
                except ValueError:
                    pass
            
            cnr = location_data.get('cnr', 'N/A')
            if cnr != 'N/A' and str(cnr) != '0.0':
                try:
                    cnr = float(cnr)
                    if cnr > 0:
                        print(f"📶 载噪比: {cnr:.1f} dB-Hz")
                except ValueError:
                    pass
            
            pdop = location_data.get('pdop', 'N/A')
            if pdop != 'N/A' and str(pdop) != '0.0' and str(pdop) != '30.0':
                try:
                    pdop = float(pdop)
                    if pdop > 0 and pdop < 30.0:  # 过滤掉异常的30.0值
                        print(f"📊 PDOP: {pdop:.2f}")
                except ValueError:
                    pass
            
            status = location_data.get('status', 'N/A')
            if status != 'N/A' and status != '':
                print(f"📋 定位状态: {status}")
            
            provider = location_data.get('provider', 'N/A')
            if provider != 'N/A' and provider != '':
                print(f"🔧 数据提供者: {provider}")
            
            print("=" * 60)
            
            # 保存到文件（可选）
            self.save_to_file(location_data, client_ip)
            
        except Exception as e:
            logging.error(f"处理位置数据时出错: {e}")
    
    def format_time(self, time_value):
        """格式化时间显示"""
        try:
            if isinstance(time_value, str) and time_value.isdigit() and len(time_value) > 10:
                timestamp = int(time_value) / 1000.0
                return datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')
            elif isinstance(time_value, str) and time_value.isdigit() and len(time_value) <= 10:
                timestamp = int(time_value)
                return datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')
            else:
                return str(time_value)
        except (ValueError, TypeError):
            return str(time_value)
    
    def save_to_file(self, location_data, client_ip):
        """保存数据到文件"""
        try:
            timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f"location_data_{timestamp}.json"
            
            data_to_save = {
                "timestamp": datetime.datetime.now().isoformat(),
                "client_ip": client_ip,
                "location_data": location_data
            }
            
            with open(filename, 'w', encoding='utf-8') as f:
                json.dump(data_to_save, f, indent=2, ensure_ascii=False)
            
            logging.info(f"数据已保存到文件: {filename}")
            
        except Exception as e:
            logging.error(f"保存文件时出错: {e}")
    
    def log_message(self, format, *args):
        """重写日志方法，避免重复日志"""
        pass

def run_http_server(host='0.0.0.0', port=8080):
    """运行HTTP服务器"""
    try:
        server = HTTPServer((host, port), LocationDataHandler)
        print(f"🌐 HTTP服务器启动成功！")
        print(f"📡 监听地址: {host}:{port}")
        print(f"🌍 公网访问: http://your-public-ip:{port}")
        print("等待客户端连接...")
        server.serve_forever()
    except Exception as e:
        logging.error(f"HTTP服务器启动失败: {e}")

def run_https_server(host='0.0.0.0', port=8443, cert_file='server.crt', key_file='server.key'):
    """运行HTTPS服务器"""
    try:
        server = HTTPServer((host, port), LocationDataHandler)
        
        # 创建SSL上下文
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert_file, key_file)
        
        # 包装socket
        server.socket = context.wrap_socket(server.socket, server_side=True)
        
        print(f"🔒 HTTPS服务器启动成功！")
        print(f"📡 监听地址: {host}:{port}")
        print(f"🌍 公网访问: https://your-public-ip:{port}")
        print("等待客户端连接...")
        server.serve_forever()
    except Exception as e:
        logging.error(f"HTTPS服务器启动失败: {e}")

def run_udp_server(host='0.0.0.0', port=12345):
    """运行UDP服务器"""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind((host, port))
        
        print(f"📡 UDP服务器启动成功！")
        print(f"📡 监听地址: {host}:{port}")
        print(f"🌍 公网访问: UDP your-public-ip:{port}")
        print("等待客户端连接...")
        
        while True:
            try:
                data, addr = sock.recvfrom(4096)
                json_data = data.decode('utf-8')
                location_data = json.loads(json_data)
                
                logging.info(f"收到来自 {addr[0]}:{addr[1]} 的UDP数据")
                
                # 处理位置数据
                handler = LocationDataHandler(None, None, None)
                handler.process_location_data(location_data, addr[0])
                
            except Exception as e:
                logging.error(f"处理UDP数据时出错: {e}")
                
    except Exception as e:
        logging.error(f"UDP服务器启动失败: {e}")

def main():
    """主函数"""
    print("LocationUploader 远程服务器")
    print("=" * 50)
    print("选择服务器类型:")
    print("1. HTTP服务器 (端口8080)")
    print("2. HTTPS服务器 (端口8443)")
    print("3. UDP服务器 (端口12345)")
    print("4. 同时启动HTTP和UDP服务器")
    
    choice = input("请选择 (1-4): ").strip()
    
    if choice == "1":
        run_http_server()
    elif choice == "2":
        run_https_server()
    elif choice == "3":
        run_udp_server()
    elif choice == "4":
        # 同时启动HTTP和UDP服务器
        http_thread = threading.Thread(target=run_http_server, args=('0.0.0.0', 8080))
        udp_thread = threading.Thread(target=run_udp_server, args=('0.0.0.0', 12345))
        
        http_thread.start()
        udp_thread.start()
        
        print("🚀 同时启动HTTP和UDP服务器")
        print("📡 HTTP: 0.0.0.0:8080")
        print("📡 UDP: 0.0.0.0:12345")
        
        http_thread.join()
        udp_thread.join()
    else:
        print("无效选择，启动HTTP服务器")
        run_http_server()

if __name__ == "__main__":
    main()
