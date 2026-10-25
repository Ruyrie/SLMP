#!/usr/bin/env bash
# 更新云端服务端与 Web 双端；保留已有数据库、账号、第三方配置和旧版资源。
set -euo pipefail
[[ ${EUID} -eq 0 ]] || { echo '请用 sudo bash update-full.sh 执行'; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"
PUBLIC_URL="${PUBLIC_URL:-http://8.208.12.217}"
PUBLIC_URL="${PUBLIC_URL%/}"
TARGET=/opt/zhimu/zhimu-server.jar
BACKUP="/opt/zhimu/backups/linked-repair-$(date +%Y%m%d-%H%M%S)"

for item in zhimu-server.jar web-user/index.html web-admin/index.html nginx-zhimu-web.conf; do
    [[ -f "${HERE}/${item}" ]] || { echo "更新包缺少 ${item}"; exit 1; }
done
[[ -f /etc/zhimu/env ]] || { echo '缺少现有 /etc/zhimu/env 配置'; exit 1; }
install -d -m 700 "${BACKUP}"
cp -a /etc/zhimu/env "${BACKUP}/env"
[[ ! -f "${TARGET}" ]] || cp -a "${TARGET}" "${BACKUP}/zhimu-server.jar"
[[ ! -f /etc/nginx/conf.d/zhimu-web.conf ]] || cp -a /etc/nginx/conf.d/zhimu-web.conf "${BACKUP}/nginx.conf"
tar -C /var/www/zhimu -czf "${BACKUP}/web.tar.gz" .

echo '备份数据库并对齐公网配置'
python3 - "${BACKUP}/database.sql" "${PUBLIC_URL}" <<'PY'
import os, pathlib, subprocess, sys
path = pathlib.Path('/etc/zhimu/env')
lines = path.read_text().splitlines()
values = {}
for line in lines:
    if line.strip() and not line.lstrip().startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        values[key] = value.strip().strip('"').strip("'")
env = dict(os.environ, MYSQL_PWD=values.get('DB_PASSWORD', ''))
with open(sys.argv[1], 'w') as backup:
    subprocess.run(['mysqldump', '-u' + values.get('DB_USER', 'zhimu'), '--no-tablespaces',
                    '--single-transaction', '--set-gtid-purged=OFF', 'zhimu'],
                   env=env, stdout=backup, check=True)
public = sys.argv[2]
origins = [value for value in values.get('ALLOWED_ORIGINS', '').split(',') if value]
if public not in origins:
    origins.append(public)
updates = {'ALLOWED_ORIGINS': ','.join(origins), 'ZMT_FIXED_DATE': '',
           'COOKIE_SECURE': 'true' if public.startswith('https://') else 'false',
           'MINIO_PUBLIC_ENDPOINT': public, 'HLS_BASE_URL': public + '/zmt-course/hls'}
new_lines = [line for line in lines if line.split('=', 1)[0] not in updates]
new_lines.extend(key + '=' + value for key, value in updates.items())
path.write_text('\n'.join(new_lines) + '\n')
PY

install -m 644 "${HERE}/nginx-zhimu-web.conf" /etc/nginx/conf.d/zhimu-web.conf
if ! nginx -t; then
    [[ ! -f "${BACKUP}/nginx.conf" ]] || cp -a "${BACKUP}/nginx.conf" /etc/nginx/conf.d/zhimu-web.conf
    cp -a "${BACKUP}/env" /etc/zhimu/env
    exit 1
fi

echo '更新服务端（新增演示数据由 Flyway 执行）'
install -o zhimu -g zhimu -m 644 "${HERE}/zhimu-server.jar" "${TARGET}.new"
mv "${TARGET}.new" "${TARGET}"
systemctl restart zhimu
ready=0
for attempt in $(seq 1 60); do
    if curl -fsS -m 3 http://127.0.0.1:8080/api/v1/ping >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done
if [[ ${ready} -ne 1 ]]; then
    echo "新服务启动失败，恢复旧 Jar；诊断与数据库备份位于 ${BACKUP}"
    [[ ! -f "${BACKUP}/zhimu-server.jar" ]] || cp -a "${BACKUP}/zhimu-server.jar" "${TARGET}"
    cp -a "${BACKUP}/env" /etc/zhimu/env
    [[ ! -f "${BACKUP}/nginx.conf" ]] || cp -a "${BACKUP}/nginx.conf" /etc/nginx/conf.d/zhimu-web.conf
    systemctl restart zhimu
    exit 1
fi

echo '更新 Web 用户端和管理端'
install -d /var/www/zhimu/admin
# 保留旧哈希资源，避免仍打开旧页面的浏览器请求资源时出现 404。
cp -a "${HERE}/web-user/." /var/www/zhimu/
cp -a "${HERE}/web-admin/." /var/www/zhimu/admin/
chown -R www-data:www-data /var/www/zhimu
systemctl reload nginx
curl -fsS -m 8 http://127.0.0.1/api/v1/ping
echo
echo "更新完成，备份：${BACKUP}"
echo "用户端：${PUBLIC_URL}/"
echo "管理端：${PUBLIC_URL}/admin/"
