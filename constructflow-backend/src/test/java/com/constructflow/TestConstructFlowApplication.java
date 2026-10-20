package com.constructflow;

import org.springframework.boot.SpringApplication;

public class TestConstructFlowApplication {

	public static void main(String[] args) {
		SpringApplication.from(ConstructFlowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
