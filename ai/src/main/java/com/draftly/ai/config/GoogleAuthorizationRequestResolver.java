package com.draftly.ai.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.HttpServletRequest;


public class GoogleAuthorizationRequestResolver implements  OAuth2AuthorizationRequestResolver{

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
    }



    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        // TODO Auto-generated method stub
        return customize(defaultResolver.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        // TODO Auto-generated method stub
       
        return customize(defaultResolver.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request) {
      if (request == null) return null;

        Map<String, Object> params = new HashMap<>(request.getAdditionalParameters());
        params.put("access_type", "offline");
        params.put("prompt", "consent");

        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(params)
                .build();
    }
    
}
