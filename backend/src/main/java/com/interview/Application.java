package com.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableAsync
public class Application {

    public static void main(String[] args) {
        // 从多个可能的位置加载 .env 文件
        String[] possibleDirs = {
            System.getProperty("user.dir"),
            System.getProperty("user.dir") + "/../",
            System.getProperty("user.dir") + "/../../",
            "."
        };

        for (String dir : possibleDirs) {
            java.io.File dotenvFile = new java.io.File(dir, ".env");
            if (dotenvFile.exists()) {
                System.out.println("找到 .env 文件: " + dotenvFile.getAbsolutePath());
                Dotenv dotenv = Dotenv.configure()
                        .directory(dir)
                        .ignoreIfMissing()
                        .load();

                dotenv.entries().forEach(entry -> {
                    if (System.getProperty(entry.getKey()) == null) {
                        System.setProperty(entry.getKey(), entry.getValue());
                    }
                });
                break;
            }
        }

        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 JSR310 模块以支持 LocalDateTime 序列化
        mapper.registerModule(new JavaTimeModule());
        // 使用 ISO 日期字符串格式而非时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
