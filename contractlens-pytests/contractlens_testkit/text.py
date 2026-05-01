import html
import re


def highlight_html(text: str, keywords: list[str] | None) -> str:
    escaped = html.escape("" if text is None else str(text), quote=True)
    if not keywords:
        return escaped

    cleaned = [str(k).strip() for k in keywords if str(k).strip()]
    if not cleaned:
        return escaped

    unique = sorted(set(cleaned), key=len, reverse=True)
    result = escaped
    for kw in unique:
        pattern = re.compile(re.escape(kw))
        result = pattern.sub(lambda m: f'<mark class="hl">{html.escape(m.group(0), quote=True)}</mark>', result)
    return result
