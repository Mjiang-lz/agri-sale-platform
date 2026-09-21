package com.rxt.storage;

/**
 * 一次成功存储的结果。
 *
 * <p>用 {@code record} 而不是普通 DTO：它是「存储动作的返回值」，一旦产生就不该被修改，
 * 不可变语义正好对上。字段刻意保持最少 —— 只有 url 是要给前端的，其余供日志与排查用。</p>
 *
 * @param url       对外可访问地址。本地兜底模式下形如
 *                  {@code /api/files/product/2026/09/xxx.jpg}（相对路径，前端直接放进 img 的 src 即可）；
 *                  OBS 模式下是 {@code https://<bucket>.<endpoint>/<objectKey>} 绝对地址。
 * @param objectKey 对象键（存储侧的相对路径），形如 {@code product/2026/09/<uuid>.jpg}。
 *                  本地模式可用它直接定位磁盘文件，OBS 模式可在控制台按它检索对象。
 * @param size      文件字节数，回显给前端用于展示。
 * @param storage   实际承载存储的实现标识：{@code local} 或 {@code obs}。
 *                  它让联调时一眼看出当前跑的是哪条链路（不开这个字段，
 *                  很容易出现「以为在传 OBS、其实写的是本地磁盘」的错觉）。
 */
public record StoredFile(String url, String objectKey, long size, String storage) {
}
