package com.zhimu.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimu.server.biz.account.request.AccountCancelRequest;
import com.zhimu.server.biz.account.request.AccountSmsCodeRequest;
import com.zhimu.server.biz.account.service.AccountCancelCleanupService;
import com.zhimu.server.biz.account.service.AccountCancelService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.farm.service.FarmService;
import com.zhimu.server.biz.finance.controller.BusinessReportController;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.biz.account.support.Secrets;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import com.zhimu.server.integration.storage.MinioProperties;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in integration evidence, never part of ordinary tests. Uses an isolated local schema,
 * Redis DB 13 and real MinIO objects under one recorded UUID prefix. No test transaction:
 * cancellation and each cleanup phase exercise their actual production transactions.
 * Run only through the accompanying local runner, which creates/verifies the schema marker.
 */
@EnabledIfEnvironmentVariable(named = "ZMT_RUN_ACCOUNT_CLEANUP_IT", matches = "LOCAL_FIXTURES_ONLY")
@SpringBootTest(classes = {ZhimuServerApplication.class, AccountCancellationLifecycleLocalIT.Isolation.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AccountCancellationLifecycleLocalIT {
    private static final String DATABASE = "zhimu_cleanup_qa_20261026";
    private static final String MARKER = "account-cleanup-local-fixtures-20261026";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1kAAAAASUVORK5CYII=");

    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry properties) {
        if (!"LOCAL_FIXTURES_ONLY".equals(System.getenv("ZMT_RUN_ACCOUNT_CLEANUP_IT"))) {
            throw new IllegalStateException("Explicit local fixture opt-in is required");
        }
        // Guard before Spring creates Flyway/data-access beans, not merely after migration.
        try (var connection = DriverManager.getConnection(jdbcUrl(), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT marker_value FROM qa_fixture_marker WHERE marker_key='purpose'");
             var rows = statement.executeQuery()) {
            if (!rows.next() || !MARKER.equals(rows.getString(1))) throw new IllegalStateException("Wrong fixture database marker");
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("Local fixture database marker is unavailable; use the guarded runner");
        }
        properties.add("spring.datasource.url", AccountCancellationLifecycleLocalIT::jdbcUrl);
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> "6379");
        properties.add("spring.data.redis.database", () -> "13");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.clean-disabled", () -> "true");
        // The runner creates one safety marker table before Flyway's first migration.
        properties.add("spring.flyway.baseline-on-migrate", () -> "true");
        properties.add("spring.flyway.baseline-version", () -> "0");
        properties.add("zhimu.integration.minio.endpoint", () -> "http://127.0.0.1:9000");
        properties.add("zhimu.integration.minio.public-endpoint", () -> "http://127.0.0.1:9000");
        properties.add("zhimu.integration.sms.mode", () -> "mock");
        properties.add("zhimu.integration.push.app-key", () -> "");
        properties.add("zhimu.integration.push.master-secret", () -> "");
        properties.add("zhimu.clock.fixed-date", () -> "");
        properties.add("zhimu.demo.shift.enabled", () -> "false");
        properties.add("logging.level.com.zhimu.server", () -> "INFO");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Isolation {
        @Bean static BeanFactoryPostProcessor disableBackgroundWorkForThisTest() {
            return factory -> {
                BeanDefinitionRegistry definitions = (BeanDefinitionRegistry) factory;
                for (String name : List.of("org.springframework.context.annotation.internalScheduledAnnotationProcessor",
                        "accountCancelledListener", "storageBucketInitializer")) {
                    if (definitions.containsBeanDefinition(name)) definitions.removeBeanDefinition(name);
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountCancelService cancellation;
    @Autowired AccountCancelCleanupService cleanup;
    @Autowired SmsCodeService sms;
    @Autowired FarmService farms;
    @Autowired FarmMemberCache members;
    @Autowired BusinessReportController reports;
    @Autowired ObjectStorage storage;
    @Autowired MinioProperties minio;

    private final String runId = UUID.randomUUID().toString();
    private final String objectPrefix = "qa-account-cleanup/" + runId + "/";
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> farmIds = new ArrayList<>();
    private final List<String> checked = new ArrayList<>();
    private LocalDateTime now;
    private long category;

    @Test
    void realCancellationRetentionAndObjectCleanupStayWithinTheirOwnFixtures() throws Throwable {
        evidence.put("run_id", runId);
        evidence.put("database", DATABASE);
        evidence.put("object_prefix", objectPrefix);
        evidence.put("user_ids", userIds);
        evidence.put("farm_ids", farmIds);
        evidence.put("checks", checked);
        evidence.put("status", "RUNNING");
        try {
            assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(DATABASE);
            assertThat(jdbc.queryForObject("SELECT marker_value FROM qa_fixture_marker WHERE marker_key='purpose'",
                    String.class)).isEqualTo(MARKER);
            assertThat(URI.create(minio.getEndpoint()).getHost()).isIn("127.0.0.1", "localhost");
            assertThat(count("SELECT COUNT(*) FROM t_user WHERE phone IN ('1380006620','13800006620')")).isZero();
            now = BeijingTime.now().truncatedTo(ChronoUnit.SECONDS);
            category = insert("t_category", row("name", "验收" + runId.substring(0, 6), "species", "PIG",
                    "count_unit", "HEAD", "sort_no", 9999, "status", "ENABLED", "created_at", now, "updated_at", now));

            long recent = user("近期注销", null);
            long manager = user("保留成员", null);
            long control = user("对照所有者", null);
            long controlFarm = farm(control, "对照正常场", "APPROVED", null, false);
            long controlPen = pen(controlFarm);
            long transferred = farm(control, "已转让对照场", "APPROVED", now.minusDays(181), true);
            long transferredPen = pen(transferred);
            LocalDateTime originalDisabledAt = now.minusDays(179);
            long retained = farm(recent, "未到期保留场", "APPROVED", originalDisabledAt, false);
            member(retained, manager, "MANAGER");
            long retainedPen = pen(retained);
            long emptyPending = farm(recent, "未审核空场", "PENDING", null, false);
            FileRef avatar = file(recent, null, "AVATAR", "recent-avatar.png");
            FileRef shared = file(recent, null, "AI_IMAGE", "shared-personal.png");
            jdbc.update("UPDATE t_user SET avatar_file_id=? WHERE id=?", avatar.id(), recent);
            jdbc.update("UPDATE t_user SET avatar_file_id=? WHERE id=?", shared.id(), control);
            Map<String, Object> controlBefore = jdbc.queryForMap("SELECT * FROM t_user WHERE id=?", control);
            Map<String, Object> controlFarmBefore = jdbc.queryForMap("SELECT * FROM t_farm WHERE id=?", controlFarm);
            Map<String, Object> transferredBefore = jdbc.queryForMap("SELECT * FROM t_farm WHERE id=?", transferred);

            login(recent);
            var preview = cancellation.preview();
            assertThat(preview.getCanCancel()).isTrue();
            sms.sendToSelf(new AccountSmsCodeRequest("CANCEL_ACCOUNT"));
            cancellation.cancel(new AccountCancelRequest("123456", true, preview.getChecklistVersion()), ClientType.WEB);
            clearContexts();
            assertThat(text("SELECT status FROM t_user WHERE id=?", recent)).isEqualTo("CANCELLED");
            assertThat(count("SELECT COUNT(*) FROM t_farm WHERE id=? AND deleted=0", retained)).isOne();
            assertThat(jdbc.queryForObject("SELECT disabled_at FROM t_farm WHERE id=?", LocalDateTime.class, retained))
                    .isEqualTo(originalDisabledAt);
            assertThat(count("SELECT COUNT(*) FROM t_farm WHERE id=?", emptyPending)).isZero();
            assertThat(text("SELECT status FROM t_farm_member WHERE farm_id=? AND user_id=?", retained, recent))
                    .isEqualTo("ACCOUNT_CANCELED");
            assertThat(text("SELECT status FROM t_farm_member WHERE farm_id=? AND user_id=?", retained, manager))
                    .isEqualTo("ACTIVE");
            checked.add("real_cancel_preserves_disabled_date_and_other_members_but_removes_empty_unapproved_farm");

            cleanup.cleanup(recent);
            assertThat(text("SELECT phone FROM t_user WHERE id=?", recent)).isNotBlank();
            assertThat(text("SELECT cleanup_status FROM t_account_cancel WHERE user_id=?", recent)).isEqualTo("WAITING");
            assertThat(count("SELECT COUNT(*) FROM t_pen WHERE id=?", retainedPen)).isOne();
            assertRetired(avatar);
            assertLive(shared);
            assertThat(count("SELECT COUNT(*) FROM t_account_file_cleanup WHERE file_id=?", shared.id())).isZero();
            assertThat(jdbc.queryForMap("SELECT * FROM t_user WHERE id=?", control)).isEqualTo(controlBefore);
            checked.add("personal_avatar_object_removed_and_shared_personal_file_preserved");
            assertRetainedManagerAccess(manager, retained);

            long beforeThirty = user("注销二十九日", now.minusDays(29));
            String reservedPhone = text("SELECT phone FROM t_user WHERE id=?", beforeThirty);
            cleanup.cleanup(beforeThirty);
            assertThat(text("SELECT phone FROM t_user WHERE id=?", beforeThirty)).isEqualTo(reservedPhone);
            assertThat(count("SELECT COUNT(*) FROM t_account_cancel WHERE user_id=? AND phone_released_at IS NULL",
                    beforeThirty)).isOne();
            checked.add("day_29_phone_still_reserved");

            long aged = user("注销三十一日", now.minusDays(31));
            long dueFarm = farm(aged, "到期应清理场", "APPROVED", now.minusDays(181), true);
            member(dueFarm, manager, "MANAGER");
            long duePen = pen(dueFarm);
            long futureFarm = farm(aged, "同人未到期场", "APPROVED", now.minusDays(179), true);
            member(futureFarm, manager, "MANAGER");
            long futurePen = pen(futureFarm);
            FileRef dueFile = file(aged, dueFarm, "FARM_QUAL", "due-qualification.png");
            FileRef futureFile = file(aged, futureFarm, "FARM_QUAL", "future-qualification.png");
            linkQualification(dueFarm, dueFile);
            linkQualification(futureFarm, futureFile);
            cleanup.cleanup(aged);
            assertThat(text("SELECT phone FROM t_user WHERE id=?", aged)).isNull();
            assertThat(count("SELECT COUNT(*) FROM t_account_cancel WHERE user_id=? AND phone_released_at IS NOT NULL", aged)).isOne();
            assertThat(count("SELECT COUNT(*) FROM t_farm WHERE id=?", dueFarm)).isZero();
            assertThat(count("SELECT COUNT(*) FROM t_pen WHERE id=?", duePen)).isZero();
            assertThat(count("SELECT COUNT(*) FROM t_farm_member WHERE farm_id=?", dueFarm)).isZero();
            assertThat(count("SELECT COUNT(*) FROM t_biz_media WHERE biz_type='FARM' AND biz_id=?", dueFarm)).isZero();
            assertRetired(dueFile);
            assertThat(count("SELECT COUNT(*) FROM t_farm WHERE id=? AND deleted=0", futureFarm)).isOne();
            assertThat(count("SELECT COUNT(*) FROM t_pen WHERE id=?", futurePen)).isOne();
            assertLive(futureFile);
            assertThat(text("SELECT cleanup_status FROM t_account_cancel WHERE user_id=?", aged)).isEqualTo("WAITING");
            assertThat(jdbc.queryForObject("SELECT farm_cleanup_at FROM t_account_cancel WHERE user_id=?",
                    LocalDateTime.class, aged)).isEqualTo(now.plusDays(1));
            checked.add("day_31_phone_released_and_day_181_farm_deleted_while_same_owner_day_179_farm_kept");

            long finished = user("可完整清理", now.minusDays(31));
            FileRef finishedAvatar = file(finished, null, "AVATAR", "finished-avatar.png");
            cleanup.cleanup(finished);
            assertRetired(finishedAvatar);
            assertThat(text("SELECT cleanup_status FROM t_account_cancel WHERE user_id=?", finished)).isEqualTo("DONE");
            long queueCount = count("SELECT COUNT(*) FROM t_account_file_cleanup WHERE user_id=?", finished);
            cleanup.cleanup(finished);
            cleanup.cleanup(aged);
            cleanup.cleanup(control);
            assertThat(count("SELECT COUNT(*) FROM t_account_file_cleanup WHERE user_id=?", finished)).isEqualTo(queueCount);
            assertThat(jdbc.queryForMap("SELECT * FROM t_farm WHERE id=?", controlFarm)).isEqualTo(controlFarmBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM t_farm WHERE id=?", transferred)).isEqualTo(transferredBefore);
            assertThat(count("SELECT COUNT(*) FROM t_pen WHERE id IN (?,?)", controlPen, transferredPen)).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT * FROM t_user WHERE id=?", control)).isEqualTo(controlBefore);
            assertLive(shared);
            assertLive(futureFile);
            checked.add("completed_cleanup_is_idempotent_and_active_owner_transferred_farm_and_other_farm_unchanged");
            evidence.put("status", "PASS");
        } catch (Throwable error) {
            evidence.put("status", "FAIL");
            evidence.put("error_type", error.getClass().getSimpleName());
            throw error;
        } finally {
            clearContexts();
            String output = System.getenv("ZMT_CLEANUP_IT_REPORT");
            if (output != null && !output.isBlank()) {
                Path path = Path.of(output);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
            }
        }
    }

    private void assertRetainedManagerAccess(long user, long farm) throws Exception {
        login(user);
        FarmContextInterceptor interceptor = new FarmContextInterceptor(members);
        MockHttpServletRequest read = request("GET", "/api/v1/business-reports/periods", farm);
        assertThat(interceptor.preHandle(read, new MockHttpServletResponse(),
                new HandlerMethod(reports, "periods", String.class, String.class))).isTrue();
        var detail = farms.detail(farm, user);
        assertThat(detail.getStatus()).isEqualTo("DISABLED");
        assertThat(detail.getPermissions().getWritable()).isFalse();
        assertThat(detail.getPermissions().getCanViewFinance()).isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/v1/business-reports/export", farm),
                new MockHttpServletResponse(), new HandlerMethod(reports, "export", FinanceRequests.ReportExportRequest.class)))
                .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/business-reports/ai-summary/regenerate", farm),
                new MockHttpServletResponse(), new HandlerMethod(reports, "regenerate", FinanceRequests.ReportQuery.class)))
                .isInstanceOf(BizException.class).hasMessageContaining("停用");
        checked.add("retained_manager_reads_real_farm_export_permission_allowed_and_write_denied");
        clearContexts();
    }

    private long user(String name, LocalDateTime cancelledAt) {
        String phone;
        do { phone = "13997" + String.format("%06d", RANDOM.nextInt(1_000_000)); }
        while (count("SELECT COUNT(*) FROM t_user WHERE phone=?", phone) != 0);
        long id = insert("t_user", row("phone", phone, "name", name, "status", cancelledAt == null ? "NORMAL" : "CANCELLED",
                "register_client", "WEB", "agreement_version", "fixture", "agreed_at", now,
                "cancelled_at", cancelledAt, "created_at", now.minusDays(35), "updated_at", now));
        userIds.add(id);
        if (cancelledAt != null) {
            insert("t_account_cancel", row("user_id", id, "phone_masked", "139****0000", "phone_hash",
                    Secrets.sha256Hex("zhimu-account-cancel:" + phone),
                    "phone_release_at", cancelledAt.plusDays(30), "cleanup_status", "PENDING", "client_type", "WEB",
                    "created_at", cancelledAt, "updated_at", cancelledAt));
        }
        return id;
    }

    private long farm(long owner, String name, String audit, LocalDateTime disabledAt, boolean cancellationMarker) {
        long id = insert("t_farm", row("name", name + runId.substring(0, 4), "owner_user_id", owner,
                "main_category_id", category, "scale", 100, "contact_name", "本地验收", "contact_phone", "13997000000",
                "address", "本地隔离验收场", "province", "江苏省", "city", "南京市", "district", "建邺区",
                "adcode", "320105", "longitude", 118.73, "latitude", 32.01, "audit_status", audit,
                "disabled", disabledAt == null ? 0 : 1, "disabled_at", disabledAt,
                "delete_type", cancellationMarker ? "ACCOUNT_CANCEL" : null, "created_at", now.minusDays(200), "updated_at", now));
        farmIds.add(id);
        member(id, owner, "OWNER");
        if ("CANCELLED".equals(text("SELECT status FROM t_user WHERE id=?", owner))) {
            jdbc.update("UPDATE t_farm_member SET status='ACCOUNT_CANCELED',active_flag=NULL,left_at=? WHERE farm_id=? AND user_id=?",
                    now.minusDays(31), id, owner);
        }
        return id;
    }

    private void member(long farm, long user, String role) {
        insert("t_farm_member", row("farm_id", farm, "user_id", user, "role", role, "status", "ACTIVE", "active_flag", 1,
                "join_type", "OWNER".equals(role) ? "CREATE" : "INVITE", "joined_at", now.minusDays(200),
                "created_at", now.minusDays(200), "updated_at", now));
    }

    private long pen(long farm) {
        return insert("t_pen", row("farm_id", farm, "pen_code", "QA1", "pen_name", "验收栏", "pen_type", "FATTENING",
                "category_id", category, "capacity", 100, "created_at", now, "updated_at", now));
    }

    private FileRef file(long owner, Long farm, String scene, String suffix) {
        String key = objectPrefix + suffix;
        storage.put(storage.privateBucket(), key, new ByteArrayInputStream(PNG), PNG.length, "image/png");
        assertThat(storage.stat(storage.privateBucket(), key)).isNotNull();
        long id = insert("t_file", row("bucket", storage.privateBucket(), "object_key", key, "original_name", suffix,
                "file_kind", "IMAGE", "scene", scene, "mime_type", "image/png", "file_ext", "png", "file_size", PNG.length,
                "upload_status", "READY", "uploader_type", "USER", "client_type", "WEB", "bound", 1,
                "farm_id", farm, "created_by", owner, "created_at", now, "updated_at", now));
        return new FileRef(id, key);
    }

    private void linkQualification(long farm, FileRef file) {
        // Legacy qualifications may have NULL farm_id: the FARM/biz_id link must still be removed.
        insert("t_biz_media", row("biz_type", "FARM", "biz_id", farm, "file_id", file.id(),
                "sort_no", 1, "created_at", now, "updated_at", now));
    }

    private void assertRetired(FileRef file) {
        assertThat(count("SELECT COUNT(*) FROM t_file WHERE id=? AND deleted=1", file.id())).isOne();
        assertThat(count("SELECT COUNT(*) FROM t_account_file_cleanup WHERE file_id=? AND status='DONE'", file.id())).isPositive();
        assertThat(storage.stat(storage.privateBucket(), file.key())).isNull();
    }

    private void assertLive(FileRef file) {
        assertThat(count("SELECT COUNT(*) FROM t_file WHERE id=? AND deleted=0", file.id())).isOne();
        assertThat(storage.stat(storage.privateBucket(), file.key())).isNotNull();
    }

    private long insert(String table, Map<String, Object> values) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(values.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put((String) values[i], values[i + 1]);
        return map;
    }

    private long count(String sql, Object... values) { return jdbc.queryForObject(sql, Long.class, values); }
    private String text(String sql, Object... values) { return jdbc.queryForObject(sql, String.class, values); }
    private static String jdbcUrl() { return "jdbc:mysql://127.0.0.1:3306/" + DATABASE
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"; }
    private static void login(long id) { UserContext.set(new UserContext.LoginUser(id, ClientType.WEB, 0, "cleanup-local-it")); }
    private static void clearContexts() { UserContext.clear(); FarmContext.clear(); }
    private static MockHttpServletRequest request(String method, String path, long farm) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Farm-Id", Long.toString(farm));
        return request;
    }
    private record FileRef(long id, String key) { }
}
