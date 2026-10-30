package dev.fascodes.ticketSystem.auth.service;

import dev.fascodes.ticketSystem.security.userdetails.CustomUserDetails;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventsListener {

    private final LoginAttemptService loginAttempts;

    public AuthenticationEventsListener(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        loginAttempts.recordFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CustomUserDetails details) {
            loginAttempts.recordSuccess(details.getId());
        }
    }
}
