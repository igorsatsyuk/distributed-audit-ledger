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
public class UserProfileChangeCommand {

	@NotBlank(message = "userId must not be blank")
	private String userId;

	@NotNull(message = "changedFields must not be null")
	private Map<String, Object> changedFields;
}

