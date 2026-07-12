package dev.cairn.api.config;

import dev.cairn.api.permission.DefaultPermissionResolver;
import dev.cairn.api.permission.GrantLookup;
import dev.cairn.api.permission.PermissionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionConfig {

    @Bean
    public PermissionResolver permissionResolver(GrantLookup grantLookup) {
        return new DefaultPermissionResolver(grantLookup);
    }
}
