package com.liubinrui;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.liubinrui.mapper")
public class LltukuApplication {

    public static void main(String[] args) {
        SpringApplication.run(LltukuApplication.class, args);
    }

}
