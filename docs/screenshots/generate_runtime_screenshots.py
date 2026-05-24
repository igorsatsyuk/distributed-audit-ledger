import json
import os
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional
from urllib import request

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SCREEN_DIR = ROOT / "docs" / "screenshots"
RUNTIME_DIR = SCREEN_DIR / "runtime"
RUNTIME_DIR.mkdir(exist_ok=True)

W, H = 1366, 768
BG = (9, 15, 25)
FG = (236, 239, 244)
MUTED = (148, 163, 184)
ACCENT = (96, 165, 250)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def http_json(method: str, url: str, body=None, token: Optional[str] = None):
    payload = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url=url, data=payload, headers=headers, method=method)
    with request.urlopen(req, timeout=15) as resp:
        text = resp.read().decode("utf-8")
        return resp.status, text, json.loads(text)


def http_text(url: str):
    req = request.Request(url=url, method="GET")
    with request.urlopen(req, timeout=10) as resp:
        text = resp.read().decode("utf-8", errors="replace")
        return resp.status, text


def run_cmd(args: List[str]) -> str:
    result = subprocess.run(args, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def psql(sql: str) -> str:
    return run_cmd([
        "docker",
        "exec",
        "dal-postgres",
        "psql",
        "-U",
        "postgres",
        "-d",
        "audit_ledger",
        "-At",
        "-c",
        sql,
    ])


def font(size: int, bold: bool = False):
    candidates = [
        # Windows
        "C:/Windows/Fonts/consolab.ttf" if bold else "C:/Windows/Fonts/consola.ttf",
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        # Linux (DejaVu / Liberation)
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
        # macOS
        "/System/Library/Fonts/Supplemental/Menlo Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Menlo.ttc",
        "/Library/Fonts/Courier New Bold.ttf" if bold else "/Library/Fonts/Courier New.ttf",
    ]
    for c in candidates:
        p = Path(c)
        if p.exists():
            return ImageFont.truetype(str(p), size=size)
    return ImageFont.load_default()


def wrap_text(draw: ImageDraw.ImageDraw, text: str, fnt, width: int) -> List[str]:
    lines = []
    for raw in text.splitlines() or [""]:
        current = ""
        for part in raw.split(" "):
            candidate = part if not current else current + " " + part
            if draw.textlength(candidate, font=fnt) <= width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = part
        lines.append(current)
    return lines


def render(path: Path, title: str, subtitle: str, body: str):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    t_font = font(56, bold=True)
    s_font = font(30)
    b_font = font(27)

    x = 56
    y = 48
    d.text((x, y), title, fill=FG, font=t_font)
    y += 78
    d.text((x, y), subtitle, fill=MUTED, font=s_font)
    y += 66

    block = [
        f"Distributed Audit Ledger - Runtime Screenshot Pack",
        f"Generated: {now_iso()}",
        f"File: {path.name}",
        "",
    ]
    block.extend(wrap_text(d, body, b_font, W - 2 * x))

    for line in block:
        color = ACCENT if line.startswith("$") else FG
        d.text((x, y), line, fill=color, font=b_font)
        y += 36
        if y > H - 40:
            break

    img.save(path, format="PNG")


def pretty(obj) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=2)


def main():
    out = {}

    # 1) Auth token
    password = os.environ.get("DEMO_PASSWORD", "admin123!")
    _, _, auth_obj = http_json(
        "POST",
        "http://localhost:8081/auth/login",
        {"username": os.environ.get("DEMO_USERNAME", "admin"), "password": password},
    )
    token = auth_obj["accessToken"]
    out["auth"] = auth_obj

    # 2) Command accepted
    _, _, cmd_obj = http_json(
        "POST",
        "http://localhost:8081/commands/user/login",
        {"userId": "demo.user@example.com", "ipAddress": "127.0.0.1", "userAgent": "runtime-capture"},
        token=token,
    )
    event_id = cmd_obj.get("eventId")
    out["command"] = cmd_obj

    # 3) Poll query list
    logs = []
    for _ in range(20):
        _, _, logs_obj = http_json(
            "GET",
            "http://localhost:8084/api/audit-logs?userId=demo.user@example.com&limit=20",
            token=token,
        )
        logs = logs_obj if isinstance(logs_obj, list) else []
        if any(row.get("eventId") == event_id for row in logs):
            break
        time.sleep(1)

    if not logs:
        raise RuntimeError("No audit logs returned by query-service")

    selected = next((r for r in logs if r.get("eventId") == event_id), logs[0])
    audit_id = int(selected["id"])
    out["list"] = logs
    out["selectedAuditId"] = audit_id

    # 4) Integrity ON_CHAIN polling
    integrity_obj = None
    for _ in range(20):
        _, _, integrity_obj = http_json(
            "GET",
            f"http://localhost:8084/api/audit-logs/{audit_id}/integrity-check",
            token=token,
        )
        if integrity_obj.get("status") == "ON_CHAIN":
            break
        time.sleep(1)
    out["integrityOnChain"] = integrity_obj

    # 5) Tamper to force mismatch and then restore (always restore in finally)
    original_hash = psql(f"SELECT event_hash FROM audit.events WHERE id={audit_id};")
    tampered = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    psql(f"UPDATE audit.events SET event_hash='{tampered}' WHERE id={audit_id};")
    try:
        _, _, mismatch_obj = http_json(
            "GET",
            f"http://localhost:8084/api/audit-logs/{audit_id}/integrity-check",
            token=token,
        )
        out["integrityMismatch"] = mismatch_obj
    finally:
        if original_hash:
            psql(f"UPDATE audit.events SET event_hash='{original_hash}' WHERE id={audit_id};")

    # 6) Kafka topics
    topics = run_cmd([
        "docker",
        "exec",
        "dal-kafka",
        "kafka-topics",
        "--bootstrap-server",
        "localhost:9092",
        "--list",
    ]).splitlines()
    out["kafkaTopics"] = topics

    # 7) Postgres rows snapshot
    rows = psql("SELECT id,event_id,event_type,user_id,event_hash,created_at FROM audit.events ORDER BY id DESC LIMIT 5;")
    out["postgresRows"] = rows

    # 8) Health snapshot
    health = {}
    for port in [8081, 8082, 8083, 8084]:
        try:
            code, text = http_text(f"http://localhost:{port}/actuator/health")
            health[str(port)] = {"statusCode": code, "body": text}
        except Exception as ex:
            health[str(port)] = {"error": str(ex)}
    out["health"] = health

    # 9) Frontend quick probe
    frontend = {}
    try:
        code, text = http_text("http://localhost:4200")
        frontend["statusCode"] = code
        frontend["preview"] = text[:600]
    except Exception as ex:
        frontend["error"] = str(ex)
    out["frontend"] = frontend

    capture_path = Path(os.environ.get("CAPTURE_OUTPUT", str(RUNTIME_DIR / "capture.json")))
    # Redact auth tokens before persisting to avoid accidental credential disclosure
    safe_out = {**out, "auth": {k: "[REDACTED]" if "token" in k.lower() or "Token" in k else v
                                for k, v in out.get("auth", {}).items()}}
    capture_path.write_text(pretty(safe_out), encoding="utf-8")

    render(
        SCREEN_DIR / "01-command-accepted.png",
        "Command Accepted Response",
        "POST /commands/user/login -> 202 Accepted",
        "$ curl -X POST http://localhost:8081/commands/user/login ...\n" + pretty(cmd_obj),
    )
    render(
        SCREEN_DIR / "02-audit-logs-list.png",
        "Audit Logs List",
        "GET /api/audit-logs?userId=demo.user@example.com",
        "$ curl \"http://localhost:8084/api/audit-logs?...\" ...\n" + pretty(logs[:3]),
    )
    render(
        SCREEN_DIR / "03-integrity-on-chain.png",
        "Integrity Check: ON_CHAIN",
        f"GET /api/audit-logs/{audit_id}/integrity-check",
        "$ curl http://localhost:8084/api/audit-logs/{audit_id}/integrity-check ...\n" + pretty(integrity_obj),
    )
    render(
        SCREEN_DIR / "04-integrity-mismatch.png",
        "Integrity Check: MISMATCH",
        f"DB hash tampered for audit.events.id={audit_id}",
        "$ psql UPDATE audit.events SET event_hash='0123...';\n" + pretty(mismatch_obj),
    )
    render(
        SCREEN_DIR / "05-kafka-topics.png",
        "Kafka Topics",
        "docker exec dal-kafka kafka-topics --list",
        "$ docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list\n" + "\n".join(topics),
    )
    render(
        SCREEN_DIR / "06-postgres-audit-events.png",
        "PostgreSQL audit.events",
        "Latest rows from audit.events",
        "$ psql -c \"SELECT ... FROM audit.events ORDER BY id DESC LIMIT 5;\"\n" + rows,
    )
    render(
        SCREEN_DIR / "07-health-endpoints.png",
        "Service Health Endpoints",
        "GET /actuator/health for ports 8081-8084",
        pretty(health),
    )
    render(
        SCREEN_DIR / "08-angular-dashboard.png",
        "Angular Dashboard Probe",
        "Frontend endpoint check at http://localhost:4200",
        pretty(frontend),
    )


if __name__ == "__main__":
    main()

