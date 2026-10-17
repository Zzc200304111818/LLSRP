package com.zzc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.zzc.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class LLSRPApplication {

    public static void main(String[] args) {
        SpringApplication.run(LLSRPApplication.class, args);
    }

}
