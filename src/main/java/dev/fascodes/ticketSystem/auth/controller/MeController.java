package dev.fascodes.ticketSystem.auth.controller;

import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MeController {

    public record MeResponse(UUID id, String email, List<String> roles) {}

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user, Authentication auth) {
        return new MeResponse(user.id(), user.email(),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
