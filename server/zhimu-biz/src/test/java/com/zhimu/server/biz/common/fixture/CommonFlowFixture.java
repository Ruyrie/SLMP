package com.zhimu.server.biz.common.fixture;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.common.change.ChangeRequestHandlerRegistry;
import com.zhimu.server.biz.common.convert.ChangeRequestConvert;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.biz.common.mapper.OperationLogMapper;
import com.zhimu.server.biz.common.query.RecordLogRow;
import com.zhimu.server.biz.common.service.impl.ChangeRequestAdminServiceImpl;
import com.zhimu.server.biz.common.service.impl.ChangeRequestApplicationService;
import com.zhimu.server.biz.common.service.impl.ChangeRequestNoGenerator;
import com.zhimu.server.biz.common.service.impl.ChangeRequestServiceImpl;
import com.zhimu.server.biz.common.service.impl.OperationLogRecorderImpl;
import com.zhimu.server.biz.common.service.impl.OperationLogServiceImpl;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 修改申请完整链路的测试夹具：内存中的 t_change_request / t_operation_log（Mockito 模拟 Mapper），
 * 真实的 ApplicationService / AdminService / OperationLogRecorder / U14 查询服务，以及测试 handler。
 */
public class CommonFlowFixture {

    public final ChangeRequestMapper changeRequestMapper = mock(ChangeRequestMapper.class);
    public final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    public final FarmMemberCache farmMemberCache = mock(FarmMemberCache.class);
    public final AccountQueryService accountQueryService = mock(AccountQueryService.class);
    public final FarmAccountService farmAccountService = mock(FarmAccountService.class);
    public final MessageService messageService = mock(MessageService.class);
    public final IpRegionResolver ipRegionResolver = mock(IpRegionResolver.class);
    public final ChangeRequestNoGenerator noGenerator = mock(ChangeRequestNoGenerator.class);

    public final List<ChangeRequest> requests = new ArrayList<>();
    public final List<OperationLog> logs = new ArrayList<>();

    public final OperationLogRecorderImpl recorder;
    public final TestFeedRecordHandler handler;
    public final ChangeRequestHandlerRegistry registry;
    public final ChangeRequestApplicationService applicationService;
    public final ChangeRequestAdminServiceImpl adminService;
    public final OperationLogServiceImpl logQueryService;
    public final ChangeRequestService changeRequestService;

    private final Map<String, FarmContext.FarmMember> members = new LinkedHashMap<>();
    private final AtomicLong requestId = new AtomicLong(9100);
    private final AtomicLong logId = new AtomicLong(5000);
    private final AtomicInteger requestNoSeq = new AtomicInteger();

    public CommonFlowFixture() {
        stubChangeRequestMapper();
        stubOperationLogMapper();
        stubAccounts();
        stubFarms();
        stubMembers();
        when(ipRegionResolver.resolve(anyString())).thenReturn("广东省 深圳市");
        when(noGenerator.next()).thenAnswer(invocation ->
                "CR20261019" + String.format("%04d", requestNoSeq.incrementAndGet()));

        recorder = new OperationLogRecorderImpl(operationLogMapper, provider(accountQueryService),
                provider(ipRegionResolver));
        handler = new TestFeedRecordHandler(recorder);
        registry = new ChangeRequestHandlerRegistry(List.of(handler));
        ChangeRequestConvert convert = new ChangeRequestConvert(provider(accountQueryService), provider(farmAccountService));
        applicationService = new ChangeRequestApplicationService(registry, changeRequestMapper, noGenerator, convert,
                farmMemberCache);
        adminService = new ChangeRequestAdminServiceImpl(changeRequestMapper, registry, recorder,
                provider(messageService));
        logQueryService = new OperationLogServiceImpl(operationLogMapper, provider(accountQueryService),
                farmMemberCache);
        changeRequestService = new ChangeRequestServiceImpl(changeRequestMapper);
    }

    public void reset() {
        requests.clear();
        logs.clear();
        members.clear();
        handler.reset();
        requestId.set(9100);
        logId.set(5000);
        requestNoSeq.set(0);
        UserContext.clear();
        FarmContext.clear();
    }

    // ===== 登录与成员 =====

    public static void login(Long userId) {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "test-jti"));
    }

    public static void logout() {
        UserContext.clear();
        FarmContext.clear();
    }

    public void grant(Long farmId, Long userId, FarmRole role) {
        grant(farmId, userId, role, FarmStatus.NORMAL);
    }

    public void grant(Long farmId, Long userId, FarmRole role, FarmStatus status) {
        members.put(key(farmId, userId), new FarmContext.FarmMember(farmId, "东山猪场", userId, 5001L, role,
                role == FarmRole.STAFF ? Set.of(2003L) : Set.of(), status));
    }

    public void revoke(Long farmId, Long userId) {
        members.remove(key(farmId, userId));
    }

    public void enterFarm(Long farmId, Long userId, FarmRole role) {
        grant(farmId, userId, role);
        FarmContext.set(members.get(key(farmId, userId)));
    }

    public ChangeRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    // ===== 桩实现 =====

    private void stubChangeRequestMapper() {
        when(changeRequestMapper.insert(any(ChangeRequest.class))).thenAnswer(invocation -> {
            ChangeRequest request = invocation.getArgument(0);
            request.setId(requestId.incrementAndGet());
            if (request.getSubmittedAt() == null) {
                request.setSubmittedAt(BeijingTime.now());
            }
            requests.add(request);
            return 1;
        });
        when(changeRequestMapper.selectByIdAny(any())).thenAnswer(invocation -> find(invocation.getArgument(0)));
        when(changeRequestMapper.selectPending(any(), any())).thenAnswer(invocation -> {
            String bizType = invocation.getArgument(0);
            Long bizId = invocation.getArgument(1);
            return requests.stream()
                    .filter(r -> bizType.equals(r.getBizType()) && bizId.equals(r.getBizId()))
                    .filter(r -> r.getPendingFlag() != null && r.getPendingFlag() == 1)
                    .findFirst().orElse(null);
        });
        when(changeRequestMapper.countResubmitted(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return (long) requests.stream().filter(r -> id.equals(r.getResubmitOfId())).toList().size();
        });
        when(changeRequestMapper.selectMine(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    List<ChangeRequest> filtered = new ArrayList<>(filter(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));
                    filtered.sort(Comparator.comparing(ChangeRequest::getSubmittedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ChangeRequest::getId).reversed());
                    int offset = invocation.getArgument(5);
                    int limit = invocation.getArgument(6);
                    return filtered.stream().skip(offset).limit(limit).toList();
                });
        when(changeRequestMapper.countMine(any(), any(), any(), any(), any())).thenAnswer(invocation ->
                (long) filter(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4)).size());
        when(changeRequestMapper.countMineByStatus(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String status = invocation.getArgument(1);
            return (long) filter(invocation.getArgument(0), status, invocation.getArgument(2),
                    invocation.getArgument(3), invocation.getArgument(4)).size();
        });
        when(changeRequestMapper.countPendingBySubmitter(any(), any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            Long farmId = invocation.getArgument(1);
            return (long) requests.stream()
                    .filter(r -> userId.equals(r.getSubmittedBy()))
                    .filter(r -> "PENDING".equals(r.getStatus()))
                    .filter(r -> farmId == null || farmId.equals(r.getFarmId()))
                    .toList().size();
        });
        when(changeRequestMapper.countPending()).thenAnswer(invocation ->
                (long) requests.stream().filter(r -> "PENDING".equals(r.getStatus())).toList().size());
        when(changeRequestMapper.claimApprove(any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            ChangeRequest request = find(invocation.getArgument(0));
            int version = invocation.getArgument(1);
            if (request == null || !"PENDING".equals(request.getStatus())
                    || request.getVersion() == null || request.getVersion() != version) {
                return 0;
            }
            request.setStatus("APPROVED");
            request.setPendingFlag(null);
            request.setReviewedBy(invocation.getArgument(2));
            request.setReviewedAt(invocation.getArgument(4));
            request.setReviewComment(invocation.getArgument(3));
            request.setVersion(request.getVersion() + 1);
            return 1;
        });
        when(changeRequestMapper.markRejected(any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            ChangeRequest request = find(invocation.getArgument(0));
            int version = invocation.getArgument(1);
            if (request == null || !"PENDING".equals(request.getStatus())
                    || request.getVersion() == null || request.getVersion() != version) {
                return 0;
            }
            request.setStatus("REJECTED");
            request.setPendingFlag(null);
            request.setReviewedBy(invocation.getArgument(2));
            request.setRejectReason(invocation.getArgument(3));
            request.setReviewedAt(invocation.getArgument(4));
            request.setVersion(request.getVersion() + 1);
            return 1;
        });
        when(changeRequestMapper.updateResult(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            ChangeRequest request = find(invocation.getArgument(0));
            if (request == null) {
                return 0;
            }
            request.setRecalcSummary(invocation.getArgument(1));
            request.setRecalcCount(invocation.getArgument(2));
            request.setImpactJson(invocation.getArgument(3));
            return 1;
        });
        when(changeRequestMapper.cancelPendingBySubmitter(any(), any(), any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return cancel(r -> userId.equals(r.getSubmittedBy()), invocation.getArgument(1), invocation.getArgument(2));
        });
        when(changeRequestMapper.cancelPendingByFarm(any(), any(), any())).thenAnswer(invocation -> {
            Long farmId = invocation.getArgument(0);
            return cancel(r -> farmId.equals(r.getFarmId()), invocation.getArgument(1), invocation.getArgument(2));
        });
    }

    private int cancel(java.util.function.Predicate<ChangeRequest> predicate, String reason, LocalDateTime now) {
        int count = 0;
        for (ChangeRequest request : requests) {
            if ("PENDING".equals(request.getStatus()) && predicate.test(request)) {
                request.setStatus("CANCELED");
                request.setPendingFlag(null);
                request.setCancelReason(reason);
                request.setVersion(request.getVersion() == null ? 1 : request.getVersion() + 1);
                count++;
            }
        }
        return count;
    }

    private List<ChangeRequest> filter(Long submittedBy, String status, String bizType, Long farmId, String keyword) {
        return requests.stream()
                .filter(r -> submittedBy == null || submittedBy.equals(r.getSubmittedBy()))
                .filter(r -> status == null ? !"CANCELED".equals(r.getStatus()) : status.equals(r.getStatus()))
                .filter(r -> bizType == null || bizType.equals(r.getBizType()))
                .filter(r -> farmId == null || farmId.equals(r.getFarmId()))
                .filter(r -> keyword == null || (r.getRecordLocator() != null && r.getRecordLocator().contains(keyword)))
                .toList();
    }

    private void stubOperationLogMapper() {
        when(operationLogMapper.insert(any(OperationLog.class))).thenAnswer(invocation -> {
            OperationLog log = invocation.getArgument(0);
            log.setId(logId.incrementAndGet());
            logs.add(log);
            return 1;
        });
        when(operationLogMapper.selectUserLogs(any(), any(), anyInt())).thenAnswer(invocation -> {
            String bizType = invocation.getArgument(0);
            Long bizId = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return logs.stream()
                    .filter(log -> "USER".equals(log.getOperatorType()))
                    .filter(log -> bizType.equals(log.getBizType()) && bizId.equals(log.getBizId()))
                    .sorted(Comparator.comparing(OperationLog::getId).reversed())
                    .limit(limit)
                    .map(this::toRow)
                    .toList();
        });
    }

    private RecordLogRow toRow(OperationLog log) {
        RecordLogRow row = new RecordLogRow();
        row.setId(log.getId());
        row.setOperatorId(log.getOperatorId());
        row.setOperatorName(log.getOperatorName());
        row.setOperatorRole(log.getOperatorRole());
        row.setOpAction(log.getOpAction());
        row.setReason(log.getReason());
        row.setBeforeJson(log.getBeforeJson());
        row.setAfterJson(log.getAfterJson());
        row.setFarmId(log.getFarmId());
        row.setChangeRequestId(log.getChangeRequestId());
        ChangeRequest request = log.getChangeRequestId() == null ? null : find(log.getChangeRequestId());
        row.setChangeRequestNo(request == null ? null : request.getRequestNo());
        row.setCreatedAt(log.getCreatedAt() == null ? BeijingTime.now() : log.getCreatedAt());
        return row;
    }

    private void stubAccounts() {
        when(accountQueryService.getBrief(any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return new AccountBrief(userId, "王建国", "13800006620", "NORMAL", false);
        });
        when(accountQueryService.listBriefs(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            Map<Long, AccountBrief> result = new LinkedHashMap<>();
            for (Long id : ids) {
                result.put(id, new AccountBrief(id, "王建国", "13800006620", "NORMAL", false));
            }
            return result;
        });
    }

    private void stubFarms() {
        when(farmAccountService.findMyFarmBrief(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            Long farmId = invocation.getArgument(1);
            return FarmBriefVO.builder().farmId(farmId).name("东山猪场").speciesText("猪").status("NORMAL")
                    .statusText("正常").role("OWNER").build();
        });
    }

    private void stubMembers() {
        when(farmMemberCache.get(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long farmId = invocation.getArgument(0);
            Long userId = invocation.getArgument(1);
            return members.get(key(farmId, userId));
        });
    }

    private ChangeRequest find(Long id) {
        if (id == null) {
            return null;
        }
        return requests.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
    }

    private static String key(Long farmId, Long userId) {
        return farmId + ":" + userId;
    }

    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getObject()).thenReturn(bean);
        return provider;
    }
}
