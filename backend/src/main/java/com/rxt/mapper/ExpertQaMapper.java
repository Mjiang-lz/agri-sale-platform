package com.rxt.mapper;

import com.rxt.vo.ExpertQaVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 专家问答聚合查询接口。
 *
 * <p>问答跨 t_question 与 t_answer 两张表，聚合结果以 ExpertQaVO 返回，
 * 故不继承 BaseMapper，SQL 见 resources/mapper/ExpertQaMapper.xml。</p>
 */
@Mapper
public interface ExpertQaMapper {

    /**
     * 查询指定专家已回复的问答列表。
     *
     * @param expertId 专家主键
     * @return 问答聚合视图列表
     */
    List<ExpertQaVO> selectAnsweredByExpert(@Param("expertId") Long expertId);
}
