package dev.fascodes.ticketSystem.auth.user;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // TODO: normalizacja emaila, sprawdzenie existsByEmail,
        //       hash hasła, przypisanie domyślnej roli, zapis użytkownika
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // TODO: authenticationManager.authenticate(...),
        //       aktualizacja lastloginAt / failedLoginAttempts, wydanie tokenów
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
