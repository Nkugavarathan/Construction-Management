package com.constructflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * Money and quantities are serialized as JSON strings ("125000.00"), not numbers, so JavaScript
 * clients never parse them into binary floating point (ADR-0005). Requests may send either form.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    JacksonModule bigDecimalAsStringModule() {
        return new SimpleModule("BigDecimalAsString").addSerializer(BigDecimal.class, ToStringSerializer.instance);
    }
}
