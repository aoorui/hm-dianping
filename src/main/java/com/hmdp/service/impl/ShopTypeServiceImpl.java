package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopTypeList() {

        /*// 1.在redis中间查询
        String key = CACHE_SHOP_TYPE_KEY;
        List<String> shopTypeList = new ArrayList<>();
        // range()中的 -1 表示最后一位
        // shopTypeList中存放的数据是[{...},{...},{...}...] 一个列表中有一个个json对象
        shopTypeList = stringRedisTemplate.opsForList().range(key,0,-1);

        // 2.判断是否缓存中了
        // 3.中了返回 （判断redis不空）
        if(!shopTypeList.isEmpty()) {
            List<ShopType> typeList = new ArrayList<>();
            for (String s : shopTypeList) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                // shopType 是一个对象
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }

        // 4.redis未命中数据，从数据库中获取，根据ShopType对象的sort属性排序后存入typeList
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 5.数据库中如果不存在直接返回错误
        if(typeList.isEmpty()){
            return Result.fail("不存在分类");
        }

        for(ShopType shopType : typeList){
            String s = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(s);
        }
        // 6.存在直接添加进缓存
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);
        return Result.ok(typeList);*/


        String key = CACHE_SHOP_TYPE_KEY;
        //1.从redis缓存中获取商铺类型列表
        List<String> shopTypeList = new ArrayList<>();
        shopTypeList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.判断商铺类型列表是否存在
        if(!shopTypeList.isEmpty()){
            //2.1存在，直接返回
            List<ShopType> typeList = new ArrayList<>();
            for (String s : shopTypeList) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //2.2不存在，从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //3.判断是否存在
        if(typeList.isEmpty()){
            //3.1不存在，返回错误信息
            return Result.fail("商铺类型不存在！");
        }
        //3.2存在，写入redis缓存
        for (ShopType s : typeList) {
            String str = JSONUtil.toJsonStr(s);
            shopTypeList.add(str);
        }
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);
        //返回
        return Result.ok(typeList);

    }
}
