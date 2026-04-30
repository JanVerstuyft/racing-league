package be.jabapage.racingleague.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class CollectorApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(CollectorApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
