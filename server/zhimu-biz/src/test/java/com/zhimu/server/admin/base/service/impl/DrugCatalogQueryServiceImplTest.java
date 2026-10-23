package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.AdminBaseTableInfo;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.BannedDrugHit;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.mapper.BannedDrugHitMapper;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogUsageMapper;
import com.zhimu.server.admin.base.mapper.FarmMainCategoryReadMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** U11 目录搜索分页与禁用药命中；覆盖停用/禁用过滤、40401、42203、按畜种用量。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrugCatalogQueryServiceImplTest {

    @Mock private DrugCatalogMapper drugCatalogMapper;
    @Mock private DrugCatalogUsageMapper drugCatalogUsageMapper;
    @Mock private BannedDrugMapper bannedDrugMapper;
    @Mock private BannedDrugHitMapper bannedDrugHitMapper;
    @Mock private FarmMainCategoryReadMapper farmMainCategoryReadMapper;
    @Mock private CategoryQueryService categoryQueryService;

    private DrugCatalogQueryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        AdminBaseTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        service = new DrugCatalogQueryServiceImpl(drugCatalogMapper, drugCatalogUsageMapper, bannedDrugMapper,
                bannedDrugHitMapper, farmMainCategoryReadMapper, categoryQueryService);
        when(bannedDrugMapper.selectList(any())).thenReturn(List.of(banned(9901L, "盐酸克仑特罗", "克伦特罗、瘦肉精")));
        when(bannedDrugHitMapper.insert(any(BannedDrugHit.class))).thenReturn(1);
    }

    @AfterEach
    void clearContext() {
        FarmContext.clear();
    }

    @Test
    void searchFiltersDisabledAndBannedAndKeepsMockOrder() {
        DrugCatalog amoxicillin = catalog(7201L, "VET_DRUG", "阿莫西林可溶性粉", "阿莫西林", "ENABLED", false);
        DrugCatalog florfenicol = catalog(7202L, "VET_DRUG", "氟苯尼考粉", null, "ENABLED", false);
        DrugCatalog disabled = catalog(7203L, "VET_DRUG", "硫酸黏菌素预混剂", null, "DISABLED", false);
        DrugCatalog banned = catalog(7299L, "VET_DRUG", "氯霉素", null, "ENABLED", true);
        when(drugCatalogMapper.selectList(any()))
                .thenReturn(List.of(amoxicillin, florfenicol, disabled, banned));

        var result = service.search(null, null, "VET_DRUG", 1, 20);

        assertThat(result.getBannedHit()).isNull();
        assertThat(result.getItems()).extracting("id").containsExactly(7201L, 7202L);
        assertThat(result.getItems().get(0).getMeasureUnit()).isEqualTo("G");
        assertThat(result.getItems().get(0).getPackUnit()).isEqualTo("袋");
        assertThat(result.getItems().get(0).getPackSize()).isEqualByComparingTo("500");
    }

    @Test
    void searchPaginatesInMemory() {
        when(drugCatalogMapper.selectList(any())).thenReturn(List.of(
                catalog(7201L, "VET_DRUG", "阿莫西林可溶性粉", null, "ENABLED", false),
                catalog(7202L, "VET_DRUG", "氟苯尼考粉", null, "ENABLED", false),
                catalog(7203L, "VET_DRUG", "恩诺沙星注射液", null, "ENABLED", false)));

        var first = service.search(null, "PIG", "VET_DRUG", 1, 2);
        var second = service.search(null, "PIG", "VET_DRUG", 2, 2);
        var third = service.search(null, "PIG", "VET_DRUG", 3, 2);

        assertThat(first.getItems()).extracting("id").containsExactly(7201L, 7202L);
        assertThat(second.getItems()).extracting("id").containsExactly(7203L);
        assertThat(third.getItems()).isEmpty();
    }

    @Test
    void searchClampsSizeTo50AndDefaultsTo20() {
        when(drugCatalogMapper.selectList(any())).thenReturn(List.of());
        service.search(null, null, "VET_DRUG", 0, 100);
        // page 从 1 起、size 最大 50，不抛异常且不返回数据
        assertThat(service.search(null, null, "VET_DRUG", 1, 0).getItems()).isEmpty();
    }

    @Test
    void searchBannedKeywordReturnsBannedHitAndRecordsHit() {
        var result = service.search("克仑特罗", "PIG", "VET_DRUG", 1, 20);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getBannedHit()).isNotNull();
        assertThat(result.getBannedHit().getBannedDrugId()).isEqualTo(9901L);
        assertThat(result.getBannedHit().getName()).isEqualTo("盐酸克仑特罗");
        assertThat(result.getBannedHit().getMessage()).isEqualTo("盐酸克仑特罗为禁用药，不能入库");

        ArgumentCaptor<BannedDrugHit> captor = ArgumentCaptor.forClass(BannedDrugHit.class);
        verify(bannedDrugHitMapper).insert(captor.capture());
        assertThat(captor.getValue().getBannedDrugId()).isEqualTo(9901L);
        assertThat(captor.getValue().getHitSource()).isEqualTo("SEARCH");
        assertThat(captor.getValue().getKeyword()).isEqualTo("克仑特罗");
        verify(drugCatalogMapper, never()).selectList(any());
    }

    @Test
    void searchBannedAliasHitsToo() {
        var result = service.search("瘦肉精", null, "VET_DRUG", 1, 20);
        assertThat(result.getBannedHit().getBannedDrugId()).isEqualTo(9901L);
    }

    @Test
    void searchRejectsBlankCategoryAndLongKeyword() {
        assertThatThrownBy(() -> service.search(null, null, " ", 1, 20))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThatThrownBy(() -> service.search("x".repeat(41), null, "VET_DRUG", 1, 20))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void infersSpeciesFromFarmMainCategory() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        when(farmMainCategoryReadMapper.selectMainCategoryId(1001L)).thenReturn(101L);
        when(categoryQueryService.info(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪",
                "HEAD", "头", false, true));
        when(drugCatalogMapper.selectList(any())).thenReturn(List.of());

        service.search(null, null, "VET_DRUG", 1, 20);

        verify(farmMainCategoryReadMapper).selectMainCategoryId(1001L);
        verify(categoryQueryService).info(101L);
    }

    @Test
    void requireCatalogMapsInfoAndThrows40401() {
        DrugCatalog catalog = catalog(7003L, "VACCINE", "口蹄疫灭活疫苗", null, "ENABLED", false);
        catalog.setMeasureUnit("DOSE");
        catalog.setVaccineDoseUnit("DOSE_PER_HEAD");
        catalog.setDefaultVaccinateMethodCode("IM");
        when(drugCatalogMapper.selectById(7003L)).thenReturn(catalog);

        DrugCatalogInfo info = service.requireCatalog(7003L);

        assertThat(info.name()).isEqualTo("口蹄疫灭活疫苗");
        assertThat(info.measureUnit()).isEqualTo("DOSE");
        assertThat(info.vaccineDoseUnit()).isEqualTo("DOSE_PER_HEAD");
        assertThat(info.enabled()).isTrue();
        assertThatThrownBy(() -> service.requireCatalog(9999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void isBannedAndAssertNotBanned() {
        DrugCatalog bannedFlag = catalog(7299L, "VET_DRUG", "氯霉素", null, "ENABLED", true);
        DrugCatalog bannedName = catalog(7300L, "VET_DRUG", "盐酸克仑特罗", null, "ENABLED", false);
        DrugCatalog safe = catalog(7201L, "VET_DRUG", "阿莫西林可溶性粉", null, "ENABLED", false);
        when(drugCatalogMapper.selectById(7299L)).thenReturn(bannedFlag);
        when(drugCatalogMapper.selectById(7300L)).thenReturn(bannedName);
        when(drugCatalogMapper.selectById(7201L)).thenReturn(safe);

        assertThat(service.isBanned(7299L, "PIG")).isTrue();
        assertThat(service.isBanned(7300L, "PIG")).isTrue();
        assertThat(service.isBanned(7201L, "PIG")).isFalse();
        assertThat(service.isBanned(9999L, "PIG")).isFalse();

        assertThatThrownBy(() -> service.assertNotBanned(7300L, "PIG"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("盐酸克仑特罗")
                .extracting("code").isEqualTo(ErrorCode.BANNED_DRUG.getCode());
        assertThatThrownBy(() -> service.assertNotBanned(7299L, "PIG"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.BANNED_DRUG.getCode());
        service.assertNotBanned(7201L, "PIG");
    }

    @Test
    void checkBannedMatchesNameAliasAndReturnsNullWhenClean() {
        assertThat(service.checkBanned("盐酸克仑特罗").getMessage()).isEqualTo("盐酸克仑特罗为禁用药，不能使用");
        assertThat(service.checkBanned("克伦特罗").getBannedDrugId()).isEqualTo(9901L);
        assertThat(service.checkBanned("瘦肉精").getBannedDrugId()).isEqualTo(9901L);
        assertThat(service.checkBanned("阿莫西林可溶性粉")).isNull();
        assertThat(service.checkBannedDrugs("禁用瘦肉精和地西泮")).extracting("name")
                .containsExactly("盐酸克仑特罗");
    }

    @Test
    void usagesSortedBySpeciesOrder() {
        DrugCatalogUsage sheep = usage("SHEEP", 14);
        DrugCatalogUsage pig = usage("PIG", 14);
        DrugCatalogUsage poultry = usage("POULTRY", 7);
        when(drugCatalogUsageMapper.selectList(any())).thenReturn(List.of(sheep, pig, poultry));

        assertThat(service.usages(7201L)).extracting("species").containsExactly("PIG", "POULTRY", "SHEEP");
        when(drugCatalogUsageMapper.selectOne(any())).thenReturn(pig);
        assertThat(service.usage(7201L, "pig")).isSameAs(pig);
        assertThat(service.usage(7201L, "DOG")).isNull();
    }

    private static DrugCatalog catalog(Long id, String category, String name, String alias, String status,
                                       boolean banned) {
        DrugCatalog catalog = new DrugCatalog();
        catalog.setId(id);
        catalog.setDrugCategory(category);
        catalog.setName(name);
        catalog.setAlias(alias);
        catalog.setSpec("10 毫升/支");
        catalog.setMeasureUnit("G");
        catalog.setPackUnit("袋");
        catalog.setPackSize(new BigDecimal("500"));
        catalog.setStatus(status);
        catalog.setBanned(banned);
        return catalog;
    }

    private static BannedDrug banned(Long id, String name, String alias) {
        BannedDrug drug = new BannedDrug();
        drug.setId(id);
        drug.setName(name);
        drug.setAlias(alias);
        drug.setSortNo(1);
        return drug;
    }

    private static DrugCatalogUsage usage(String species, int withdrawalDays) {
        DrugCatalogUsage usage = new DrugCatalogUsage();
        usage.setCatalogId(7201L);
        usage.setSpecies(species);
        usage.setWithdrawalDays(withdrawalDays);
        return usage;
    }
}
