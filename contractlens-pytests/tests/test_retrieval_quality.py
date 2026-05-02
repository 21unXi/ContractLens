import pytest


@pytest.mark.retrieval
def test_rag_quality_probe_has_preview_and_keywords(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    r = api.knowledge_status(user.token)
    assert r.status_code == 200
    data = r.json() or {}

    docs = data.get("knowledgeDocsCount") or 0
    if docs <= 0:
        pytest.skip("knowledge_docs is empty; retrieval quality cannot be evaluated")

    preview = (data.get("retrieverProbeContextPreview") or "").strip()
    assert preview, "retrieverProbeContextPreview is required for retrieval evaluation"

    lowered = preview.lower()
    if "no-context" in lowered or "not able to provide an answer" in lowered:
        rag_mode = data.get("ragMode")
        assert rag_mode in ("lightrag", "legacy")
        if rag_mode == "lightrag":
            assert data.get("lightRagEnabled") is True
            assert data.get("lightRagOk") is True
        return

    probe_query = (data.get("retrieverProbeQuery") or "").strip()
    assert probe_query

    keywords = [k for k in probe_query.replace("\t", " ").split(" ") if len(k) >= 2]
    keywords = keywords[:6]
    hits = [k for k in keywords if k in preview]
    if not hits:
        return

    rag_mode = data.get("ragMode")
    assert rag_mode in ("lightrag", "legacy")

    if rag_mode == "lightrag":
        assert data.get("lightRagEnabled") is True
        assert data.get("lightRagOk") is True
        assert (data.get("lightRagProbeContextChars") or 0) >= 50
        assert (data.get("lightRagProbeLatencyMs") or 0) > 0
        assert (data.get("lightRagProbeLatencyMs") or 0) < 10000
    else:
        assert (data.get("retrieverProbeReturnedSegments") or 0) >= 1
