# 智牧通云端部署与更新（Ubuntu 24.04）

当前演示服务器为 `8.208.12.217`，已运行 Java 21 服务端、MySQL、Redis、MinIO 和 Nginx。App 已切换真实云端；Web 用户端和管理端分别部署在根路径与 `/admin/`。

## 当前目录与访问入口

| 内容 | 服务器路径 / 地址 |
|---|---|
| 服务端 | `/opt/zhimu/zhimu-server.jar`，systemd 服务 `zhimu`，本机端口 8080 |
| 环境配置 | `/etc/zhimu/env` |
| 用户端 | `/var/www/zhimu/` → `http://8.208.12.217/` |
| 管理端 | `/var/www/zhimu/admin/` → `http://8.208.12.217/admin/` |
| 用户接口 | `/api/v1/` → `127.0.0.1:8080` |
| 管理接口 | `/admin/v1/` → `127.0.0.1:8080` |
| 头像与附件 | `/zmt-private/` → `127.0.0.1:9000/zmt-private/` |
| 课程与公开媒体 | `/zmt-course/` → `127.0.0.1:9000/zmt-course/` |

Nginx 配置以 `web/deploy/nginx-zhimu-web.conf` 为准。管理端接口与静态页面分别匹配，不把接口请求或缺失的 JS 文件回退成 HTML。

## 文件公网地址

当前公网 HTTP 演示环境使用以下配置：

```properties
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_PUBLIC_ENDPOINT=http://8.208.12.217
HLS_BASE_URL=http://8.208.12.217/zmt-course/hls
ALLOWED_ORIGINS=http://8.208.12.217
COOKIE_SECURE=false
ZMT_FIXED_DATE=
```

`MINIO_PUBLIC_ENDPOINT` 是公网根地址，不添加 `/files`。服务端签名地址自身包含 `/zmt-private/` 或 `/zmt-course/`；Nginx 保留桶名、对象路径和原始 Host 转发，头像签名才能验证通过。

后续切换 HTTPS 时，将公网地址、Origin 和 `COOKIE_SECURE` 一起按新协议更新即可。

## 构建

在仓库根目录执行 Android 构建：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

服务端与网页分别构建：

```powershell
cd server
mvn -B -pl zhimu-app -am verify
cd ..\web
npm run typecheck
npm test
npm run build
```

产物为 `server/zhimu-app/target/zhimu-server.jar`、`web/apps/user/dist`、`web/apps/admin/dist` 和 `app/build/outputs/apk/debug/app-debug.apk`。Jar、APK、zip 和本机密钥不纳入源码提交。

## 统一更新

更新包包含以下文件，不包含环境密码或第三方密钥：

```text
zhimu-server.jar
web-user/
web-admin/
nginx-zhimu-web.conf
update-full.sh
SHA256.json
```

在服务器解压到独立目录，然后执行：

```bash
sudo bash update-full.sh
```

脚本源码位于 `server/deploy/update-full.sh`。脚本先备份数据库、旧 Jar、环境配置和 Web 页面，再更新服务端及两端网页；保留旧哈希静态资源，避免已打开的旧页面在更新期间请求分包失败。新服务启动失败时会恢复旧 Jar 与配置。备份位置会打印在更新结果中。

默认公网地址为当前 IP；更换地址时使用 `PUBLIC_URL=https://新地址 sudo -E bash update-full.sh`。

## 演示数据与日期

当前小组演示环境使用 `SPRING_PROFILES_ACTIVE=dev`，Flyway 执行 `db/migration` 和 `db/sample`。已执行迁移保持原样，补数使用新版本脚本。

- V9_15–V9_17：健康记录及其库存/成本关联、近期示例行情。
- V9_20–V9_24：演示日期锚点、西岭鸡场业务链与成员消息、栏舍负责人、跨场数据与失效待办修正。
- 后续演示日期锚点仅管理显式登记的样例行；服务重启时对齐真实今天，不移动用户自行新增的记录。
- 饲喂、免疫、消毒、巡检与今日待办按业务来源持续生成；已登记、手工忽略和取消的记录保留操作结果。

`ZMT_FIXED_DATE` 在云端保持为空。需要固定到 Mock 日期的独立联调环境可以临时设置该值，不能把该环境的测试库覆盖到云端业务库。

## 更新后确认

```bash
systemctl is-active zhimu nginx
curl -fsS http://127.0.0.1:8080/api/v1/ping
curl -fsS http://8.208.12.217/api/v1/ping
curl -fsS -H 'X-Client: ADMIN_WEB' http://8.208.12.217/admin/v1/auth/captcha
```

浏览器确认用户端登录、切换农场、今日任务与统计；管理端确认登录、首次改密、重新登录、菜单、刷新和退出。头像确认顶栏、个人中心和成员列表的上传、刷新与删除结果一致。

## 云端内存配置

当前 1.6GB 演示机使用以下配置，避免 JVM 堆超过物理内存后被内核 OOM：

- `/etc/systemd/system/zhimu.service`：`-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError`。
- 2GB `/swapfile`，`vm.swappiness=10`。
- MySQL 关闭 `performance_schema`，`innodb_buffer_pool_size=128M`，`max_connections=100`。

调整后 `free -m` 应保留至少 300MB available；若持续低于 100MB，优先检查 JVM 堆和 MySQL 连接数，再考虑升配。

