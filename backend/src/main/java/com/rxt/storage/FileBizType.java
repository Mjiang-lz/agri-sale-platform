package com.rxt.storage;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import java.util.Arrays;
import lombok.Getter;

/**
 * 上传文件的业务归属。
 *
 * <p>《概要设计说明书》§2 技术方案：「商品图片、知识配图与头像经后端转存至对象存储」——
 * 这三类就是本枚举的取值，同时也是对象键的<b>第一级目录名</b>。</p>
 *
 * <p><b>为什么用枚举而不是让前端直接传目录字符串：</b>
 * 目录名一旦由调用方自由输入，就等于把「往对象存储的哪个位置写文件」交给了客户端。
 * 枚举天然是一张白名单，非法值在进实现之前就被挡掉 ——
 * 与 M6 审核接口对 {@code targetType} 的处理口径一致（非法值给 400，而不是查出空列表）。</p>
 */
@Getter
public enum FileBizType {

    /** 商品图片（M2 农产品交易，农户发布/编辑商品时上传） */
    PRODUCT("product"),

    /** 知识配图（专家发布知识条目时上传） */
    KNOWLEDGE("knowledge"),

    /** 用户头像（个人中心维护资料时上传） */
    AVATAR("avatar");

    /** 对象键的一级目录，如 {@code product/2026/09/<uuid>.jpg} */
    private final String dir;

    FileBizType(String dir) {
        this.dir = dir;
    }

    /**
     * 把前端传来的字符串解析成枚举，大小写不敏感。
     *
     * @param value 前端传值，如 {@code PRODUCT} / {@code product}
     * @return 匹配的业务类型
     * @throws BizException 值为空或不在白名单内（400，点名合法取值，便于前端自查）
     */
    public static FileBizType of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "缺少参数：bizType（合法取值：PRODUCT、KNOWLEDGE、AVATAR）");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value) || t.dir.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.BAD_REQUEST.getCode(),
                        "bizType 不合法：" + value + "（合法取值：PRODUCT、KNOWLEDGE、AVATAR）"));
    }
}
