package lt.satsyuk.distributed.audit.command.api;

import jakarta.validation.Valid;
import lt.satsyuk.distributed.audit.command.service.AdditionalCommandService;
import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import lt.satsyuk.distributed.audit.contracts.command.DataDeletedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityCreatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityUpdatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
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
    private final AdditionalCommandService additionalCommandService;

    public CommandController(
            UserLoginCommandService userLoginCommandService,
            AdditionalCommandService additionalCommandService
    ) {
        this.userLoginCommandService = userLoginCommandService;
        this.additionalCommandService = additionalCommandService;
    }

    @PostMapping("/commands/user/login")
    public Mono<ResponseEntity<CommandResponse>> userLogin(
            @Valid @RequestBody UserLoginCommand command,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String requestUserAgent,
            ServerHttpRequest request
    ) {
        String requestIp = null;
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            requestIp = request.getRemoteAddress().getAddress().getHostAddress();
        }

        return userLoginCommandService.handleUserLogin(command, requestIp, requestUserAgent)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    @PostMapping("/commands/user/profile-change")
    public Mono<ResponseEntity<CommandResponse>> userProfileChange(@Valid @RequestBody UserProfileChangeCommand command) {
        return additionalCommandService.handleUserProfileChange(command)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    @PostMapping("/commands/entity/create")
    public Mono<ResponseEntity<CommandResponse>> entityCreated(@Valid @RequestBody EntityCreatedCommand command) {
        return additionalCommandService.handleEntityCreated(command)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    @PostMapping("/commands/entity/update")
    public Mono<ResponseEntity<CommandResponse>> entityUpdated(@Valid @RequestBody EntityUpdatedCommand command) {
        return additionalCommandService.handleEntityUpdated(command)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    @PostMapping("/commands/data/delete")
    public Mono<ResponseEntity<CommandResponse>> dataDeleted(@Valid @RequestBody DataDeletedCommand command) {
        return additionalCommandService.handleDataDeleted(command)
                .map(response -> ResponseEntity.accepted().body(response));
    }
}

