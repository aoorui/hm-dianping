package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(now)) {
            //否，返回异常结果
            return Result.fail("活动未开始！");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(now)) {
            //是，返回异常结果
            return Result.fail("活动已结束！");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            //否，返回异常结果
            return Result.fail("库存不足！");
        }

        return createVoucherOrder(voucherId);
    }

    @Transactional
    public synchronized Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString()) {
            //查询订单是否存在
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //订单是否存在
            if (count > 0) {
                //存在，返回错误信息
                return Result.fail("此商品限购一次！");
            }
            //5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where voucher_id = ? and stock > 0
                    .update();
            if (!success) {
                //扣减失败
                return Result.fail("库存不足! ");
            }
            //6.创建订单
            VoucherOrder order = new VoucherOrder();
            //订单id
            long orderId = redisIdWorker.nextId("order");
            order.setId(orderId);
            //用户id
            order.setUserId(userId);
            //代金券id
            order.setVoucherId(voucherId);
            save(order);
            //7.返回订单id
            return Result.ok(orderId);

        }
    }
}