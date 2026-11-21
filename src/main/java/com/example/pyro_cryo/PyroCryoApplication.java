package com.example.pyro_cryo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PyroCryoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PyroCryoApplication.class, args);
	}

}
