package com.premiergroup.ad_metrics_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
public class AdMetricsHubApplication {

//    public static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    public static void main(String[] args) {
        SpringApplication.run(AdMetricsHubApplication.class, args);
    }
}
