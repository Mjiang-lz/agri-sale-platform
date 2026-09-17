package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 专家问答聚合视图对象，由 ExpertQaMapper 跨表查询产生。
 */
@Data
public class ExpertQaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;
    private Long askerId;
    private Long expertId;
    private String questionContent;
    private String answerContent;
    private LocalDateTime askTime;
    private LocalDateTime answerTime;
}
