package com.rxt.common;

import java.util.Arrays;
import lombok.Getter;

/**
 * 站内通知的事件类型，对应 {@code t_notification.type} 的取值。
 *
 * <p>《软件需求规格说明书》表 3-1「消息通知」一行列了四类事件：
 * 订单状态变更、问答回复、预约回复、融资申请状态变更 —— 本枚举与之一一对应。</p>
 *
 * <p><b>为什么这个枚举放在 {@code common} 而不是某个业务包：</b>
 * 它被 M2（订单）、M4/M5（问答与预约）、融资（M0）四个不同模块共同写入，
 * 又只有通知中心一个读取方。放在任何一个业务包下都会让另外三个模块
 * 反向依赖那个包 —— 这是「跨模块共享的常量」的典型特征，与 {@link ResultCode} 同类。
 * 参照物是 {@code storage.FileBizType}：那个枚举只被上传链路使用，所以留在 storage 包内。</p>
 *
 * <p><b>{@code link} 为什么由后端给：</b>见 {@code docs/接口约定-站内通知.md} §3.3 ——
 * 「订单通知跳哪一页」这个知识只应存在一处，前端拼的话会在每个渲染通知的位置重复一遍。
 * 这里存的是<b>页面级</b>路径，不带业务主键：本项目没有订单详情页
 * （订单列表用弹窗处理单条），拼 {@code /trade/orders/{bizId}} 会得到一个匹配不到的路由。
 * 业务主键另有 {@code bizId} 字段下发，供前端做高亮或定位。</p>
 */
@Getter
public enum NotificationType {

    /** 订单状态变更（M2 农产品交易）→ 我的订单 */
    ORDER("订单", "/trade/orders"),

    /** 专家问答回复（M4/M5）→ 专家问答 */
    QA("问答", "/expert/qa"),

    /** 专家预约状态流转（M4/M5）→ 专家预约 */
    APPOINTMENT("预约", "/expert/appointment"),

    /** 融资申请状态变更（M0）→ 我的融资 */
    FINANCE("融资", "/finance/my");

    /** 中文名，用于日志与排查时不必再去对照英文枚举名 */
    private final String label;

    /** 前端跳转目标（业务端路径，不含 {@code /app} 前缀，由前端按 base 处理） */
    private final String link;

    NotificationType(String label, String link) {
        this.label = label;
        this.link = link;
    }

    /**
     * 取某个类型对应的跳转目标。
     *
     * <p>库里存的是字符串，可能来自更早的版本、或人工插入的数据。
     * 认不出的类型**返回 null 而不是抛错**：列表接口是为了「把已有的通知读出来」，
     * 因为一条老数据的类型名对不上就让整页打不开，代价不对等。
     * 前端拿到 null 时不做跳转即可。</p>
     *
     * @param type 库里的 type 值
     * @return 跳转路径；认不出时返回 null
     */
    public static String linkOf(String type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equals(type))
                .map(NotificationType::getLink)
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为受支持的类型。
     *
     * <p>写入方（业务服务）用它做白名单校验：非法类型会被挡在入库之前，
     * 而不是先写进库、再让前端渲染时才发现认不出 —— 那时已经无法区分
     * 「类型拼错了」和「这是一条新增类型的通知，前端还没跟上」。</p>
     *
     * @param type 待校验的类型名
     * @return true 表示在四类之内
     */
    public static boolean isSupported(String type) {
        return type != null && Arrays.stream(values()).anyMatch(t -> t.name().equals(type));
    }

    /**
     * 全部合法取值，拼错误提示用（避免提示文案与实际枚举脱节）。
     */
    public static String names() {
        return String.join("、", Arrays.stream(values()).map(Enum::name).toList());
    }
}
