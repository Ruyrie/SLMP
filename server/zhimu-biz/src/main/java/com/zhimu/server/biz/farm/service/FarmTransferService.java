package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.request.CreateTransferRequest;
import com.zhimu.server.biz.farm.vo.TransferAcceptVO;
import com.zhimu.server.biz.farm.vo.TransferPageVO;
import com.zhimu.server.biz.farm.vo.TransferVO;

/** 所有权转让（API-F28–F32） */
public interface FarmTransferService {

    TransferPageVO page(Long farmId, Long userId);

    TransferVO create(Long farmId, Long userId, CreateTransferRequest request);

    TransferAcceptVO accept(Long transferId, Long userId);

    void reject(Long transferId, Long userId);

    TransferVO cancel(Long transferId, Long userId);
}
