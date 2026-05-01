import pytest

from contractlens_testkit import filter_risks_by_keywords, highlight_html, parse_sse_events, sort_risks


pytestmark = pytest.mark.tool


def test_sse_parser_parses_status_answer_done() -> None:
    raw = (
        "event: status\n"
        'data: {"message":"analyzing"}\n'
        "\n"
        "event: answer\n"
        'data: {"delta":"hello","isLast":false}\n'
        "\n"
        "event: done\n"
        'data: {"analysisResult":{"risk_level":"中","risk_score":50}}\n'
        "\n"
    )
    events = parse_sse_events(raw)
    assert [e[0] for e in events] == ["status", "answer", "done"]
    assert events[0][1]["message"] == "analyzing"
    assert events[2][1]["analysisResult"]["risk_level"] == "中"


def test_sse_parser_handles_multiline_data() -> None:
    raw = (
        "event: status\n"
        "data: {\"message\":\"line1\"\n"
        "data: ,\"phase\":\"p1\"}\n"
        "\n"
    )
    events = parse_sse_events(raw)
    assert len(events) == 1
    assert events[0][0] == "status"
    assert events[0][1]["message"] == "line1"
    assert events[0][1]["phase"] == "p1"


def test_highlight_escapes_html_then_marks_keywords() -> None:
    text = '<script>alert("x")</script> 押金 违约'
    html = highlight_html(text, ["押金", "违约"])
    assert "<script>" not in html
    assert "&lt;script&gt;" in html
    assert '<mark class="hl">押金</mark>' in html
    assert '<mark class="hl">违约</mark>' in html


def test_risk_sorting_high_medium_low() -> None:
    risks = [
        {"risk_level": "低", "risk_type": "A"},
        {"risk_level": "高", "risk_type": "B"},
        {"risk_level": "中", "risk_type": "C"},
    ]
    sorted_risks = sort_risks(risks)
    assert [r["risk_level"] for r in sorted_risks] == ["高", "中", "低"]


def test_risk_keyword_filter_matches_clause_or_desc() -> None:
    risks = [
        {"risk_level": "高", "clause_text": "押金返还", "risk_description": ""},
        {"risk_level": "中", "clause_text": "", "risk_description": "涉及维修责任"},
        {"risk_level": "低", "clause_text": "其他", "risk_description": "无关"},
    ]
    filtered = filter_risks_by_keywords(risks, ["押金", "维修"])
    assert len(filtered) == 2
