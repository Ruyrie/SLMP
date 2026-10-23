package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;

import java.util.List;

/** 模块04 栏舍查询（API-L07、L08、L10、L25）。写接口在后续批次补入本接口。 */
public interface PenService {

    PenFormOptionsVO formOptions();

    List<PenOptionVO> options(PenQuery query);

    PenDetailVO detail(Long penId);

    List<ExecutorOptionVO> executors(Long penId);
}
