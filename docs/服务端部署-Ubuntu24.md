# 服务端部署到 Ubuntu 24.04（无域名，仅公网 IP）

> 目标服务器：`8.208.12.217`，Ubuntu 24.04。
> 结论先说：**服务端代码已具备部署条件，但目前还没部署；App 也仍在 Mock。**
> App 禁止明文 HTTP，所以「只用 `http://8.208.12.217:8080`」不可行，必须解决 HTTPS。

## 一、HTTPS 的三种方案

| 方案 | 做法 | 适用 |
|---|---|---|
| **A. 免费泛解析域名 + Let's Encrypt（推荐）** | 用 `8.208.12.217.nip.io` 或 `sslip.io` 作为域名，Certbot 申请证书 | 测试/演示，不需要买域名 |
| B. 免费 DDNS 域名 | DuckDNS 注册 `xxx.duckdns.org` 指向该 IP，再 Certbot | 测试/长期演示，比 nip.io 稳定 |
| C. 买域名 | 任意域名解析 A 记录到 IP，Certbot | 正式上线 |

> 公共 CA 不给纯 IP 签证书，所以「只有 IP、又要 HTTPS」必须借一个域名。  
> 不推荐改 App 允许明文 HTTP：那只对 debug 包有效，release 包仍然连不上。

## 二、服务器准备

```bash
# 1. 安全组/防火墙只开 22、80、443；不要对公网开 3306、6379、9000
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# 2. 基础软件（Ubuntu 24.04）
sudo apt update
sudo apt install -y openjdk-21-jdk nginx redis-server ffmpeg unzip curl
java -version   # 应为 21

# 3. MySQL（Ubuntu 自带 8.0；要 8.4 用 MySQL APT 源）
sudo apt install -y mysql-server
sudo mysql_secure_installation
```

创建库和用户（不要用 root 跑应用）：

```sql
CREATE DATABASE zhimu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'zhimu'@'127.0.0.1' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON zhimu.* TO 'zhimu'@'127.0.0.1';
FLUSH PRIVILEGES;
```

MinIO（课程视频/图片需要；也可先用本地目录替代）：

```bash
wget https://dl.min.io/server/minio/release/linux-amd64/minio -O /usr/local/bin/minio
chmod +x /usr/local/bin/minio
sudo useradd -r -s /sbin/nologin minio-user
sudo mkdir -p /data/minio && sudo chown minio-user:minio-user /data/minio
# systemd 启动 minio server /data/minio --console-address :9001
```

## 三、部署应用

```bash
sudo useradd -r -m -s /bin/bash zhimu
sudo mkdir -p /opt/zhimu /etc/zhimu
sudo chown zhimu:zhimu /opt/zhimu

# 本机构建后上传，或在服务器上构建
mvn -q -B -pl zhimu-app -am -DskipTests package
scp server/zhimu-app/target/zhimu-server.jar root@8.208.12.217:/opt/zhimu/
```

`/etc/zhimu/env`（权限 600，不要进 git）：

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

DB_URL=jdbc:mysql://127.0.0.1:3306/zhimu?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true
DB_USER=zhimu
DB_PASSWORD=强密码

REDIS_HOST=127.0.0.1
REDIS_PASSWORD=
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_PUBLIC_ENDPOINT=https://8.208.12.217.nip.io/files
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
HLS_BASE_URL=https://8.208.12.217.nip.io/hls

JWT_USER_SECRET=至少32字节随机串
JWT_ADMIN_SECRET=至少32字节随机串

DASHSCOPE_API_KEY=
DEEPSEEK_API_KEY=
SMS_MODE=aliyun
SMS_ACCESS_KEY=
SMS_SECRET=
SMS_SIGN_NAME=恒创联众
JPUSH_APP_KEY=
JPUSH_MASTER_SECRET=
AMAP_WEB_KEY=
FFMPEG_PATH=/usr/bin/ffmpeg
FFPROBE_PATH=/usr/bin/ffprobe
```

> `prod` 不导入 `db/sample`，数据库只有 V2 的平台基础数据（品类/字典/药品/模板），没有王建国等演示账号。
> 想先用演示数据联调，把 `SPRING_PROFILES_ACTIVE` 改成 `dev`（会导入 V9_01–V9_12，短信为 mock 123456，Swagger 开启）；正式使用改回 `prod`。

`/etc/systemd/system/zhimu.service`：

```ini
[Unit]
Description=Zhimu Server
After=network.target mysql.service redis-server.service

[Service]
User=zhimu
WorkingDirectory=/opt/zhimu
EnvironmentFile=/etc/zhimu/env
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/zhimu/zhimu-server.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now zhimu
sudo journalctl -u zhimu -f   # 看 Flyway 启动日志
curl http://127.0.0.1:8080/api/v1/ping
```

首次启动 Flyway 会自动建表；`prod` 只执行 `db/migration`。

## 四、Nginx + 证书（方案 A）

```bash
sudo apt install -y certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/zhimu`：

```nginx
server {
    listen 80;
    server_name 8.208.12.217.nip.io;

    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name 8.208.12.217.nip.io;

    ssl_certificate     /etc/letsencrypt/live/8.208.12.217.nip.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/8.208.12.217.nip.io/privkey.pem;

    client_max_body_size 50m;

    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
    }

    # AI SSE：关闭缓冲
    location /api/v1/ai/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 180s;
    }

    # MinIO HLS / 文件（按实际 bucket 调整）
    location /hls/ { proxy_pass http://127.0.0.1:9000/zmt-course/hls/; }
    location /files/ { proxy_pass http://127.0.0.1:9000/; }
}
```

```bash
sudo mkdir -p /var/www/certbot
sudo ln -s /etc/nginx/sites-available/zhimu /etc/nginx/sites-enabled/zhimu
sudo nginx -t && sudo systemctl reload nginx

sudo certbot --nginx -d 8.208.12.217.nip.io
sudo certbot renew --dry-run
```

验证：

```bash
curl https://8.208.12.217.nip.io/api/v1/ping
```

## 五、App 配置

`local.properties`：

```properties
zmt.baseUrl=https://8.208.12.217.nip.io/
zmt.useMock=false
zmt.mockToday=
```

同时清空测试直连：

```properties
ai.dashscopeKey=
ai.deepseekKey=
aliyun.accessKeyId=
aliyun.accessKeySecret=
```

重新打 debug 包安装。示例账号取决于服务端 profile：

- `dev`：`13800006620 / Zmt12345`、员工 `13700005678`、短信 123456
- `prod`：全新库，没有演示账号，需要先注册

## 六、上线前必须改

1. MySQL/Redis/MinIO 不对公网开放；安全组只开 22/80/443。
2. 改掉所有默认密码、JWT 随机串、MinIO 密钥。
3. `SPRING_PROFILES_ACTIVE=prod`，关闭 Swagger，短信改真实。
4. 配 MinIO 的 `MINIO_PUBLIC_ENDPOINT`/`HLS_BASE_URL` 为 HTTPS 域名；课程视频先确认 bucket/路径。
5. 配极光 debug/release AppKey 与 Master Secret；否则推送只写日志。
6. 配 AI Key；否则 AI 诊断/问答不可用。
7. 定期备份 MySQL 与 MinIO；`prod` 不再导入 V9 示例数据。

## 七、当前状态

- 代码：可部署，`zhimu-server.jar` 已能构建。
- 服务器：尚未部署（没有 SSH 权限/密钥，也未在 `8.208.12.217` 上执行过命令）。
- App：仍在使用 Mock；改 `zmt.useMock=false` 并指向 HTTPS 域名后才走真实服务端。
- 数据：部署后是真实数据库；`dev` 首启是演示种子数据，`prod` 是空业务库 + 平台基础数据。
