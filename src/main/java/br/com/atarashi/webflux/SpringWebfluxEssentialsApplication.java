package br.com.atarashi.webflux;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class SpringWebfluxEssentialsApplication {

	static {
		BlockHound.install(builder -> builder.allowBlockingCallsInside("java.util.UUID", "randomUUID")
				.allowBlockingCallsInside("java.io.InputStream", "readNBytes")
				.allowBlockingCallsInside("java.io.FilterInputStream", "read")
		);
	}

	public static void main(String[] args) {
//		System.out.println(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("devdojo"));
		SpringApplication.run(SpringWebfluxEssentialsApplication.class, args);
	}
}
