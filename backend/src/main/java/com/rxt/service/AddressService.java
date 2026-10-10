package com.rxt.service;

import com.rxt.dto.AddressDTO;
import com.rxt.vo.AddressVO;

import java.util.List;

/**
 * 收货地址模块业务接口（M2 农产品交易 · REQ-07 下单结算）。
 *
 * <p>归属：地址是「个人资源」，不是某个角色专属的业务对象。所以本模块只要求登录
 * （由 {@code LoginInterceptor} 统一拦截），不在 Service 层卡角色 ——
 * 农户用「采购方」身份买东西时同样需要收货地址，
 * 卡角色会把「切换身份就能买」这条既有能力直接堵死。</p>
 *
 * <p>越权口径：所有按主键操作的方法在 Service 内部校验归属，
 * 非本人名下的地址一律按「不存在」处理（不返回 403），避免通过错误码探测出
 * 「这个 addressId 是别人的」。</p>
 */
public interface AddressService {

    /**
     * 查询当前用户名下的全部收货地址，默认地址排在最前，其余按新增倒序。
     */
    List<AddressVO> listMine(Long userId);

    /**
     * 新增收货地址。若用户此前一条地址都没有，这条会被自动置为默认地址。
     *
     * @return 新增地址主键
     */
    Long create(Long userId, AddressDTO dto);

    /**
     * 编辑收货地址（整体覆盖，见 {@link AddressDTO} 的语义说明）。
     */
    void update(Long userId, Long addressId, AddressDTO dto);

    /**
     * 删除收货地址。若删掉的正好是默认地址且还有其它地址，
     * 会把剩余中最新的一条顶为默认，避免留下「有地址但无默认地址」的状态。
     */
    void remove(Long userId, Long addressId);

    /**
     * 把指定地址设为默认（同用户名下先清后置，保证最多一条默认）。
     */
    void setDefault(Long userId, Long addressId);

    /**
     * 取本人名下的一条地址，供下单时生成收货快照。
     *
     * <p>返回 {@link AddressVO} 而不是 {@code Address} 实体：下单要的正是
     * 「收货人 + 电话 + 拼好的地址全文」，而 {@code fullAddress} 是 VO 上算好的。
     * 让调用方拿到实体再自己拼一遍，等于把「省市区怎么拼」这条规则复制两份，
     * 迟早一份改了另一份没改。</p>
     *
     * @throws com.rxt.common.BizException 地址不存在或不属于当前用户
     */
    AddressVO loadOwned(Long userId, Long addressId);
}
