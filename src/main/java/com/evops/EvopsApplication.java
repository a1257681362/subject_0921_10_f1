package com.evops;

import org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 不使用 Shiro 注解鉴权（权限在过滤器链统一控制），排除其自动代理，避免 JDK 代理导致具体类注入失败
@SpringBootApplication(exclude = ShiroAnnotationProcessorAutoConfiguration.class)
@MapperScan("com.evops.**.mapper")
public class EvopsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvopsApplication.class, args);
    }
}
