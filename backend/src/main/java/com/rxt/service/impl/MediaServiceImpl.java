package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.entity.Knowledge;
import com.rxt.entity.Product;
import com.rxt.entity.User;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.service.MediaService;
import com.rxt.storage.FileStorageService;
import com.rxt.storage.StoredObject;
import com.rxt.vo.MediaItemVO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 图片资源管理实现。
 *
 * <p><b>引用是怎么判定的：用 URL 后缀匹配，而不是相等。</b>
 * 同一个对象在不同时期可能以不同形式落库 —— 本地兜底模式写的是
 * {@code /api/files/product/2026/10/x.png}，OBS 模式写的是
 * {@code https://<bucket>.<endpoint>/product/2026/10/x.png}
 * （而且桶名、endpoint 都可能随部署变化）。两者唯一的共同点是<b>都以对象键结尾</b>，
 * 所以判据取 {@code url.endsWith(objectKey)}。用相等判断会在切换存储模式后
 * 全量失效，且失效方向是「以为没人用」—— 正好是最危险的那一侧。</p>
 *
 * <p><b>为什么一次把所有引用读进内存：</b>引用索引要供列表整页使用。
 * 逐条对象去查四次库，一页 12 条就是 48 次查询。这些列的总行数等于
 * 用户数 + 商品数 + 知识数，量级很小，一次性取回反而更省。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final FileStorageService fileStorageService;
    private final PermissionChecker permissionChecker;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final KnowledgeMapper knowledgeMapper;

    /** 引用方描述的最大条数，避免一行被引用几十次时把提示撑爆 */
    private static final int MAX_REPORTED_REFERENCES = 5;

    @Override
    public IPage<MediaItemVO> pageMedia(String prefix, long pageNum, long pageSize) {
        permissionChecker.requireAdmin();

        List<StoredObject> objects = fileStorageService.list(prefix);
        List<Reference> references = loadAllReferences();

        List<MediaItemVO> all = new ArrayList<>(objects.size());
        for (StoredObject object : objects) {
            all.add(toItem(object, references));
        }

        // 存储层不做分页（对象量级小、且跨两种实现难统一），在这里切页
        return new Page<MediaItemVO>(pageNum, pageSize)
                .setRecords(slice(all, pageNum, pageSize))
                .setTotal(all.size());
    }

    @Override
    public void deleteMedia(String objectKey) {
        permissionChecker.requireAdmin();
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "缺少参数：objectKey");
        }

        List<String> usedBy = findReferences(objectKey, loadAllReferences());
        if (!usedBy.isEmpty()) {
            // 报出「谁在用」比只报「不能删」有用得多：管理员据此才能决定下一步
            StringBuilder message = new StringBuilder("该图片仍被业务数据引用，不能删除：");
            message.append(String.join("、", usedBy));
            if (usedBy.size() >= MAX_REPORTED_REFERENCES) {
                message.append("…");
            }
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), message.toString());
        }

        fileStorageService.delete(objectKey);
        log.info("后台删除图片 objectKey={}", objectKey);
    }

    /** 全库扫描四类图片引用，返回「URL → 描述」列表 */
    private List<Reference> loadAllReferences() {
        List<Reference> references = new ArrayList<>();

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getUserId, User::getRealName, User::getAvatarUrl)
                .isNotNull(User::getAvatarUrl));
        for (User user : users) {
            references.add(new Reference(user.getAvatarUrl(),
                    "用户 #" + user.getUserId() + userLabel(user) + " 头像"));
        }

        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .select(Product::getProductId, Product::getProductName,
                        Product::getImageUrl, Product::getImages));
        for (Product product : products) {
            String label = "商品 #" + product.getProductId() + productLabel(product);
            if (StringUtils.hasText(product.getImageUrl())) {
                references.add(new Reference(product.getImageUrl(), label + " 主图"));
            }
            List<String> images = product.getImages();
            if (images != null) {
                for (int i = 0; i < images.size(); i++) {
                    if (StringUtils.hasText(images.get(i))) {
                        references.add(new Reference(images.get(i), label + " 附图 " + (i + 1)));
                    }
                }
            }
        }

        List<Knowledge> knowledgeList = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .select(Knowledge::getKnowledgeId, Knowledge::getTitle, Knowledge::getImageUrl)
                .isNotNull(Knowledge::getImageUrl));
        for (Knowledge knowledge : knowledgeList) {
            references.add(new Reference(knowledge.getImageUrl(),
                    "知识 #" + knowledge.getKnowledgeId() + knowledgeLabel(knowledge) + " 配图"));
        }

        return references;
    }

    /**
     * 找出引用了该对象键的描述。
     *
     * <p>匹配规则见类注释：<b>URL 以对象键结尾</b>即视为引用。
     * 加长度下限是因为空串会 {@code endsWith} 到任何东西上 —— 库里若有脏的空 URL，
     * 会把每一个对象都判成"被引用"，于是谁也删不掉。</p>
     */
    private List<String> findReferences(String objectKey, List<Reference> references) {
        if (!StringUtils.hasText(objectKey)) {
            return Collections.emptyList();
        }
        List<String> hit = new ArrayList<>();
        for (Reference reference : references) {
            if (reference.url() != null && reference.url().endsWith(objectKey)) {
                hit.add(reference.label());
                if (hit.size() >= MAX_REPORTED_REFERENCES) {
                    break;
                }
            }
        }
        return hit;
    }

    private MediaItemVO toItem(StoredObject object, List<Reference> references) {
        List<String> usedBy = findReferences(object.objectKey(), references);
        MediaItemVO vo = new MediaItemVO();
        vo.setObjectKey(object.objectKey());
        vo.setUrl(object.url());
        vo.setSize(object.size());
        vo.setLastModified(object.lastModified());
        vo.setReferenced(!usedBy.isEmpty());
        vo.setReferences(usedBy);
        return vo;
    }

    /** 内存分页。越界的页码返回空列表，与 MyBatis-Plus 的分页行为一致 */
    private List<MediaItemVO> slice(List<MediaItemVO> all, long pageNum, long pageSize) {
        if (pageSize <= 0 || pageNum <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "分页参数不合法");
        }
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    private String userLabel(User user) {
        return StringUtils.hasText(user.getRealName()) ? "「" + user.getRealName() + "」" : "";
    }

    private String productLabel(Product product) {
        return StringUtils.hasText(product.getProductName()) ? "「" + product.getProductName() + "」" : "";
    }

    private String knowledgeLabel(Knowledge knowledge) {
        return StringUtils.hasText(knowledge.getTitle()) ? "「" + knowledge.getTitle() + "」" : "";
    }

    /**
     * 一条引用记录：某个业务列里存着的 URL + 它属于谁。
     *
     * @param url   落库的图片地址（可能是相对路径，也可能是 OBS 绝对地址）
     * @param label 给人看的描述，用于「不能删除，正被 XXX 使用」的提示
     */
    private record Reference(String url, String label) {
    }
}
