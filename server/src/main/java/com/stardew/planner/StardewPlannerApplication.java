package com.stardew.planner;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.stardew.planner.repository")
public class StardewPlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(StardewPlannerApplication.class, args);
    }
}
