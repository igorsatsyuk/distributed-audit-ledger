package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityCreatedCommand {

	@NotBlank(message = "userId must not be blank")
	private String userId;

	@NotBlank(message = "entityType must not be blank")
	private String entityType;

	@NotBlank(message = "entityId must not be blank")
	private String entityId;

	@NotNull(message = "entityData must not be null")
	private Map<String, Object> entityData;
}

