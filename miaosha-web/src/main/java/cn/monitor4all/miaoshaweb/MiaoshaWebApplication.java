package cn.monitor4all.miaoshaweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages="cn.monitor4all")
@EnableScheduling
public class MiaoshaWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiaoshaWebApplication.class, args);
    }

}
