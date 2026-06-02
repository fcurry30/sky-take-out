package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {
    /**
     * 根据日期统计营业额数据
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin,LocalDate end);

    UserReportVO getUserStatistics(LocalDate begin,LocalDate end);

    /**
     * 根据日期统计订单数据
     * @param begin
     * @param end
     * @return
     */
    OrderReportVO getOrderStatistics(LocalDate begin,LocalDate end);

    /**
     * 根据日期统计销量数据
     * @param begin
     * @param end
     * @return
     */
    SalesTop10ReportVO getSalesStatistics(LocalDate begin, LocalDate end);
}
