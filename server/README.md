# 智牧通服务端

Spring Boot 4.1 + MyBatis-Plus + MySQL + Redis。开发约定（分包、接口 JSON、框架用法、数据库规范）见 [docs/服务端开发约定.md](docs/服务端开发约定.md)，写代码前先读。

## 环境要求

| 项目 | 版本 / 说明 |
|---|---|
| JDK | 21 |
| Maven | 3.9 及以上 |
| MySQL | 9.5，本机服务；开发库 `zhimu`，集成测试库 `zhimu_test`（测试时自动创建） |
| Redis、MinIO、FFmpeg | 免安装版放在 `tools/`，下载地址见 [tools/README.md](tools/README.md) |

首次使用先建开发库（表由 Flyway 在启动时自动创建）：

```sql
CREATE DATABASE zhimu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 本机运行

1. 启动中间件（Redis + MinIO），在 `server/tools` 目录执行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
   ```

   停止用 `stop-dev.ps1`。

2. 配置环境变量：把 `.env.example` 复制为 `.env.local`，填写数据库密码、JWT 密钥（至少 32 字节随机串）和外部服务 Key。
   `.env.local` 不进 git，应用启动时自动读取（工作目录为 `server` 或 `server/zhimu-app` 都可以）；服务器上改用真实环境变量。
   默认 `SPRING_PROFILES_ACTIVE=dev`：短信不真实发送（验证码固定 123456），并导入 `db/sample` 下的统一示例数据。

3. 启动应用，任选一种：
   - 在 `server` 目录执行 `mvn -pl zhimu-app -am spring-boot:run`
   - 在 IDEA 中直接运行 `zhimu-app` 模块的 `com.zhimu.server.ZhimuServerApplication`
   - 打包后运行：`mvn -pl zhimu-app -am package -DskipTests`，再 `java -jar zhimu-app/target/zhimu-server.jar`

4. 接口文档：<http://127.0.0.1:8080/swagger-ui.html>

批次二维码通过 `USER_WEB_BASE_URL` 指向用户 Web 的 `/s/b/{token}`：本地使用
`http://127.0.0.1:5173`，当前公网部署使用 `http://8.208.12.217`。真机本地联调时应配置手机可访问的电脑局域网地址；
这与文件存储的 `MINIO_PUBLIC_ENDPOINT`、视频的 `HLS_BASE_URL` 是不同配置。
扫码解析继续识别旧版 `https://web.zhimutong.example.com/s/b/{token}` 标签，令牌与访问权限规则保持一致。
旧示例域名无法直接由普通浏览器访问当前服务器，已有旧标签请使用 App 扫描；新生成或重新保存的标签使用实际站点地址。

日志写在启动目录下的 `logs/`：`zhimu-server.log`（按天滚动，保留 30 天）、`zhimu-server-error.log`（只有错误）、`oplog.log`（操作留痕）。

## 数据库迁移

- 表结构脚本放在 `zhimu-app/src/main/resources/db/migration`，示例数据放在 `db/sample`（只在 dev、test 环境执行）。
- 版本号按 [docs/服务端开发约定.md](docs/服务端开发约定.md) 第五节分配（V1_00 公共表、V1_01 模块01 ……，示例数据 V9_01 起）；文件名如 `V1_02__init_farm.sql`。
- 已执行的脚本不再修改，结构变更另起新版本（如 `V1_02_1__farm_add_version.sql`）。

北湾牛场的演示资质图由 `V9_29__sample_farm_qualification_images.sql` 补齐空图片快照和文件关联，
`SampleQualificationImages` 在 dev/test 启动时将随包 PNG 写入私有 MinIO，并设置真实大小、摘要和已绑定状态。
图片明确标注“模拟材料”，包括旧模糊图、重拍清晰图及两张沿用材料；已有实际图片的审核记录不会被替换。
缺失对象会在下次启动时补齐，存储不可用会记录警告；正式环境不会运行此初始化器。

正常运行默认关闭日期平移（`ZMT_DEMO_SHIFT=false`），样例和用户记录的历史日期都保留。
饲喂、健康任务保留每日生成；生产任务在启动时及每五分钟检查一次，自动补齐缺少的任务和待办。
补偿复用原来的槽位去重、状态保护和巡检补录窗口，不复制实际投喂、称重、用药或财务记录，
也不把已经完成、忽略或取消的任务重置为待处理。单个养殖场或步骤失败不会阻断其他步骤，下一轮自动重试。
所有补偿入口使用同一把任务锁，失败会输出养殖场、业务日期和步骤，便于定位持续故障。

## 运行测试

测试连本机 MySQL（`zhimu_test` 库）与本机 Redis（database 15），运行前先启动中间件并配置好 `.env.local`。

```powershell
# 全部单元测试与框架集成测试
mvn -q -B verify

# 只测某个模块（连同它依赖的模块一起编译）
mvn -q -B -pl zhimu-framework -am test

# 应用启动冒烟测试（清空 zhimu_test 后执行全部迁移脚本）
mvn -B -pl zhimu-app -am test -Dtest=ZhimuServerApplicationIT -Dsurefire.failIfNoSpecifiedTests=false
```
