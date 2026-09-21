package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用「名称 + 数量」统计项，供看板里的各类分布图使用。
 *
 * <p>用一个通用结构承载角色分布、融资状态分布等，前端只需一套渲染逻辑。</p>
 */
@Data
public class CountItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组名称，如角色名「农户」、申请状态「已推送银行」 */
    private String name;

    /** 该分组下的记录数 */
    private long count;

    public CountItemVO() {
    }

    public CountItemVO(String name, long count) {
        this.name = name;
        this.count = count;
    }
}
