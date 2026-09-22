package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品货源数据访问接口（t_product）。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 原子扣减库存：把「库存是否充足」的判断与扣减放进同一条 UPDATE。
     *
     * <p><b>为什么不能查出商品再 updateById 回写：</b>那是「读-改-写」三步，
     * 两个并发下单会读到同一个旧库存、都判定「充足」，随后各自覆盖写入，
     * 卖出的总量就会超过实际库存（超卖）。事务只保证原子性，
     * 挡不住这种基于旧快照的判断。</p>
     *
     * <p>把 {@code stock >= quantity} 写进 WHERE 之后，「判断」由数据库在同一条语句内完成：
     * 两个请求争同一行会被行锁串行化，第一个扣减成功，第二个因 WHERE 不再成立而影响 0 行。</p>
     *
     * @param productId 商品主键
     * @param quantity  购买数量（调用方保证 ≥ 1）
     * @return 受影响行数：1 = 扣减成功；0 = 库存不足或商品不存在
     */
    @Update("UPDATE t_product SET stock = stock - #{quantity} "
            + "WHERE product_id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 原子回滚库存（取消订单时调用）。
     *
     * <p>同样不采用「查出商品 → 加回去 → updateById」：订单取消与新的下单可能并发，
     * 先查后写会丢掉其中一次的修改。写成 {@code stock = stock + quantity} 由数据库自增，
     * 不存在覆盖问题。</p>
     *
     * @param productId 商品主键
     * @param quantity  回滚数量（原订单的购买数量）
     * @return 受影响行数：1 = 回滚成功；0 = 商品已被删除
     */
    @Update("UPDATE t_product SET stock = stock + #{quantity} WHERE product_id = #{productId}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
