package com.zhimu.slmp.network.mock;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mock 文件来源：assets/mock/&lt;组件名&gt;/routes.json 与响应文件。单元测试可用内存实现。
 */
public interface MockSource {

    /** mock 下的子目录名（未排序） */
    @NonNull
    List<String> listDirs();

    /** 读取 mock/&lt;dir&gt;/&lt;file&gt;，不存在返回 null */
    @Nullable
    String read(@NonNull String dir, @NonNull String file);

    /** 读取合并后的 assets（各组件 src/debug/assets 在 debug 包中合并） */
    final class Assets implements MockSource {

        public static final String ROOT = "mock";

        private final AssetManager assets;

        public Assets(@NonNull Context context) {
            this.assets = context.getApplicationContext().getAssets();
        }

        @NonNull
        @Override
        public List<String> listDirs() {
            try {
                String[] dirs = assets.list(ROOT);
                return dirs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(dirs));
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }

        @Nullable
        @Override
        public String read(@NonNull String dir, @NonNull String file) {
            try (InputStream in = assets.open(ROOT + "/" + dir + "/" + file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            } catch (IOException e) {
                return null;
            }
        }
    }
}
