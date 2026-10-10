package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.dto.AddressDTO;
import com.rxt.entity.Address;
import com.rxt.mapper.AddressMapper;
import com.rxt.service.AddressService;
import com.rxt.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址模块业务实现。
 *
 * <p><b>「最多一条默认地址」这条不变量由本类单独负责。</b>
 * 数据库层兜不住它（MySQL 5.7 与 8.0.12 都只解析、不执行 CHECK 约束，
 * 见 schema.sql 头部说明），所以只要出现「某条被置为默认」的路径，
 * 就必须在同一次事务里把同用户名下的其它地址清掉 ——
 * 本类的 create / update / setDefault 三处都调了 {@link #clearOtherDefaults}，
 * 新增路径时不要漏。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    /** 是默认地址 */
    private static final int DEFAULT_YES = 1;

    /** 不是默认地址 */
    private static final int DEFAULT_NO = 0;

    private final AddressMapper addressMapper;

    @Override
    public List<AddressVO> listMine(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
                // 默认地址排最前，其余按新增倒序（最近加的排前面）。
                // 两段排序写在一个 wrapper 里而不是在 Java 里 sort：
                // 「列表顺序」是展示口径，放在 SQL 里才能和分页/索引保持一致。
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getAddressId);

        return addressMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, AddressDTO dto) {
        boolean wantDefault = isDefaultRequested(dto);

        Address address = new Address();
        address.setUserId(userId);
        applyEditableFields(address, dto);

        // 用户的第一条地址无条件设为默认：否则会出现「有地址、但没有默认地址」，
        // 结算页就得再写一层「没有默认就取第一条」的兜底，同样的规则散在两处。
        // 注意这个判断必须在插入之前做，否则会把自己算进「已有地址」里。
        boolean isFirst = countMine(userId) == 0;
        address.setIsDefault(wantDefault || isFirst ? DEFAULT_YES : DEFAULT_NO);

        addressMapper.insert(address);

        if (address.getIsDefault() == DEFAULT_YES) {
            clearOtherDefaults(userId, address.getAddressId());
        }
        log.debug("新增收货地址 userId={} addressId={} isDefault={}",
                userId, address.getAddressId(), address.getIsDefault());
        return address.getAddressId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long addressId, AddressDTO dto) {
        Address exist = requireOwnAddress(userId, addressId);

        Address update = new Address();
        update.setAddressId(addressId);
        applyEditableFields(update, dto);

        // 已经是默认地址时，不允许通过「编辑时没勾默认」把它降级。
        // 因为一次提交里看不出「用户想取消默认」还是「用户只是没勾」，
        // 而误判成前者会让名下一条默认都不剩，结算页就没法预选地址了。
        // 取消默认的唯一途径是「把别的地址设为默认」或「删掉这一条」。
        boolean staysDefault = Integer.valueOf(DEFAULT_YES).equals(exist.getIsDefault());
        update.setIsDefault(isDefaultRequested(dto) || staysDefault ? DEFAULT_YES : DEFAULT_NO);

        addressMapper.updateById(update);

        if (update.getIsDefault() == DEFAULT_YES) {
            clearOtherDefaults(userId, addressId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long addressId) {
        Address exist = requireOwnAddress(userId, addressId);
        addressMapper.deleteById(addressId);

        if (!Integer.valueOf(DEFAULT_YES).equals(exist.getIsDefault())) {
            return;
        }

        // 删掉的是默认地址：把剩下最新的一条顶上来。
        // 不这么做的话，用户删掉默认地址后会进入「有地址、没默认」的状态 ——
        // 功能上结算页能兜住，但用户会觉得「怎么没默认了」，而且他没法解释为什么。
        List<Address> rest = selectMineOrdered(userId);
        if (rest.isEmpty()) {
            return;
        }
        Long promoteId = rest.get(0).getAddressId();
        Address promote = new Address();
        promote.setAddressId(promoteId);
        promote.setIsDefault(DEFAULT_YES);
        addressMapper.updateById(promote);
        log.debug("删除默认地址后自动顶替 userId={} 新默认 addressId={}", userId, promoteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        requireOwnAddress(userId, addressId);

        Address update = new Address();
        update.setAddressId(addressId);
        update.setIsDefault(DEFAULT_YES);
        addressMapper.updateById(update);

        // 先置后清。顺序不能反：反过来的话万一第二步失败，
        // 会短暂出现「一条默认都没有」；而按这个顺序最坏也只是「短暂两条默认」，
        // 事务回滚后两种都恢复原状。
        clearOtherDefaults(userId, addressId);
    }

    @Override
    public AddressVO loadOwned(Long userId, Long addressId) {
        if (addressId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请选择收货地址");
        }
        Address address = addressMapper.selectById(addressId);
        // 越权与不存在返回同一种错误：否则拿一串 id 试一遍就能探测出
        // 「哪些地址存在、属于别人」，那是可被利用的信息泄露。
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "收货地址不存在，请重新选择");
        }
        return toVO(address);
    }

    /* ==================================================================
     * 内部方法
     * ================================================================== */

    /**
     * 取本人名下的地址，不存在或不属于当前用户时抛 400。
     */
    private Address requireOwnAddress(Long userId, Long addressId) {
        if (addressId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请指定收货地址");
        }
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "收货地址不存在");
        }
        return address;
    }

    /** 本人名下地址，默认地址在前、其余按新增倒序（与列表接口同一口径） */
    private List<Address> selectMineOrdered(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getAddressId);
        return addressMapper.selectList(wrapper);
    }

    private long countMine(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId);
        return addressMapper.selectCount(wrapper);
    }

    /**
     * 把同用户名下除 keepId 之外的默认地址全部清掉。
     *
     * <p>用 update(entity, wrapper) 而不是「查出来逐条 updateById」：
     * 后者在并发下会读到同一批行、各自回写，仍然可能留下两条默认。</p>
     */
    private void clearOtherDefaults(Long userId, Long keepId) {
        Address clear = new Address();
        clear.setIsDefault(DEFAULT_NO);

        LambdaUpdateWrapper<Address> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, DEFAULT_YES)
                .ne(Address::getAddressId, keepId);
        addressMapper.update(clear, wrapper);
    }

    /** 只有显式传 1 才算「要设为默认」，传 null / 0 / 其它值一律按非默认处理 */
    private boolean isDefaultRequested(AddressDTO dto) {
        return dto.getIsDefault() != null && dto.getIsDefault() == DEFAULT_YES;
    }

    private void applyEditableFields(Address address, AddressDTO dto) {
        address.setConsignee(dto.getConsignee().trim());
        address.setPhone(dto.getPhone().trim());
        address.setProvince(dto.getProvince().trim());
        address.setCity(dto.getCity().trim());
        // district 是唯一可空字段：空串与 null 都归一化成 null，
        // 免得库里同时存在「空串」和「NULL」两种表示同一个意思的值
        address.setDistrict(trimToNull(dto.getDistrict()));
        address.setDetail(dto.getDetail().trim());
    }

    private AddressVO toVO(Address address) {
        AddressVO vo = new AddressVO();
        vo.setAddressId(address.getAddressId());
        vo.setConsignee(address.getConsignee());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setFullAddress(join(address));
        return vo;
    }

    /**
     * 拼「省 + 市 + 区县 + 详细地址」。
     *
     * <p>中文地址段之间不加分隔符；区县为空时直接跳过，
     * 不留下多余空格，也不会把字符串 {@code null} 拼进去。</p>
     */
    private String join(Address address) {
        return nz(address.getProvince())
                + nz(address.getCity())
                + nz(address.getDistrict())
                + nz(address.getDetail());
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
