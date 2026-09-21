package com.rxt.storage;

import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传图片的校验：大小上限、格式白名单、真实类型识别。
 *
 * <p>《概要设计说明书》§8.4：「文件上传：限制文件类型与大小（单张图片不超过 5MB），
 * 上传文件重命名后转存对象存储，不直接执行」。本类是这条要求的前半段。</p>
 *
 * <p><b>为什么用文件头（magic number）判断类型，而不是看文件名后缀或 Content-Type：</b>
 * 这两者都由客户端提供、都可任意伪造 —— 把一个可执行文件改名成 {@code a.jpg}、
 * 再把 {@code Content-Type} 写成 {@code image/jpeg}，是最基础的绕过手法。
 * 文件头是文件内容自身的前若干字节，改后缀改不掉。所以这里：</p>
 * <ul>
 *   <li>只认文件头能识别出的真实格式；识别不出来的（无论后缀写得多像图片）一律拒绝；</li>
 *   <li>落存储时的扩展名与 Content-Type <b>取自识别结果</b>，不取客户端给的后缀。
 *       于是一个「后缀 jpg 实际是 png」的文件会被正确存成 .png，而不是留下名实不符的对象。</li>
 * </ul>
 *
 * <p><b>上限为什么在代码里判、而不把 multipart 上限改成 5MB：</b>
 * {@code spring.servlet.multipart.max-file-size} 是全局传输层限制，将来融资资料（PDF）
 * 等其他上传类型未必适用同一个 5MB；5MB 是「图片」这条业务规则，就该落在图片的校验里。</p>
 */
public final class ImageFileValidator {

    /** 单张图片大小上限：5MB */
    public static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 上限的可读写法，供提示文案复用，避免「文案写 5MB、常量改成 8MB」的错位 */
    public static final String MAX_SIZE_LABEL = "5MB";

    /** 支持格式的可读写法 */
    public static final String SUPPORTED_LABEL = "jpg / jpeg / png / gif / webp";

    /** 读取文件头的字节数：webp 需要看到第 12 字节才能确认，取 12 覆盖全部格式 */
    private static final int HEAD_LENGTH = 12;

    // —— 支持格式的规范名。扩展名与 Content-Type 都从这里取，不接受客户端输入 ——

    private static final ImageFormat JPEG = new ImageFormat("jpg", "image/jpeg");
    private static final ImageFormat PNG = new ImageFormat("png", "image/png");
    private static final ImageFormat GIF = new ImageFormat("gif", "image/gif");
    private static final ImageFormat WEBP = new ImageFormat("webp", "image/webp");

    private ImageFileValidator() {
    }

    /**
     * 识别出的图片格式。
     *
     * @param extension   规范扩展名（不含点），如 {@code jpg}
     * @param contentType 与之一致的 MIME 类型，如 {@code image/jpeg}
     */
    public record ImageFormat(String extension, String contentType) {
    }

    /**
     * 校验上传的图片并返回其真实格式。
     *
     * <p>按「由廉到贵」的顺序检查：空文件 → 大小 → 文件头。前两步不需要读流，
     * 大多数非法请求在这一步就被挡掉，不必浪费一次 IO。</p>
     *
     * @param file 上传的文件
     * @return 由文件头识别出的真实格式
     * @throws BizException 校验不通过（一律 400，并把「错在哪、该传什么」写进文案）
     */
    public static ImageFormat validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "图片大小不能超过 " + MAX_SIZE_LABEL + "（当前 " + readableSize(file.getSize()) + "）");
        }
        ImageFormat format = detect(readHead(file));
        if (format == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "仅支持 " + SUPPORTED_LABEL + " 格式的图片");
        }
        return format;
    }

    /**
     * 读取文件头。
     *
     * <p>{@code getInputStream()} 每次调用都会返回一个新的流（StandardMultipartFile 内部
     * 是重新从临时文件读），所以这里读完文件头之后，调用方还能再取一次流去上传，互不影响。</p>
     */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEAD_LENGTH);
        } catch (IOException e) {
            // 读不到头说明临时文件有问题，属于请求本身异常，不该报「系统繁忙」
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "图片读取失败，请重新选择文件");
        }
    }

    /**
     * 按文件头识别格式，识别不出返回 {@code null}。
     *
     * <p>字节在 Java 里是有符号的，凡是要和 {@code 0xFF} 之类比较的地方都必须先
     * {@code & 0xFF} 转成无符号；ASCII 字符（{@code 'P'}/{@code 'G'} 等）小于 128，直接比即可。</p>
     */
    private static ImageFormat detect(byte[] h) {
        // JPEG：FF D8 FF
        if (h.length >= 3 && u(h[0]) == 0xFF && u(h[1]) == 0xD8 && u(h[2]) == 0xFF) {
            return JPEG;
        }
        // PNG：89 50 4E 47 0D 0A 1A 0A
        if (h.length >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && u(h[4]) == 0x0D && u(h[5]) == 0x0A && u(h[6]) == 0x1A && u(h[7]) == 0x0A) {
            return PNG;
        }
        // GIF："GIF87a" 或 "GIF89a"
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return GIF;
        }
        // WEBP："RIFF" + 4 字节长度 + "WEBP"
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    /** 把字节数写成人看的大小，用于提示文案（如「6.2MB」） */
    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }
}
