package com.rxt.service;
import com.rxt.dto.LoanGroupDTO;

public interface LoanGroupService {
    void createGroup(Long userId, LoanGroupDTO dto);
    void joinGroup(Long groupId, Long userId);
    void quitGroup(Long groupId, Long userId);
}