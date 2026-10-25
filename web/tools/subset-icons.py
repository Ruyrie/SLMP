"""Material Symbols 图标字体子集生成。

图标字体只保留用到的图标（完整字体约 4 MB，公网首屏加载慢，加载超过 3 秒时图标会先显示成英文单词）。
新增图标后，`npx vitest run tests/admin-icons.test.ts` 会列出字体里缺的图标名；把它们加进来重新生成：

    python -I tools/subset-icons.py <完整字体.woff2> [额外图标名 ...]

完整字体可从 git 历史取：
    git show 920f496:web/packages/shared/src/theme/fonts/MaterialSymbolsOutlined.woff2 > full.woff2

保留的图标 = 当前子集里已有的图标 + 命令行给出的图标名；轴向保留 FILL（0/1）与 wght，GRAD 固定 0、opsz 固定 20
（与 zw.css 的 font-variation-settings 一致）。需要 fontTools 与 brotli。
"""
import io
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

HERE = os.path.dirname(os.path.abspath(__file__))
TARGET = os.path.join(HERE, '..', 'packages', 'shared', 'src', 'theme', 'fonts', 'MaterialSymbolsOutlined.woff2')
CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789_'


def ligature_map(font):
    """图标名 -> 连字字形名。"""
    cmap = font.getBestCmap()
    rev = {}
    for cp, g in sorted(cmap.items()):
        ch = chr(cp)
        if g not in rev or (ch.islower() and not rev[g].islower()):
            rev[g] = ch
    result = {}
    for lookup in font['GSUB'].table.LookupList.Lookup:
        for st in lookup.SubTable:
            if lookup.LookupType == 7:
                st = st.ExtSubTable
            if not hasattr(st, 'ligatures'):
                continue
            for first, ligs in st.ligatures.items():
                for lig in ligs:
                    seq = [first] + list(lig.Component)
                    if all(g in rev for g in seq):
                        result[''.join(rev[g] for g in seq).lower()] = lig.LigGlyph
    return result


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    full_path, extra = sys.argv[1], [n.strip() for n in sys.argv[2:] if n.strip()]
    names = set(ligature_map(TTFont(TARGET))) | set(extra)
    font = TTFont(full_path)
    all_ligs = ligature_map(font)
    unknown = sorted(n for n in names if n not in all_ligs)
    if unknown:
        print('完整字体里没有这些图标：', ', '.join(unknown))
        sys.exit(2)
    axes = {a.axisTag for a in font['fvar'].axes}
    pins = {k: v for k, v in {'GRAD': 0, 'opsz': 20}.items() if k in axes}
    font = instancer.instantiateVariableFont(font, pins)
    # 实例化后的 gvar 是惰性表，直接子集化会 KeyError；先保存再读回
    buf = io.BytesIO()
    font.flavor = None
    font.save(buf)
    buf.seek(0)
    font = TTFont(buf)
    options = subset.Options()
    options.layout_closure = False
    options.layout_features = ['liga', 'rlig', 'calt', 'ccmp']
    options.flavor = 'woff2'
    options.name_IDs = ['*']
    options.notdef_outline = True
    sub = subset.Subsetter(options)
    glyphs = [all_ligs[n] for n in sorted(names)]
    sub.populate(glyphs=glyphs, unicodes=[ord(c) for c in CHARS + CHARS.upper()])
    sub.subset(font)
    font.flavor = 'woff2'
    font.save(TARGET)
    print('图标', len(names), '字节', os.path.getsize(TARGET), '->', os.path.normpath(TARGET))


if __name__ == '__main__':
    main()
