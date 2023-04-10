package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefrashTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author jinrui
 * @create 2023-03-07-21:46
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                    "/user/login",
                    "/user/code",
                    "/blog/hot",
                    "/upload/**",
                    "/shop-type/**",
                    "/voucher/**",
                    "/shop/**"
                ).order(1);
        //刷新token的拦截器
        registry.addInterceptor(new RefrashTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
