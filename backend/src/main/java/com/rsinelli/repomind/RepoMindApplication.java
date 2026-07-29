package com.rsinelli.repomind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RepoMindApplication {

  public static void main(String[] args) {
    SpringApplication.run(RepoMindApplication.class, args);
  }
}
