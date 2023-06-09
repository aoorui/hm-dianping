package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while(true){
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }

        private void handlerVoucherOrder(VoucherOrder voucherOrder) {
            //获取用户
            Long userId = voucherOrder.getUserId();
            //创建锁对象
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            //尝试获取锁
            boolean isLock = lock.tryLock();
            //判断是否获取锁
            if(!isLock) {
                //否，返回失败信息
                log.error("不允许重复下单");
                return;
            }
            try {
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                //释放锁
                lock.unlock();
            }
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断购买资格
        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
        }
        //将下单信息保存到消息队列
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }
    /*@Override
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
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //尝试获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁
        if(!isLock) {
            //否，返回失败信息
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }

    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        Long userId = voucherOrder.getUserId();

            //查询订单是否存在
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            //订单是否存在
            if (count > 0) {
                //存在，返回错误信息
                log.error("不允许重复下单");
                return;
            }
            //5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where voucher_id = ? and stock > 0
                    .update();
            if (!success) {
                //扣减失败
                log.error("库存不足");
                return;
            }
            save(voucherOrder);


    }
}
