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

日志写在启动目录下的 `logs/`：`zhimu-server.log`（按天滚动，保留 30 天）、`zhimu-server-error.log`（只有错误）、`oplog.log`（操作留痕）。

## 数据库迁移

- 表结构脚本放在 `zhimu-app/src/main/resources/db/migration`，示例数据放在 `db/sample`（只在 dev、test 环境执行）。
- 版本号按 [docs/服务端开发约定.md](docs/服务端开发约定.md) 第五节分配（V1_00 公共表、V1_01 模块01 ……，示例数据 V9_01 起）；文件名如 `V1_02__init_farm.sql`。
- 已执行的脚本不再修改，结构变更另起新版本（如 `V1_02_1__farm_add_version.sql`）。

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
