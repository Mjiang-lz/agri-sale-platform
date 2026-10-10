package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.AddressDTO;
import com.rxt.security.UserContext;
import com.rxt.service.AddressService;
import com.rxt.vo.AddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口。基础路径 /api/address。
 *
 * <p>本模块<b>整体要求登录</b>，且不在 {@link com.rxt.security.PublicEndpoints} 里 ——
 * 地址是个人资源，没有「游客可看」的形态。</p>
 *
 * <p><b>归属人一律取自登录态</b> {@link UserContext#currentUserId()}，
 * 不接受客户端传入 userId：该参数可被伪造，会让任何人读写他人地址簿。</p>
 *
 * <p>不卡角色（见 {@link AddressService} 的说明）：农户用「采购方」身份下单时同样要收货地址。</p>
 */
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 我的收货地址列表（默认地址排最前）。
     */
    @GetMapping("/list")
    public Result<List<AddressVO>> listMine() {
        return Result.success(addressService.listMine(UserContext.currentUserId()));
    }

    /**
     * 新增收货地址。名下第一条地址会被自动置为默认。
     *
     * @return 新增地址主键，前端可据此把它选中
     */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid AddressDTO dto) {
        return Result.success(addressService.create(UserContext.currentUserId(), dto));
    }

    /**
     * 编辑收货地址（整体覆盖）。
     */
    @PutMapping("/{addressId}")
    public Result<Void> update(@PathVariable Long addressId,
                              @RequestBody @Valid AddressDTO dto) {
        addressService.update(UserContext.currentUserId(), addressId, dto);
        return Result.success();
    }

    /**
     * 删除收货地址。删的是默认地址时，剩余最新的一条会自动顶为默认。
     */
    @DeleteMapping("/{addressId}")
    public Result<Void> remove(@PathVariable Long addressId) {
        addressService.remove(UserContext.currentUserId(), addressId);
        return Result.success();
    }

    /**
     * 设为默认地址（同用户名下先清后置）。
     *
     * <p>用 PUT 而不是 POST：它是幂等的 —— 把同一条设两次，结果一致。</p>
     */
    @PutMapping("/{addressId}/default")
    public Result<Void> setDefault(@PathVariable Long addressId) {
        addressService.setDefault(UserContext.currentUserId(), addressId);
        return Result.success();
    }
}
