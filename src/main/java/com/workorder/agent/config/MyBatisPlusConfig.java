package com.workorder.agent.config;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.plugins.*;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.springframework.context.annotation.*;

@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
