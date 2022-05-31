package com.itheima.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.itheima.constant.MessageConstant;
import com.itheima.dao.MemberDao;
import com.itheima.dao.OrderDao;
import com.itheima.dao.OrderSettingDao;
import com.itheima.entity.Result;
import com.itheima.pojo.Member;
import com.itheima.pojo.Order;
import com.itheima.pojo.OrderSetting;
import com.itheima.service.OrderService;
import com.itheima.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 体检预约服务
 */
@Service(interfaceClass = OrderService.class)
@Transactional
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderSettingDao orderSettingDao;
    @Autowired
    private MemberDao memberDao;
    @Autowired
    private OrderDao orderDao;
    //体检预约
    public Result order(Map map) throws Exception {
        //1、检查用户选择的预约日期是否已经进行了预约设置
        String orderDate = (String) map.get("orderDate");//预约日期
        OrderSetting orderSetting = orderSettingDao.findByOrderDate(DateUtils.parseString2Date(orderDate));
        if (orderSetting == null) {
            //所选日期没有进行预约设置
            return new Result(false, MessageConstant.SELECTED_DATE_CANNOT_ORDER);
        }
        //2、检查用户所选的预约日期是否已经约满，如果约满则无法预约
        if (orderSetting.getNumber() <= orderSetting.getReservations()){
            //已经约满,无法预约
            return new Result(false, MessageConstant.ORDER_FULL);
        }
        //3、检查用户是否重复预约（同一个在同一天预约了同一个套餐）,如果重复预约则无法预约
        String telephone = (String) map.get("telephone");
        Member member = memberDao.findByTelephone(telephone);
        if (member != null) {
            //判断是否重复预约
            Integer memberId = member.getId();//会员ID
            Date order_Date = DateUtils.parseString2Date(orderDate);//预约日期
            String setmealId = (String) map.get("setmealId");//套餐
            Order order = new Order(memberId, order_Date, Integer.parseInt(setmealId));
            //根据条件进行查询
            List<Order> list = orderDao.findByCondition(order);
            if (list != null && list.size() > 0) {
                //说明用户在重复预约,无法再次预约
                return new Result(false, MessageConstant.HAS_ORDERED);
            }
        } else {
            //4、判断当前用户是否为会员，如果为会员则直接完成预约，否则自动完成注册并预约
            member = new Member();
            member.setPhoneNumber(telephone);
            member.setName((String) map.get("name"));
            member.setIdCard((String) map.get("idCard"));
            member.setSex((String) map.get("sex"));
            member.setRegTime(new Date());
            memberDao.add(member);//自动完成注册
        }
        //5、预约成功，更新当日的已预约人数
        Order order = new Order();
        order.setMemberId(member.getId());//设置会员ID
        order.setOrderDate(DateUtils.parseString2Date(orderDate));//预约日期
        order.setOrderType((String) map.get("orderType"));//预约类型
        order.setOrderStatus(Order.ORDERSTATUS_NO);//到诊状态
        order.setSetmealId(Integer.parseInt((String) map.get("setmealId")));//设置套餐ID
        orderDao.add(order);

        orderSetting.setReservations(orderSetting.getReservations() + 1);//设置已预约人数加一
        orderSettingDao.editReservationsByOrderDate(orderSetting);

        return new Result(true, MessageConstant.ORDERSETTING_SUCCESS, order.getId());
    }

    //根据预约id查询预约信息(体检人姓名，预约日期，套餐名称，预约类型)
    public Map findById(Integer id) throws Exception {
        Map map = orderDao.findById4Detail(id);
        if (map != null) {
            //处理日期格式
            Date orderDate = (Date) map.get("orderDate");
            map.put("orderDate", DateUtils.parseDate2String(orderDate));
        }
        return map;
    }
}
