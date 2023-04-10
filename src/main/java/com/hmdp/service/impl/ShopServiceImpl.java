package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        //缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,20L,TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺信息不存在!");
        }
        //6.返回
        return Result.ok(shop);
    }

    /*private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    *//**
     * 逻辑过期
     * @param id
     * @return
     *//*
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(shopJson)){
            //2.1未命中，返回空
            return null;
        }
        //2.2命中，将shopJson反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //3.1未过期，返回店铺信息
            return shop;
        }
        //3.2已过期，获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        //4.判断是否获取锁
        if (tryLock(lockKey)) {
            //4.1是，开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        //4.2否，返回旧的商铺信息
        return shop;
    }*/

    /**
     * 缓存击穿
     * @param id
     * @return
     *//*
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.1存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if(shopJson != null){
            //返回一个错误信息
            return null;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        //3.不存在，尝试获取互斥锁
        Shop shop = null;
        try {
            if(!tryLock(lockKey)){
                //3.1获取互斥锁失败，休眠后再次尝试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //3.2获取互斥锁成功，根据id查询数据库
            shop = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            //4.店铺信息是否存在
            if (shop == null) {
                //4.1不存在，返回错误信息
                //将空值写入redis缓存
                stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.2存在，写入redis缓存
            stringRedisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //5.释放互斥锁
            unLock(lockKey);
        }
        //6.返回
        return shop;
    }*/

    /**
     * 缓存穿透
     * @param id
     * @return
     *//*
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.1存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if(shopJson != null){
            //返回一个错误信息
            return null;
        }
        //2.2不存在，根据id查询数据库
        Shop shop = getById(id);
        //3.数据库中是否存在
        if(shop == null){
            //3.1不存在，将空值写入redis缓存，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return shop;
        }
        //3.2存在，写入redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //6.返回
        return shop;
    }*/

    /**
     * 获取互斥锁
     * @param key
     * @return
     *//*
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    *//**
     * 释放互斥锁
     * @param key
     *//*
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }*/



    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺id不能为空！");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
