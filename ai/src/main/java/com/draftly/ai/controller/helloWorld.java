package com.draftly.ai.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class helloWorld {



    
  @GetMapping("/hello")
public String sayHello(@AuthenticationPrincipal OAuth2User user) {
    String email = user.getAttribute("email");
    String name = user.getAttribute("name");

    return "Hello " + name + " (" + email + ")";
}
}
