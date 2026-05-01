def _risk_score(level: str) -> int:
    if level == "高":
        return 3
    if level == "中":
        return 2
    if level == "低":
        return 1
    return 0


def sort_risks(risks: list[dict]) -> list[dict]:
    items = list(risks or [])
    items.sort(key=lambda r: _risk_score((r or {}).get("risk_level")), reverse=True)
    return items


def filter_risks_by_keywords(risks: list[dict], keywords: list[str] | None) -> list[dict]:
    if not keywords:
        return list(risks or [])
    kws = [str(k) for k in keywords if str(k)]
    if not kws:
        return list(risks or [])

    out = []
    for r in list(risks or []):
        clause = str((r or {}).get("clause_text") or "")
        desc = str((r or {}).get("risk_description") or "")
        text = clause + "\n" + desc
        if any(kw in text for kw in kws):
            out.append(r)
    return out
