# 局域网共享浏览器 (LanShareExplorer)

一款 Android 应用，用于扫描局域网中的 SMB/Samba 共享设备，浏览共享文件并执行文件操作。

## 功能特性

### 🔍 局域网扫描
- 自动扫描同一局域网内开启 SMB 共享的设备
- 并发扫描，速度快（50个线程并发）
- 实时显示扫描进度
- 支持下拉刷新重新扫描

### 📁 SMB 共享浏览
- 自动发现设备上的共享文件夹
- 支持匿名(Guest)和用户名/密码认证
- 浏览共享文件夹内的文件和子目录
- 支持路径导航，可返回上级目录

### ⚡ 文件操作
- **下载** - 将远程文件下载到本地
- **上传** - 将本地文件上传到共享目录
- **删除** - 删除文件或文件夹
- **重命名** - 重命名文件或文件夹
- **新建文件夹** - 在共享目录中创建新文件夹
- **查看详情** - 查看文件名、大小、修改时间等信息

### 🔧 设置选项
- 配置默认 SMB 用户名和密码
- 自动匿名连接开关
- 扫描超时设置
- 自定义下载路径
- 显示/隐藏隐藏文件
- 记住最近连接的服务器

## 技术架构

```
├── model/          # 数据模型 (LanDevice, SmbShare, SmbFileItem)
├── scanner/        # 局域网扫描器 (LanScanner)
├── smb/            # SMB客户端管理器 (SmbClientManager)
├── service/        # 文件传输后台服务 (FileTransferService)
├── ui/             # 界面 (MainActivity, ShareBrowserActivity, SettingsActivity)
│                   # 适配器 (DeviceAdapter, FileAdapter)
└── util/           # 工具类 (Utils, SettingsManager)
```

### 核心依赖
| 库 | 用途 |
|---|---|
| **SMBJ** | SMB/CIFS 协议通信 |
| **Kotlin Coroutines** | 异步操作和并发 |
| **Material Components** | Material Design 3 UI |
| **AndroidX Preference** | 设置界面 |

## 编译与运行

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK: minSdk 24, targetSdk 34
- Gradle 8.0

### 编译步骤
```bash
# 1. 克隆项目
git clone <repo-url>
cd LanShareExplorer

# 2. 使用 Gradle 编译
./gradlew assembleDebug

# 3. 安装到设备
./gradlew installDebug

# 或使用 Android Studio 打开项目直接运行
```

### 生成的 APK
编译后的 Debug APK 位于: `app/build/outputs/apk/debug/app-debug.apk`

## 使用方法

1. **确保手机和目标设备在同一局域网**（同一WiFi）
2. 打开APP，自动扫描局域网
3. 点击发现的设备进行连接
4. 如果需要认证，输入用户名和密码
5. 浏览共享文件夹
6. 点击文件可下载，长按可查看更多操作

## 权限说明

| 权限 | 用途 |
|---|---|
| `INTERNET` | 网络通信、SMB连接 |
| `ACCESS_NETWORK_STATE` | 检测网络状态 |
| `ACCESS_WIFI_STATE` | 获取WiFi信息用于局域网扫描 |
| `READ/WRITE_EXTERNAL_STORAGE` | 文件下载到本地 |
| `FOREGROUND_SERVICE` | 后台文件传输服务 |

## 目标设备SMB共享配置

### Windows
1. 右键文件夹 → 属性 → 共享 → 高级共享
2. 勾选"共享此文件夹"
3. 点击"权限"设置访问权限

### macOS
1. 系统偏好设置 → 共享 → 文件共享
2. 添加共享文件夹
3. 在"选项"中启用SMB

### Linux (Samba)
```bash
# 安装 samba
sudo apt install samba

# 编辑配置 /etc/samba/smb.conf
[shared]
   path = /home/user/shared
   browseable = yes
   read only = no
   guest ok = yes

# 重启服务
sudo systemctl restart smbd
```

## 注意事项

- 需要在同一局域网内使用
- 部分路由器可能隔离客户端，需关闭AP隔离
- Android 13+ 需要手动授予通知权限
- 大文件传输建议在WiFi环境下进行
