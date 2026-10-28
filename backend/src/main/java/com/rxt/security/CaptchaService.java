package com.rxt.security;

import lombok.Data;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：生成、暂存、一次性校验。
 *
 * <p><b>为什么自绘而不用 Kaptcha 之类的库</b>：这里要的只是「4 位不易 OCR 的字符」，
 * 引一个依赖换来的能力（中文算式、声音、滑动）一个都用不上，却多一条要维护的版本线。
 * 用 JDK 自带的 {@link BufferedImage} + {@link Graphics2D} 画，服务端无头环境
 * （Spring Boot 打包成 jar 跑在轻量服务器上时没有显示器）同样能出图 ——
 * BufferedImage 是内存位图，不依赖本地图形环境。</p>
 *
 * <p><b>存哪</b>：进程内 {@link ConcurrentHashMap}，不落库、不进 Redis。
 * 验证码是「一次成功即弃」的短周期凭据，重启丢失完全可以接受；
 * 为它引入一个外部存储才是真正的过度设计。</p>
 *
 * <p><b>安全性口径</b>：</p>
 * <ul>
 *   <li><b>一次性</b>：校验通过或失败后立即从 map 移除，防同一验证码被重放；</li>
 *   <li><b>与登录态解耦</b>：key 由服务端生成、不指向任何用户，拿到图也推不出账号；</li>
 *   <li><b>过期</b>：5 分钟；上限 5000 条，超出时按「最早的过期时间」整批清理，
 *       防止内存被无限增长（每个验证码才几十字节，但攻击者可以故意只取不验）。</li>
 * </ul>
 */
@Component
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 42;
    private static final int CODE_LENGTH = 4;

    /** 有效期 5 分钟 —— 够填完一个表单，又不至于让丢弃的验证码长期占内存 */
    private static final long EXPIRE_MS = 5 * 60 * 1000L;

    /** 最多同时存在多少个未使用的验证码 */
    private static final int MAX_ENTRIES = 5000;

    /**
     * 字符集：去掉 0/O/1/I/L/Z/2 这类形近字符。
     * 用户在一个 120×42 的小图里辨认「l」和「1」，错了只会觉得是平台bug。
     */
    private static final char[] CHARS = "ABCDEFGHJKMNPQRSTUVWXY3456789".toCharArray();

    private static final Color[] INK = {
            new Color(14, 122, 95), new Color(31, 45, 39), new Color(154, 109, 56),
            new Color(60, 90, 120), new Color(120, 70, 90)
    };

    private final SecureRandom random = new SecureRandom();

    /** key -> 正确答案（统一大写，比较时忽略大小写） */
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    /** key -> 过期时间戳（毫秒）。与 codes 分开存，清理时不必遍历 value */
    private final Map<String, Long> expireAt = new ConcurrentHashMap<>();

    /** 验证码图与 key。image 是完整的 data URL，前端可直接塞进 <img src> */
    @Data
    public static class Captcha {
        /** 提交登录/注册时要原样带回的凭据 */
        private String captchaKey;
        /** data:image/png;base64,... */
        private String captchaImage;
    }

    /** 生成一个新验证码。 */
    public Captcha create() {
        evictIfFull();

        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        String code = sb.toString();

        String key = newKey();
        codes.put(key, code);
        expireAt.put(key, System.currentTimeMillis() + EXPIRE_MS);

        Captcha vo = new Captcha();
        vo.setCaptchaKey(key);
        vo.setCaptchaImage("data:image/png;base64," + draw(code));
        return vo;
    }

    /**
     * 校验并消费一个验证码。
     *
     * @return true 表示通过；false 表示过期 / 不存在 / 不匹配
     */
    public boolean validate(String key, String input) {
        if (key == null || key.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        Long exp = expireAt.get(key);
        if (exp == null || exp < System.currentTimeMillis()) {
            codes.remove(key);
            expireAt.remove(key);
            return false;
        }
        // 先取出来再删：一次性。无论是猜对还是猜错，这个 key 都不能再用
        String expect = codes.remove(key);
        expireAt.remove(key);
        if (expect == null) {
            return false;
        }
        // 忽略大小写与首尾空白 —— 用户在小图上分清大小写成本很高，不值得因此拒绝
        return expect.equalsIgnoreCase(input.trim());
    }

    /* ---------------- 内部实现 ---------------- */

    private String newKey() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String draw(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 米白底：与登录卡片的白底拉开一点，又不至于像纯白那样和卡片糊在一起
            g.setColor(new Color(246, 243, 236));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 两条斜向干扰线，颜色浅、不压字符
            for (int i = 0; i < 2; i++) {
                g.setColor(new Color(14, 122, 95, 42));
                g.setStroke(new BasicStroke(1.4f));
                int x1 = random.nextInt(WIDTH / 2);
                int y1 = random.nextInt(HEIGHT);
                int x2 = WIDTH / 2 + random.nextInt(WIDTH / 2);
                int y2 = random.nextInt(HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 若干噪点
            for (int i = 0; i < 40; i++) {
                g.setColor(new Color(14, 122, 95, 60));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }

            // 四个字符：各自随机字体、颜色、旋转与基线
            // 字体只用逻辑名（Serif/SansSerif/Monospaced），任何 JVM 上都一定存在 ——
            // 写具体中文字体名在无头服务器上会静默回退成默认字体，旋转效果就废了
            String[] fonts = {Font.SERIF, Font.SANS_SERIF, Font.MONOSPACED};
            int slot = WIDTH / (CODE_LENGTH + 1);
            for (int i = 0; i < CODE_LENGTH; i++) {
                AffineTransform at = new AffineTransform();
                double rad = (random.nextDouble() - 0.5) * 0.5; // ±约 14°
                at.rotate(rad, 0, 0);
                Font f = new Font(fonts[random.nextInt(fonts.length)], Font.BOLD, 26).deriveFont(at);
                g.setFont(f);
                g.setColor(INK[random.nextInt(INK.length)]);
                int x = slot * (i + 1) - 8;
                int y = HEIGHT / 2 + random.nextInt(7) - 3;
                g.drawString(String.valueOf(code.charAt(i)), x, y);
            }
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            // ImageIO 对 png 是内置支持，正常不会走到这里；真失败就抛，别返回半张图
            throw new IllegalStateException("验证码图片编码失败", e);
        }
    }

    /** 达到上限时把已过期的整批清掉；清完仍然满就不再生成，让请求失败而不是 OOM */
    private void evictIfFull() {
        if (codes.size() < MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        expireAt.forEach((k, exp) -> {
            if (exp < now) {
                codes.remove(k);
                expireAt.remove(k);
            }
        });
        if (codes.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("验证码数量已达上限，请稍后重试");
        }
    }
}
