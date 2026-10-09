package com.bankflow.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/security")
public class SecurityDebugController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", authentication.getName());
        map.put("authenticated", authentication.isAuthenticated());
        map.put("authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        map.put("principalClass", authentication.getPrincipal().getClass().getName());
        return map;
    }

    @GetMapping("/public")
    public String publicMethod() {
        return "public";
    }

}
