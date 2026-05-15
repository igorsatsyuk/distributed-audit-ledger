package lt.satsyuk.distributed.audit.command.api;

import jakarta.validation.Valid;
import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

@RestController
public class CommandController {

    private final UserLoginCommandService userLoginCommandService;

    public CommandController(UserLoginCommandService userLoginCommandService) {
        this.userLoginCommandService = userLoginCommandService;
    }

    @PostMapping("/commands/user/login")
    public Mono<ResponseEntity<CommandResponse>> userLogin(
            @Valid @RequestBody UserLoginCommand command,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String requestUserAgent,
            ServerHttpRequest request
    ) {
        String requestIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;

        return userLoginCommandService.handleUserLogin(command, requestIp, requestUserAgent)
                .map(response -> ResponseEntity.accepted().body(response));
    }
}

