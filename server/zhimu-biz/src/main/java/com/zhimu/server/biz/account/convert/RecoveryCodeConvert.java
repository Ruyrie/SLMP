package com.zhimu.server.biz.account.convert;

import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;

/** RecoveryCode → VO（明文由调用方传入，只在当次响应中出现）。 */
public final class RecoveryCodeConvert {

    private RecoveryCodeConvert() {
    }

    public static RecoveryCodeVO toVO(RecoveryCode code, String plaintext, String phone) {
        if (code == null) {
            return null;
        }
        RecoveryCodeScene scene = RecoveryCodeScene.valueOf(code.getScene());
        return RecoveryCodeVO.builder()
                .recoveryCodeId(code.getId())
                .code(plaintext)
                .generatedAt(BeijingTime.format(code.getCreatedAt()))
                .phoneMasked(Masks.phone(phone))
                .scene(code.getScene())
                .sceneText(scene.getText())
                .isRegenerated(Boolean.TRUE.equals(code.getRegenerated()))
                .build();
    }

    public static RecoveryCodeConfirmVO toConfirm(RecoveryCode code, String nextStep) {
        return RecoveryCodeConfirmVO.builder()
                .recoveryCodeId(code.getId())
                .status(code.getStatus())
                .confirmedAt(BeijingTime.format(code.getConfirmedAt()))
                .nextStep(nextStep)
                .build();
    }
}
