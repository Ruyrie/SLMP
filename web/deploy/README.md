# Web 端公网部署（用户端 + 管理端）

> 目标：在服务器上打开 `http://<公网IP>/` 进入用户端登录，打开 `http://<公网IP>/admin/` 进入**管理后台**登录；输入账号密码后即可使用。
> 前提：服务端已在同一台机器 8080 运行，MySQL / Redis / MinIO 已启动（`server/tools/start-dev.ps1` 仅 Windows 开发用，服务器用 systemd/docker）。

## 一、本地构建

```powershell
cd D:\Android_Program\SLMP\web
npm ci
npm run build
# 产物：
#   apps/user/dist     -> 用户端
#   apps/admin/dist    -> 管理端（资源前缀 /admin/）
```

## 二、上传到服务器

```bash
sudo mkdir -p /var/www/zhimu/admin
sudo rsync -av --delete apps/user/dist/  /var/www/zhimu/
sudo rsync -av --delete apps/admin/dist/ /var/www/zhimu/admin/
sudo chown -R www-data:www-data /var/www/zhimu
```

目录结构应为：

```
/var/www/zhimu/index.html
/var/www/zhimu/assets/...
/var/www/zhimu/admin/index.html
/var/www/zhimu/admin/assets/...
```

## 三、Nginx

```bash
sudo cp web/deploy/nginx-zhimu-web.conf /etc/nginx/conf.d/zhimu-web.conf
sudo nginx -t && sudo systemctl reload nginx
```

- 站点监听 80；如用域名/HTTPS，把 `listen 80` 改为 443 并加证书。
- 后端 8080 不需要对公网开放，只给 Nginx 本机代理。

## 四、服务端环境变量（公网 HTTP 必看）

管理端会话与用户端刷新令牌都是 `HttpOnly + Secure` 的 Cookie。**用 HTTP 访问公网 IP 时浏览器不会回传 Secure Cookie**，必须让服务端关闭 Secure：

```bash
# /etc/systemd/system/zhimu-server.service 的 Environment= 或启动脚本中
ALLOWED_ORIGINS=http://<公网IP>
COOKIE_SECURE=false
```

- 若使用 HTTPS：`ALLOWED_ORIGINS=https://<域名>`、`COOKIE_SECURE=true`（默认）。
- `ALLOWED_ORIGINS` 是服务端对凭 Cookie 的请求做 Origin 校验的白名单，必须与浏览器地址栏的协议 + IP/域名一致（含端口，默认 80 可省略）。
- 多个来源用英文逗号分隔。
- 修改后重启服务端。

## 五、访问

| 地址 | 内容 | 账号 |
|---|---|---|
| `http://<公网IP>/` | Web 用户端（登录 → 工作台） | `13800006620 / Zmt12345`（所有者）；`13700005678`（员工，验证码 123456） |
| `http://<公网IP>/admin/` | Web 管理端（后台） | `zhoumin / Zmt12345`（首登强制改密） |

## 六、防火墙与端口

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# 不要开放 8080、3306、6379、9000 到公网
```

## 七、常见问题

| 现象 | 原因与处理 |
|---|---|
| 管理端登录成功但下一个请求 401 | HTTP 下 Cookie 带 Secure 被浏览器丢弃：`COOKIE_SECURE=false`，清 Cookie 重试 |
| 登录/提交返回 403，提示来源不合法 | `ALLOWED_ORIGINS` 与当前地址不一致（协议、IP、端口） |
| 刷新 `/admin/system/users` 404 | Nginx 缺少 `/admin/` 的 `try_files ... /admin/index.html` |
| 用户端刷新子路由 404 | Nginx 缺少 `try_files ... /index.html` |
| AI 回答不流式、一次性返回 | `/api/v1/ai/` 未关闭 `proxy_buffering`，或超时不足 180s |
| 上传视频失败 413 | `client_max_body_size` 太小（默认 64m，可按需调大） |
| 课程视频 404 | MinIO 未启动、HLS 前缀不一致（`HLS_BASE_URL` / `/hls/` 代理） |
| 页面白屏 | 检查 `/admin/` 资源是否 404（构建时 `base=/admin/` 是否生效） |

## 八、部署后自检

```bash
curl -I http://<公网IP>/
curl -I http://<公网IP>/admin/
curl http://<公网IP>/api/v1/ping
curl -i -X GET http://<公网IP>/admin/v1/auth/captcha
```

前两个返回 200 且是 HTML；`/api/v1/ping` 返回 `{"code":0,...}`；captcha 返回 PNG data URL。
