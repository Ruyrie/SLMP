package com.zhimu.server.framework.exception;

import tools.jackson.databind.PropertyNamingStrategies;

/**
 * 把校验错误的属性路径转成请求 JSON 的 snake_case 路径：items[0].actualQty → items[0].actual_qty。
 * 方括号内（下标、Map 键）原样保留；命名规则与 Jackson SNAKE_CASE 完全一致。
 */
public final class FieldPaths {

    private static final SnakeCase SNAKE = new SnakeCase();

    private FieldPaths() {
    }

    public static String toSnake(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        StringBuilder out = new StringBuilder(path.length() + 8);
        StringBuilder name = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '[') {
                flush(name, out);
                int end = path.indexOf(']', i);
                end = end < 0 ? path.length() - 1 : end;
                out.append(path, i, end + 1);
                i = end + 1;
                continue;
            }
            if (c == '.') {
                flush(name, out);
                out.append(c);
            } else {
                name.append(c);
            }
            i++;
        }
        flush(name, out);
        return out.toString();
    }

    private static void flush(StringBuilder name, StringBuilder out) {
        if (!name.isEmpty()) {
            out.append(SNAKE.convert(name.toString()));
            name.setLength(0);
        }
    }

    private static final class SnakeCase extends PropertyNamingStrategies.SnakeCaseStrategy {

        String convert(String name) {
            return translate(name);
        }
    }
}
