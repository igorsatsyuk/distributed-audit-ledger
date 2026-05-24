package lt.satsyuk.distributed.audit.command.api;

import jakarta.validation.Valid;
import lt.satsyuk.distributed.audit.command.service.AuthenticationService;
import lt.satsyuk.distributed.audit.contracts.auth.AuthLoginRequest;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public authentication endpoint for obtaining JWT bearer tokens.
 */
@RestController
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/auth/login")
    public Mono<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return authenticationService.login(request);
    }
}

