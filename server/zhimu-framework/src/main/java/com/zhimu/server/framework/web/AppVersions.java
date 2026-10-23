package com.zhimu.server.framework.web;

/**
 * “主.次.修订”版本号比较，规则与 App 的 ErrorInterceptor.compareVersion 一致：
 * 逐段按数字比较，缺少的段按 0；每段取开头的数字（1.2.3-debug 的第三段为 3）。
 */
public final class AppVersions {

    private AppVersions() {
    }

    public static int compare(String a, String b) {
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parse(pa[i]) : 0;
            int vb = i < pb.length ? parse(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
