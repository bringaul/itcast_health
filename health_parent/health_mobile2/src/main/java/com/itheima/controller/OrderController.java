package com.itheima.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.itheima.constant.MessageConstant;
import com.itheima.constant.RedisMessageConstant;
import com.itheima.entity.Result;
import com.itheima.pojo.Order;
import com.itheima.service.OrderService;
import com.itheima.utils.SMSUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPool;

import java.util.Map;

/**
 * 体检预约处理
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private JedisPool jedisPool;
    @Reference
    private OrderService orderService;

    //在线体检预约
    @RequestMapping("/submit")
    public Result submit(@RequestBody Map map) {
        //从redis中获取保存的验证码
        String telephone = (String) map.get("telephone");
        String validateCodeInRedis = jedisPool.getResource().get(telephone + RedisMessageConstant.SENDTYPE_ORDER);
        String validateCode = (String) map.get("validateCode");
        //将用户输入的验证码和Redis中的进行比对
        if (validateCodeInRedis != null && validateCode != null && validateCode.equals(validateCodeInRedis)) {
            //比对成功，调用服务
            map.put("orderType", Order.ORDERTYPE_WEIXIN);//设置预约类型,微信预约和电话预约
            Result result = null;
            try {
                result = orderService.order(map);//通过dubbo远程调用服务实现在线预约处理
            } catch (Exception e) {
                e.printStackTrace();
                return result;
            }
            if (result.isFlag()) {
                //预约成功，可以为用户发送信息
                try {
                    SMSUtils.sendShortMessage(SMSUtils.ORDER_NOTICE, telephone, (String) map.get("orderDate"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        } else {
            //比对失败返回结果给页面
            return new Result(false, MessageConstant.VALIDATECODE_ERROR);
        }
    }

    //根据预约id，查询预约信息
    @RequestMapping("/findById")
    public Result findById(Integer id) {
        try {
            Map map = orderService.findById(id);
            return new Result(true, MessageConstant.QUERY_ORDER_SUCCESS, map);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false, MessageConstant.QUERY_ORDER_FAIL);
        }
    }
}
