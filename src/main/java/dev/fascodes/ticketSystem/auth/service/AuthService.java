package dev.fascodes.ticketSystem.auth.service;

import dev.fascodes.ticketSystem.auth.dto.LoginRequest;
import dev.fascodes.ticketSystem.auth.dto.RegisterRequest;
import dev.fascodes.ticketSystem.auth.exception.EmailAlreadyUsedException;
import dev.fascodes.ticketSystem.auth.exception.TokenReuseException;
import dev.fascodes.ticketSystem.auth.jwt.JwtService;
import dev.fascodes.ticketSystem.auth.refresh.RefreshTokenService;
import dev.fascodes.ticketSystem.security.userdetails.CustomUserDetails;
import dev.fascodes.ticketSystem.user.entity.Role;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.user.repository.RoleRepository;
import dev.fascodes.ticketSystem.user.repository.UserRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public record Tokens(String accessToken, String refreshToken) {}

    @Transactional
    public void register(RegisterRequest req) {
        String email = normalize(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEnabled(true); // TODO: Later add email verification not default account enable/email verification
        user.getRoles().add(roleRepository.findByName("ROLE_USER").orElseThrow());
        userRepository.save(user);
    }

    @Transactional
    public Tokens login(LoginRequest req, String userAgent, String ip) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalize(req.email()), req.password()));

        CustomUserDetails details = (CustomUserDetails) auth.getPrincipal();
        User user = userRepository.getReferenceById(details.getId());

        String access = jwtService.generateAccessToken(
                details.getId(), details.getUsername(), roleNames(auth.getAuthorities()));
        String refresh = refreshTokenService.issue(user, userAgent, ip);
        return new Tokens(access, refresh);
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public Tokens refresh(String rawRefreshToken, String userAgent, String ip) {
        var rotation = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);
        User user = rotation.user();
        String access = jwtService.generateAccessToken(
                user.getId(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).toList());
        return new Tokens(access, rotation.newRawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static List<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
