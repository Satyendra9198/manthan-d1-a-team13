package com.returnright;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReturnRightApplication {

  public static void main(String[] args) {
    // Store and compare instants in UTC; business rules use Asia/Kolkata explicitly.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(ReturnRightApplication.class, args);
  }
}
