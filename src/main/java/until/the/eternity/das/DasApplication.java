package until.the.eternity.das;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DasApplication {

  public static void main(String[] args) {
    SpringApplication.run(DasApplication.class, args);
  }

}
