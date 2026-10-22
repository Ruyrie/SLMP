package com.zhimu.slmp.common.session;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 令牌加密：Android Keystore 中生成的 AES-256 密钥（GCM 模式，不可导出，卸载即失效）。
 * 密文格式：Base64(12 字节 IV + 密文)。
 */
final class TokenCipher {

    private static final String TAG = "TokenCipher";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "zmt_session_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private TokenCipher() {
    }

    @Nullable
    static String encrypt(@Nullable String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length);
            buf.put(iv).put(enc);
            return Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "加密失败", e);
            return null;
        }
    }

    /** 解密失败（密钥丢失、数据损坏）返回 null，调用方按未登录处理 */
    @Nullable
    static String decrypt(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
            if (all.length <= IV_LENGTH) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "解密失败", e);
            return null;
        }
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
