import json


def parse_sse_events(raw: str) -> list[tuple[str, object]]:
    if raw is None:
        return []

    text = str(raw)
    buffer = text.replace("\r\n", "\n")
    blocks = buffer.split("\n\n")

    events: list[tuple[str, object]] = []
    for block in blocks:
        lines = [line.strip("\r") for line in block.split("\n") if line.strip("\r")]
        if not lines:
            continue

        event_name = None
        data_lines = []
        for line in lines:
            if line.startswith("event:"):
                event_name = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].lstrip())

        if not event_name or not data_lines:
            continue

        payload_text = "\n".join(data_lines)
        try:
            payload = json.loads(payload_text)
        except Exception:
            payload = payload_text

        events.append((event_name, payload))

    return events
