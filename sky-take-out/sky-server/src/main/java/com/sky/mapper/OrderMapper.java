package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 根据DTO里的参数查询数据，封装成Page对象
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);
    @Select("select count(orders.id) from orders where status = #{status}")
    Integer getNumber(Integer status);
    /**
     * 根据支付状态和下单时间查询超时订单
     */
    @Select("select * from orders where status = #{status} and order_time < #{ordertime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime ordertime);

    /**
     * 根据动态条件统计营业额数据
     * @param map
     * @return
     */
    Double sumByMap(Map<String,Object> map);

    /**
     * 根据动态条件统计订单数据
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin,LocalDateTime end);
}
