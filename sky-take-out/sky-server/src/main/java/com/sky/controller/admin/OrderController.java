package com.sky.controller.admin;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.OrderDetail;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController("AdminOrderController")
@Api(tags = "管理端下单接口")
@RequestMapping("/admin/order")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    @GetMapping("/conditionSearch")
    @ApiOperation("订单条件搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        PageResult pageResult = orderService.conditionSearch(ordersPageQueryDTO);
        return Result.success(pageResult);
    }
    @GetMapping("/statistics")
    @ApiOperation("统计订单状态数量")
    public Result<OrderStatisticsVO> orderStatistics(){
        OrderStatisticsVO orderStatisticsVO = orderService.getStatistics();
        return Result.success(orderStatisticsVO);
    }
    @GetMapping("/details/{id}")
    @ApiOperation("商户端订单详情查看")
    public Result<OrderVO> getDetails(@PathVariable Long id){
        OrderVO orderVO = orderService.getDetailsById(id);
        return Result.success(orderVO);
    }
    @PutMapping("/confirm")
    @ApiOperation("商户端接单")
    public Result setConfirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO){
        orderService.setConfirm(ordersConfirmDTO);
        return Result.success();
    }
    @PutMapping("/rejection")
    @ApiOperation("商户端拒单")
    public Result setReject(@RequestBody OrdersRejectionDTO ordersRejectionDTO){
        orderService.setReject(ordersRejectionDTO);
        return Result.success();
    }
    @PutMapping("/cancel")
    @ApiOperation("商户端取消订单")
    public Result setCancel(@RequestBody OrdersCancelDTO ordersCancelDTO){
        orderService.setCancel(ordersCancelDTO);
        return Result.success();
    }


}
