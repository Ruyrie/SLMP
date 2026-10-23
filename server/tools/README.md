# 本机开发中间件（Windows 免安装）

本目录存放服务端本机开发用的 Redis、MinIO、FFmpeg 免安装包及启停脚本。
**二进制文件和数据/日志不进 git**（见 `server/.gitignore`：`tools/*` 仅放行 `README.md` 和 `*.ps1`），新环境需按下表手动下载。

## 版本与来源

| 工具 | 版本 | 来源 |
| --- | --- | --- |
| Redis | 7.4.11（msys2 构建） | https://github.com/redis-windows/redis-windows/releases/download/7.4.11/Redis-7.4.11-Windows-x64-msys2.zip |
| MinIO Server | RELEASE.2025-09-07T16-13-09Z | https://github.com/minio/minio/releases/download/RELEASE.2025-09-07T16-13-09Z/minio.windows-amd64.RELEASE.2025-09-07T16-13-09Z.exe |
| MinIO Client (mc) | RELEASE.2025-08-13T08-35-41Z | https://github.com/minio/mc/releases/download/RELEASE.2025-08-13T08-35-41Z/mc.windows-amd64.RELEASE.2025-08-13T08-35-41Z.exe |
| FFmpeg / FFprobe | 7.1.1（gyan.dev essentials） | https://github.com/GyanD/codexffmpeg/releases/download/7.1.1/ffmpeg-7.1.1-essentials_build.zip |

说明：

- MinIO 官方已归档开源版，`dl.min.io` 不再提供社区版下载（返回 410），上表为官方 GitHub Releases 中最后一个带 Windows 二进制的版本（已用同目录 `.sha256sum` 校验）。仅用于本机开发。
- gyan.dev 的 `ffmpeg-release-essentials.zip` 现已是 9.x，7.x 取自 gyan.dev 官方 GitHub 镜像仓库 GyanD/codexffmpeg。

## 目录结构

```
tools/
├─ README.md
├─ start-dev.ps1      # 一键启动 Redis + MinIO 并检查端口
├─ start-redis.ps1    # 后台启动 Redis
├─ start-minio.ps1    # 后台启动 MinIO
├─ stop-dev.ps1       # 停止 Redis、MinIO
├─ redis/             # redis-server.exe、redis-cli.exe、redis.conf 等
│  ├─ data/           # 数据目录（AOF/RDB，运行后生成）
│  └─ redis.log       # 日志（运行后生成）
├─ minio/             # minio.exe、mc.exe
│  ├─ data/           # 数据目录（运行后生成）
│  └─ minio.log       # 日志（运行后生成）
└─ ffmpeg/
   └─ bin/            # ffmpeg.exe、ffprobe.exe
```

## 启动 / 停止

在 `server/tools` 目录下执行（若提示脚本被禁止执行，加 `-ExecutionPolicy Bypass`）：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1   # 启动 Redis + MinIO
powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1    # 停止
```

也可单独运行 `start-redis.ps1` / `start-minio.ps1`。端口已被占用时脚本会提示并跳过启动。

## 端口与账号

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| Redis | 127.0.0.1:6379 | 无密码，开启 appendonly |
| MinIO API | http://127.0.0.1:9000 | 健康检查：`/minio/health/live` |
| MinIO 控制台 | http://127.0.0.1:9001 | 账号 `minioadmin`，密码 `minioadmin123` |

`mc` 示例：

```powershell
.\minio\mc.exe alias set local http://127.0.0.1:9000 minioadmin minioadmin123
.\minio\mc.exe ls local
```

## FFmpeg 路径

应用配置 `FFMPEG_PATH` / `FFPROBE_PATH` 时指向：

- `server/tools/ffmpeg/bin/ffmpeg.exe`
- `server/tools/ffmpeg/bin/ffprobe.exe`
