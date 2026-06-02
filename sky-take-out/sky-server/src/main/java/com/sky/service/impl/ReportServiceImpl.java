package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    /**
     * 根据日期统计营业额
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //当前集合存放begin到end范围内的每天日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询date日期对应的营业额数据，状态要为已完成
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //select sum(count) from orders where order_time > begin and order_time < end and status = 5;
            Map<String,Object> map = new HashMap<>();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double v = orderMapper.sumByMap(map);
            v = v == null ? 0.0 : v;
            turnoverList.add(v);
        }
        //拼接字符串，返回结果
        String join = StringUtils.join(dateList,",");
        String join1 = StringUtils.join( turnoverList,",");
        TurnoverReportVO turnoverReportVO = new TurnoverReportVO();
        turnoverReportVO.setDateList(join);
        turnoverReportVO.setTurnoverList(join1);
        return turnoverReportVO;
    }

    /**
     * 用户数据统计
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //当前集合存放begin到end范围内的每天日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime begintime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endtime = LocalDateTime.of(date, LocalTime.MAX);
            Map<String,Object> map = new HashMap<>();
            map.put("end",endtime);
            //总用户数量
            Integer totalUser = userMapper.countByMap(map);
            map.put("begin",begintime);
            //新增用户数量
            Integer newUser = userMapper.countByMap(map);
            //加入列表
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();
    }
    /**
     * 根据日期统计订单数据
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //创建集合和数字存放结果
        List<Integer> orderList = new ArrayList<>();
        List<Integer> validList = new ArrayList<>();
        //遍历datelist集合，查询有效订单数和订单总数
        for (LocalDate date : dateList) {
            LocalDateTime begintime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endtime = LocalDateTime.of(date, LocalTime.MAX);
            //查询每日的订单总数
            Integer orderCount = getOrderCount(begintime, endtime, null);
            //查询每日的有效订单数
            Integer validCount = getOrderCount(begintime, endtime, Orders.COMPLETED);
            //存放订单数
            orderList.add(orderCount);
            validList.add(validCount);
        }
        Integer totalOrderCount = orderList.stream().reduce(Integer::sum).get();
        Integer totalValidCount = validList.stream().reduce(Integer::sum).get();
        Double orderCompleteRate = 0.0;
        if(totalOrderCount != 0){
            orderCompleteRate = totalValidCount.doubleValue() / totalOrderCount;
        }
        OrderReportVO vo = OrderReportVO.builder()
                .orderCompletionRate(orderCompleteRate)
                .orderCountList(StringUtils.join(orderList,","))
                .validOrderCountList(StringUtils.join(validList,","))
                .dateList(StringUtils.join(dateList,","))
                .validOrderCount(totalValidCount)
                .totalOrderCount(totalOrderCount)
                .build();
        return vo;
    }

    /**
     * 统计销量top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesStatistics(LocalDate begin, LocalDate end) {
        LocalDateTime begintime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endtime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(begintime, endtime);
        List<String> collect = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String join = StringUtils.join(collect,",");
        List<Integer> collect1 = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String join1 = StringUtils.join(collect1,",");
        return SalesTop10ReportVO.builder()
                .nameList(join)
                .numberList(join1)
                .build();
    }

    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map<String,Object> map = new HashMap<>();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);
        Integer i = orderMapper.countByMap(map);
        return i;
    }
}
