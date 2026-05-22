package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
public class UserLoginCommandService {

    private final AuditCommandPublisher auditCommandPublisher;

    public UserLoginCommandService(AuditCommandPublisher auditCommandPublisher) {
        this.auditCommandPublisher = auditCommandPublisher;
    }

    public Mono<CommandResponse> handleUserLogin(UserLoginCommand command, String requestIp, String requestUserAgent) {
        // Prefer server-derived metadata over client-supplied body values to prevent spoofing.
        String effectiveIp = StringUtils.hasText(requestIp) ? requestIp : command.getIpAddress();
        String effectiveUserAgent = StringUtils.hasText(requestUserAgent)
                ? requestUserAgent
                : command.getUserAgent();

        UserLoggedInEvent event = UserLoggedInEvent.of(command.getUserId(), effectiveIp, effectiveUserAgent);
        return auditCommandPublisher.publish(event);
    }
}

