# 图标脚本

把 Material Symbols Rounded（字重 400）转换为 VectorDrawable，每个图标生成 `ic_<名称>.xml`（FILL 0）和 `ic_<名称>_filled.xml`（FILL 1），24 dp、黑色，使用处用 `app:tint="?attr/colorOnSurfaceVariant"` 这类主题属性着色。

```bash
# 按 icons.txt 生成到 lib_base/src/main/res/drawable（已存在的跳过，--force 覆盖）
python tools/icons/fetch_icons.py
# 业务组件生成到自己的目录，文件名为 farm_ic_<名称>.xml
python tools/icons/fetch_icons.py --list module_farm/icons.txt --out module_farm/src/main/res/drawable --prefix farm_
# 只生成命令行列出的图标
python tools/icons/fetch_icons.py --out module_farm/src/main/res/drawable --prefix farm_ egg pets
```

- 清单每行一个图标名，`#` 后为注释；Material Symbols 改过名的写成 `文件名=源图标名`（如 `smartphone=mobile`）。
- 源地址固定为 `@material-symbols/svg-400@0.47.6`，可用 `--version` 改。lib_base 已有的图标不要在业务组件重复生成。
