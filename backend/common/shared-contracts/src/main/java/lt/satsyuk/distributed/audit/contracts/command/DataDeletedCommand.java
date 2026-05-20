package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDeletedCommand {

	@NotBlank(message = "userId must not be blank")
	private String userId;

	@NotBlank(message = "entityType must not be blank")
	private String entityType;

	@NotBlank(message = "entityId must not be blank")
	private String entityId;

	private String reason;
}

