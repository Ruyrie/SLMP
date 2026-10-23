package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.request.PenCreateRequest;
import com.zhimu.server.biz.herd.request.PenStatusRequest;
import com.zhimu.server.biz.herd.request.PenUpdateRequest;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;

import java.util.List;

/** 模块04 栏舍接口（API-L07、L08、L10–L13、L25、L27）。 */
public interface PenService {

    PenFormOptionsVO formOptions();

    List<PenOptionVO> options(PenQuery query);

    PenDetailVO detail(Long penId);

    List<ExecutorOptionVO> executors(Long penId);

    PenDetailVO create(PenCreateRequest request);

    PenDetailVO update(Long penId, PenUpdateRequest request);

    PenDetailVO disable(Long penId, PenStatusRequest request);

    PenDetailVO enable(Long penId, PenStatusRequest request);
}
