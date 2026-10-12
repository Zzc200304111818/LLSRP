package com.zzc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.zzc.mapper")
@SpringBootApplication
public class LLSRPApplication {

    public static void main(String[] args) {
        SpringApplication.run(LLSRPApplication.class, args);
    }

}
