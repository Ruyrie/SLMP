#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
下载 Material Symbols Rounded（字重 400、Grade 0）的 SVG，转换为 Android VectorDrawable。

每个图标生成两份：
    <prefix>ic_<名称>.xml          线性（FILL 0）
    <prefix>ic_<名称>_filled.xml   填充（FILL 1）

尺寸 24 dp，viewport 960 x 960；SVG 的 viewBox 为 "0 -960 960 960"（原点在 y = -960），
用 group 的 translateY 校正。fillColor 为黑色，使用处用 app:tint / android:tint 着色。

用法：
    python tools/icons/fetch_icons.py                        # 按 icons.txt 生成到 lib_base/src/main/res/drawable
    python tools/icons/fetch_icons.py --list my.txt --out module_farm/src/main/res/drawable --prefix farm_
    python tools/icons/fetch_icons.py --force                # 覆盖已存在的文件
    python tools/icons/fetch_icons.py qr_code_scanner today  # 只处理命令行列出的图标

清单每行一个图标名；图标在 Material Symbols 中改过名时写成“文件名=源图标名”，
例如 smartphone=mobile 表示下载 mobile 并输出为 ic_smartphone.xml。

来源：优先取 npm 包 @material-symbols/svg-400 的固定版本；该包缺少的图标（如 auto_awesome、done）
改从 Google Fonts 官方地址取 24 px 光学尺寸的同名图标（FALLBACK_TEMPLATE，最新版），文件注释中注明来源。
"""
import argparse
import concurrent.futures
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

DEFAULT_VERSION = "0.47.6"
URL_TEMPLATE = "https://unpkg.com/@material-symbols/svg-400@{version}/rounded/{name}{suffix}.svg"
FALLBACK_TEMPLATE = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{name}/{style}/24px.svg"
NAME_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_]*$")
SVG_NS = "{http://www.w3.org/2000/svg}"

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DEFAULT_LIST = os.path.join(HERE, "icons.txt")
DEFAULT_OUT = os.path.join(ROOT, "lib_base", "src", "main", "res", "drawable")

VECTOR_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded · {name} · FILL {fill} · wght 400 · {source}
     由 tools/icons/fetch_icons.py 生成，不要手改；颜色在使用处用 tint 设置 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{vw}"
    android:viewportHeight="{vh}">
    <group{translate}>
{paths}
    </group>
</vector>
"""


def read_list(path):
    names = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if line:
                names.append(line)
    return names


def download(url, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": "zmt-fetch-icons/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8")


def svg_to_vector(svg_text, name, fill, source):
    root = ET.fromstring(svg_text)
    view_box = root.get("viewBox", "0 -960 960 960").replace(",", " ").split()
    min_x, min_y, vw, vh = (float(v) for v in view_box)
    paths = []
    for el in root.iter():
        if el.tag in (SVG_NS + "path", "path"):
            d = (el.get("d") or "").strip()
            if d:
                paths.append(d)
    if not paths:
        raise ValueError("SVG 中没有 path")
    translate = ""
    if min_x:
        translate += ' android:translateX="%s"' % fmt(-min_x)
    if min_y:
        translate += ' android:translateY="%s"' % fmt(-min_y)
    path_xml = "\n".join(
        '        <path\n            android:fillColor="@android:color/black"\n'
        '            android:pathData="%s" />' % d for d in paths)
    return VECTOR_TEMPLATE.format(name=name, fill=fill, source=source, vw=fmt(vw), vh=fmt(vh),
                                  translate=translate, paths=path_xml)


def fmt(v):
    return str(int(v)) if float(v).is_integer() else str(v)


def split_alias(entry):
    """“文件名=源图标名”拆成 (文件名, 源图标名)；没有“=”时两者相同"""
    if "=" in entry:
        name, source = (part.strip() for part in entry.split("=", 1))
        return name, source
    return entry, entry


def process(entry, args):
    """返回 (name, 结果说明列表, 是否出错)"""
    name, source = split_alias(entry)
    results = []
    failed = False
    for fill, suffix, file_suffix in ((0, "", ""), (1, "-fill", "_filled")):
        file_name = "%sic_%s%s.xml" % (args.prefix, name, file_suffix)
        out_path = os.path.join(args.out, file_name)
        if os.path.exists(out_path) and not args.force:
            results.append("跳过 " + file_name)
            continue
        url = URL_TEMPLATE.format(version=args.version, name=source, suffix=suffix)
        try:
            try:
                xml = svg_to_vector(download(url), source, fill, "@material-symbols/svg-400@" + args.version)
            except urllib.error.HTTPError as e:
                if e.code != 404:
                    raise
                url = FALLBACK_TEMPLATE.format(name=source, style="fill1" if fill else "default")
                xml = svg_to_vector(download(url), source, fill, "fonts.gstatic.com（npm 包中没有）")
        except urllib.error.HTTPError as e:
            results.append("失败 %s：HTTP %s（%s）" % (file_name, e.code, url))
            failed = True
            continue
        except Exception as e:  # noqa: BLE001 网络或解析错误都记为失败
            results.append("失败 %s：%s" % (file_name, e))
            failed = True
            continue
        with open(out_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(xml)
        results.append("生成 " + file_name)
    return name, results, failed


def main():
    parser = argparse.ArgumentParser(description="下载 Material Symbols Rounded 并转换为 VectorDrawable")
    parser.add_argument("names", nargs="*", help="只处理这些图标（默认读取清单文件）")
    parser.add_argument("--list", default=DEFAULT_LIST, help="图标清单文件，每行一个名称（默认 tools/icons/icons.txt）")
    parser.add_argument("--out", default=DEFAULT_OUT, help="输出目录（默认 lib_base/src/main/res/drawable）")
    parser.add_argument("--prefix", default="", help="文件名前缀，例如 farm_ 生成 farm_ic_<名称>.xml（默认无）")
    parser.add_argument("--version", default=DEFAULT_VERSION, help="@material-symbols/svg-400 版本（默认 %s）" % DEFAULT_VERSION)
    parser.add_argument("--force", action="store_true", help="覆盖已存在的文件")
    parser.add_argument("--jobs", type=int, default=8, help="并发下载数（默认 8）")
    args = parser.parse_args()

    if args.prefix and not NAME_PATTERN.match(args.prefix.rstrip("_") or "x"):
        parser.error("前缀只能包含小写字母、数字和下划线")
    names = args.names or read_list(args.list)
    bad = [n for n in names if not all(NAME_PATTERN.match(p) for p in split_alias(n))]
    if bad:
        parser.error("图标名不合法：" + ", ".join(bad))
    names = list(dict.fromkeys(names))  # 去重并保持顺序
    os.makedirs(args.out, exist_ok=True)

    failed_names = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        for name, results, failed in pool.map(lambda n: process(n, args), names):
            for line in results:
                if not line.startswith("跳过"):
                    print(line)
            if failed:
                failed_names.append(name)

    print("完成：%d 个图标，输出到 %s" % (len(names) - len(failed_names), args.out))
    if failed_names:
        print("失败：" + ", ".join(failed_names), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
