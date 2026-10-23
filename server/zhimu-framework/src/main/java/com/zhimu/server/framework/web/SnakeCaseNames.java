package com.zhimu.server.framework.web;

/**
 * snake_case 名称转驼峰：start_date → startDate，recent7_done_days → recent7DoneDays，items[0].actual_qty → items[0].actualQty。
 */
public final class SnakeCaseNames {

    private SnakeCaseNames() {
    }

    public static String toCamel(String name) {
        if (name == null || name.indexOf('_') < 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '.' && sb.charAt(sb.length() - 1) != ']') {
                upper = true;
            } else if (c == '.' || c == '[' || c == ']') {
                upper = false;
                sb.append(c);
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
}
