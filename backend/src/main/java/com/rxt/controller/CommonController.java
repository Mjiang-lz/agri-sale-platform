package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.storage.FileBizType;
import com.rxt.storage.FileStorageService;
import com.rxt.storage.StoredFile;
import com.rxt.vo.UploadResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公共文件上传接口。基础路径 {@code /api/common}，对应《概要设计说明书》§6：
 * 「公共 → 文件上传 → {@code POST /api/common/upload} → 转存对象存储并返回 URL」。
 *
 * <p><b>路径为什么是 {@code /common} 而不是 {@code /trade}：</b>
 * 这个接口服务的不止 M2 商品图片 —— 知识配图（专家模块）、用户头像（个人中心）都走它。
 * 设计文档把它归在「公共」一类，照搬即可；业务差异靠 {@code bizType} 参数区分，
 * 而不是给每个模块各开一个上传接口。</p>
 *
 * <p><b>本接口要求登录，且刻意不放进免登录白名单：</b>
 * 上传是写操作，放行等于给游客开一个往服务端写文件的口子。
 * 免登录的只有它的<b>读</b>侧 —— 本地兜底模式下文件通过 {@code GET /files/**} 访问，
 * 那是因为商品图片本身是公开内容（游客能浏览商品列表），
 * 与「谁能上传」是两件事，《概要设计说明书》§8.2 的访问控制讲的正是这个区分。</p>
 *
 * <p><b>不限制角色：</b>农户传商品图、专家传知识配图、所有登录用户传头像，
 * 三类主体都要用。写入位置由 {@code bizType} 白名单（见 {@link FileBizType}）兜住，
 * 调用方无法把文件写到约定之外的位置。</p>
 */
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class CommonController {

    private final FileStorageService fileStorageService;

    /**
     * 上传单个文件，返回可访问地址。
     *
     * @param file    文件本体（表单字段名 {@code file}），由 {@code MultipartFile} 接收
     * @param bizType 业务类型，合法取值 {@code PRODUCT} / {@code KNOWLEDGE} / {@code AVATAR}；
     *                非法或缺失一律 400 并点名合法取值
     * @return 含可访问 url 的上传结果
     */
    @PostMapping("/upload")
    public Result<UploadResultVO> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("bizType") String bizType) {
        FileBizType type = FileBizType.of(bizType);
        StoredFile stored = fileStorageService.store(file, type);
        return Result.success(UploadResultVO.from(stored, type));
    }
}
