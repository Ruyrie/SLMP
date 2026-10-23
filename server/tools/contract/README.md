# App 接口覆盖率检查

用途：对比 App 端 Mock 路由（`module_*/src/debug/assets/mock/**/routes.json`）与服务端 Controller 源码，列出缺失的 method+path。
App 契约是权威：本脚本报出的缺失项就是服务端还没实现的接口。

## 用法

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File server/tools/contract/check-app-paths.ps1
```

只看某个模块：

```powershell
powershell -ExecutionPolicy Bypass -File server/tools/contract/check-app-paths.ps1 -Module module_today
```

输出：

- 汇总：App 唯一接口数、服务端已映射数、缺失数、覆盖率。
- 缺失清单：按模块分组，`METHOD /path`。
- 可选 `-FailOnMissing`：有缺失时退出码 1，用于 CI。

## 说明

- 路径参数 `{id}`、`{id:\d+}`、`{batch_id}` 统一归一化为 `{}` 再比较。
- 服务端 Controller 的类级 `@RequestMapping` 会自动拼接；方法级 `@GetMapping` 等无路径时按空串处理。
- 不检查字段形状；字段级验收靠各模块的 MockMvc 测试与联调。
