package com.rxt.storage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 生成对象键（存储侧的相对路径）。
 *
 * <p><b>为什么要重命名，而不是沿用上传时的文件名：</b>
 * 客户端给的文件名是不可信输入，它能带来的问题不止一种 ——</p>
 * <ul>
 *   <li><b>路径穿越</b>：文件名写成 {@code ../../application.yml}，若直接拼进路径就写到了目录之外；</li>
 *   <li><b>覆盖已有文件</b>：两个用户都上传 {@code 1.jpg}，后者静默覆盖前者；</li>
 *   <li><b>中文/空格/表情符号</b>：落在 URL 里要反复编码，是各种「本地好好的、线上裂图」的来源；</li>
 *   <li><b>带可执行后缀</b>：{@code x.jsp}、{@code x.sh} 这类名字在生产环境一旦被当成资源执行就是事故。
 *       这里扩展名取自<b>文件头识别结果</b>（见 {@link ImageFileValidator}），不取客户端后缀，
 *       从根上不产生可执行后缀。</li>
 * </ul>
 *
 * <p>命名规则：{@code <业务目录>/<yyyy>/<MM>/<uuid 去横线>.<规范扩展名>}，
 * 例如 {@code product/2026/09/3f2b1c9d4e5a4b8c9d0e1f2a3b4c5d6e.jpg}。</p>
 *
 * <p>按年月分目录不是为了好看：对象存储与本地文件系统在单目录文件数很多时都会明显变慢，
 * 分目录后单层条目可控，也便于按时间批量清理（如清理半年前的测试图片）。</p>
 */
final class ObjectKeys {

    private ObjectKeys() {
    }

    /**
     * @param bizType   业务归属，作为一级目录
     * @param extension 规范扩展名（不含点），来自文件头识别结果
     * @return 对象键
     */
    static String build(FileBizType bizType, String extension) {
        LocalDate today = LocalDate.now();
        String name = UUID.randomUUID().toString().replace("-", "");
        return "%s/%04d/%02d/%s.%s".formatted(
                bizType.getDir(), today.getYear(), today.getMonthValue(), name, extension);
    }
}
