import html
import os

FAILURE_STATES = {"failure", "cancelled", "timed_out", "action_required"}
SUCCESS_STATES = {"success", "skipped", "neutral"}

JOB_RESULT_FIELDS = (
	("backend", "BACKEND_RESULT"),
	("frontend", "FRONTEND_RESULT"),
	("blockchain", "BLOCKCHAIN_RESULT"),
	("sonarqube-backend", "SONAR_BACKEND_RESULT"),
	("sonarqube-frontend", "SONAR_FRONTEND_RESULT"),
	("sonarqube-blockchain", "SONAR_BLOCKCHAIN_RESULT"),
)


def normalize(value: str) -> str:
	return (value or "unknown").lower()


def status_icon(status: str) -> str:
	if status == "success":
		return "✅"
	if status in FAILURE_STATES:
		return "❌"
	if status in {"skipped", "neutral"}:
		return "⏭️"
	return "❓"


def overall_status() -> str:
	statuses = [normalize(os.environ.get(env_name, "unknown")) for _, env_name in JOB_RESULT_FIELDS]
	if any(status in FAILURE_STATES for status in statuses):
		return "failure"
	if all(status in SUCCESS_STATES for status in statuses):
		return "success"
	return "unknown"


def esc(value: str) -> str:
	return html.escape(str(value), quote=True)


def compose_message() -> str:
	current_overall = overall_status()
	lines = [
		"distributed-audit-ledger CI finished",
		"",
		f"{status_icon(current_overall)} <b>Status:</b> {esc(current_overall)}",
		f"<b>Branch:</b> {esc(os.environ.get('GITHUB_REF_NAME', ''))}",
		f"<b>Commit:</b> {esc(os.environ.get('GITHUB_SHA', ''))}",
		f"<b>Actor:</b> {esc(os.environ.get('GITHUB_ACTOR', ''))}",
		f"<b>Workflow:</b> {esc(os.environ.get('GITHUB_WORKFLOW', ''))}",
		"",
		"<b>Job results</b>",
	]

	for job_name, env_name in JOB_RESULT_FIELDS:
		status = normalize(os.environ.get(env_name, "unknown"))
		lines.append(f"- {status_icon(status)} {esc(job_name)}: {esc(status)}")

	lines.append("")
	lines.append(
		f"Link: {esc(os.environ.get('GITHUB_SERVER_URL', ''))}/{esc(os.environ.get('GITHUB_REPOSITORY', ''))}/actions/runs/{esc(os.environ.get('GITHUB_RUN_ID', ''))}"
	)

	return "\n".join(lines)


def main() -> None:
	message = compose_message()
	output_path = os.environ["GITHUB_OUTPUT"]
	with open(output_path, "a", encoding="utf-8") as output_file:
		output_file.write("message<<EOF\n")
		output_file.write(message)
		output_file.write("\nEOF\n")


if __name__ == "__main__":
	main()

