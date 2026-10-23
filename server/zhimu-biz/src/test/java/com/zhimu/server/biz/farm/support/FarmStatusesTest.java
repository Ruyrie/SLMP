package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.vo.FarmPermissionVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FarmStatusesTest {

    @Test
    void derivesStatusInPriorityOrder() {
        Farm farm = new Farm();
        farm.setAuditStatus("PENDING");
        farm.setDisabled(true);
        farm.setFrozen(true);
        assertThat(FarmStatuses.of(farm)).isEqualTo(FarmStatus.FROZEN);

        farm.setFrozen(false);
        assertThat(FarmStatuses.of(farm)).isEqualTo(FarmStatus.DISABLED);

        farm.setDisabled(false);
        assertThat(FarmStatuses.of(farm)).isEqualTo(FarmStatus.PENDING);

        farm.setAuditStatus("REJECTED");
        assertThat(FarmStatuses.of(farm)).isEqualTo(FarmStatus.REJECTED);

        farm.setAuditStatus("APPROVED");
        assertThat(FarmStatuses.of(farm)).isEqualTo(FarmStatus.NORMAL);
    }

    @Test
    void buildsRestrictTipByStatus() {
        Farm farm = new Farm();
        farm.setName("东山猪场");
        assertThat(FarmStatuses.restrictTip(farm, FarmStatus.PENDING))
                .isEqualTo(FarmStatuses.MSG_PENDING_RESTRICT);
        assertThat(FarmStatuses.restrictTip(farm, FarmStatus.DISABLED))
                .isEqualTo("东山猪场已停用。数据只能查看，不能登记新的记录。");
        assertThat(FarmStatuses.restrictTip(farm, FarmStatus.NORMAL)).isNull();
    }

    @Test
    void permissionMatrixFollowsRoleAndStatus() {
        FarmPermissionVO owner = FarmPermissions.of(FarmStatus.NORMAL, FarmRole.OWNER);
        assertThat(owner.getCanInvite()).isTrue();
        assertThat(owner.getCanChangeRole()).isTrue();
        assertThat(owner.getCanTransfer()).isTrue();
        assertThat(owner.getCanQuit()).isFalse();
        assertThat(owner.getWritable()).isTrue();

        FarmPermissionVO manager = FarmPermissions.of(FarmStatus.NORMAL, FarmRole.MANAGER);
        assertThat(manager.getCanEditBasic()).isTrue();
        assertThat(manager.getCanAdjustPens()).isTrue();
        assertThat(manager.getCanInvite()).isFalse();
        assertThat(manager.getCanQuit()).isTrue();
        assertThat(manager.getCanViewFinance()).isTrue();

        FarmPermissionVO staff = FarmPermissions.of(FarmStatus.NORMAL, FarmRole.STAFF);
        assertThat(staff.getCanViewFarmInfo()).isFalse();
        assertThat(staff.getCanViewMembers()).isFalse();
        assertThat(staff.getCanQuit()).isTrue();
        assertThat(staff.getWritable()).isTrue();

        FarmPermissionVO disabled = FarmPermissions.of(FarmStatus.DISABLED, FarmRole.OWNER);
        assertThat(disabled.getCanEnable()).isTrue();
        assertThat(disabled.getCanEditBasic()).isFalse();
        assertThat(disabled.getCanTransfer()).isTrue();
        assertThat(disabled.getCanRemoveMember()).isTrue();
        assertThat(disabled.getWritable()).isFalse();

        FarmPermissionVO pending = FarmPermissions.of(FarmStatus.PENDING, FarmRole.OWNER);
        assertThat(pending.getCanInvite()).isFalse();
        assertThat(pending.getCanViewFarmInfo()).isFalse();
        assertThat(pending.getCanQuit()).isFalse();
    }
}
