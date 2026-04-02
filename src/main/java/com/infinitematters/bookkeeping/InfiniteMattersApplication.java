package com.infinitematters.bookkeeping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InfiniteMattersApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfiniteMattersApplication.class, args);
    }

}
