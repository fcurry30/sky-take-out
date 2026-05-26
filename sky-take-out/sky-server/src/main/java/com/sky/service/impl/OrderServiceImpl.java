package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO){
        //1.处理各种业务异常（地址簿为空，购物车为空）——其实小程序前端做了校验，但为了代码的稳定性，还是需要添加。
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //检查用户地址是否超限制
        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> list = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if(list == null || list.size() == 0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //2.向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());//从地址簿中获取电话
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() +addressBook.getDistrictName()
                + addressBook.getDetail());
        orderMapper.insert(orders);
        //向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart shoppingCart : list) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(shoppingCart,orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);
        //清空当前用户的购物车数据
        shoppingCartMapper.delete(userId);
        //5.返回VO对象
        OrderSubmitVO build = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .build();
        return build;
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
//
//        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
//        vo.setPackageStr(jsonObject.getString("package"));
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        paySuccess(orderNumber);
        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setNonceStr("");
        vo.setPackageStr("");
        vo.setPaySign("");
        vo.setSignType("");
        vo.setTimeStamp("");
        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 历史订单查询
     */
    public PageResult pageQuery4HistoryOrder(int page, int pageSize, Integer status){
        //设置分页
        PageHelper.startPage(page,pageSize);
        //分页查询
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        Page<Orders> pageresult =  orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if(pageresult != null && pageresult.size() > 0){
            for (Orders orders : pageresult) {
                Long ordersId = orders.getId();
                List<OrderDetail> byOrderId = orderDetailMapper.getByOrderId(ordersId);
                OrderVO orderVO = new OrderVO();
                orderVO.setOrderDetailList(byOrderId);
                BeanUtils.copyProperties(orders,orderVO);//orderVO extends了orders
                list.add(orderVO);
            }
        }
        //order_time &gt;= #{beginTime}为 order > begin
        return new PageResult(pageresult.getTotal(),list);
    }


    public OrderVO orderDetailCheck(Long id){
        OrderVO orderVO = new OrderVO();
        //分别查询获得orders和orderdetail
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 订单取消
     */
    public void orderCancelById(Long id){
        Orders orders = orderMapper.getById(id);
        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = orders.getStatus();
        if(status > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //为了避免不必要的更新，new一个新的对象更新
        Orders newOrders = new Orders();
        newOrders.setId(id);
        if(status == Orders.TO_BE_CONFIRMED){
            //要退款,退款逻辑不进行书写
            newOrders.setPayStatus(Orders.REFUND);
        }
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelReason("用户自行取消");
        newOrders.setCancelTime(LocalDateTime.now());
        orderMapper.update(newOrders);
    }

    /**
     * 再来一单
     * @param id
     */
    public void orderAgain(Long id){
        //再来一单实际上往购物车加新的数据
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.delete(userId);//先把当前购物车清空
        //要获取订单明细表
        List<OrderDetail> list = orderDetailMapper.getByOrderId(id);
        for (OrderDetail orderDetail : list) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail,shoppingCart,"id");//要忽略id这个主键值
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
        //下为批量插入数据的代码，官方答案代码
//        List<ShoppingCart> id1 = list.stream().map(x -> {
//            ShoppingCart shoppingCart = new ShoppingCart();
//            BeanUtils.copyProperties(x, shoppingCart, "id");
//            shoppingCart.setUserId(userId);
//            shoppingCart.setCreateTime(LocalDateTime.now());
//            return shoppingCart;
//        }).collect(Collectors.toList());
//        shoppingCartMapper.insertBatch(id1);
    }

    /**
     * 管理端订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = order2Vo(page);
        return new PageResult(page.getTotal(),list);
    }



    /**
     * 把orders转换成VO对象给管理端前端展示
     * @param page
     * @return
     */
    private List<OrderVO> order2Vo(Page<Orders> page){
        List<OrderVO> list = new ArrayList<>();
        List<Orders> result = page.getResult();
        if(result != null && result.size() > 0){
            for (Orders orders : result) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                String orderDishesStr = getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishesStr);
                list.add(orderVO);
            }
        }
        return list;
    }

    /**
     * 根据orders获取orderdetail从而拼接菜品信息，生成字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders){
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        List<String> collect = orderDetailList.stream().map(x -> {
            String result = x.getName() + "*" + x.getNumber() + ";";
            return result;
        }).collect(Collectors.toList());
        return String.join("",collect);
    }

    /**
     * 获取订单状态的数量，封装到VO里面，返回给前端
     * @return
     */
    public OrderStatisticsVO getStatistics() {
        Integer DIP = orderMapper.getNumber(Orders.DELIVERY_IN_PROGRESS);
        Integer TBC = orderMapper.getNumber(Orders.TO_BE_CONFIRMED);
        Integer C = orderMapper.getNumber(Orders.CONFIRMED);
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(C);
        orderStatisticsVO.setToBeConfirmed(TBC);
        orderStatisticsVO.setDeliveryInProgress(DIP);
        return orderStatisticsVO;
    }

    /**
     * 通过id查询orders和orderid
     * @return
     */
    public OrderVO getDetailsById(Long id) {
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    public void setConfirm(OrdersConfirmDTO ordersConfirmDTO) {
        Long orderId = ordersConfirmDTO.getId();
        Orders orders = Orders.builder()
                .id(orderId)
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 商家拒单
     * @param ordersRejectionDTO
     */
    public void setReject(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());
        Integer status = orders.getStatus();
        if(!status.equals(Orders.TO_BE_CONFIRMED) || orders == null){//判断订单是否存在或者状态错误
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orders.setStatus(Orders.CANCELLED);
        if(orders.getPayStatus().equals(Orders.PAID)){
            //退款逻辑不书写
            log.info("申请退款，退款成功");
            orders.setPayStatus(Orders.REFUND);
        }
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    public void setCancel(OrdersCancelDTO ordersCancelDTO){
        Orders orders = orderMapper.getById(ordersCancelDTO.getId());
        Integer payStatus = orders.getPayStatus();
        if(payStatus == 1){
            log.info("申请退款，退款成功");
            orders.setStatus(Orders.REFUND);
        }
        Orders build = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .build();
        orderMapper.update(build);
    }

    /**
     * 商家派单功能
     * @param id
     */
    public void setDelivery(Long id){
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.CONFIRMED)){//需要先判空再获取数据
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orders1);
    }

    /**
     * 商户端完成派送
     * @param id
     */
    public void setComplete(Long id){
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.COMPLETED);
        orders1.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    /**
     * 检查派送范围
     * @param address
     */
    private void checkOutOfRange(String address){
        Map<String,String> map = new HashMap<>();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }
        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;
        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        JSONObject jsonObject1 = JSON.parseObject(userCoordinate);
        if(!jsonObject1.getString("status").equals("0")){
            throw  new OrderBusinessException("收货地址解析失败");
        }
        JSONObject location2 = jsonObject1.getJSONObject("result").getJSONObject("location");
        String lat1 = location2.getString("lat");
        String lng1 = location2.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat1 + "," + lng1;
        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("step_info","0");
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);
        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }
        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }

    }

}
