package com.financebot;

import com.financebot.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TelegramProperties.class)
public class FinanceBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceBotApplication.class, args);
    }
}
