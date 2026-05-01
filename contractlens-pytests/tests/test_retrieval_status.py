import pytest


pytestmark = pytest.mark.retrieval


def test_knowledge_status_schema_and_probe_fields(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    r = api.knowledge_status(user.token)
    assert r.status_code == 200
    data = r.json()

    assert data.get("ragMode") in ("lightrag", "legacy")
    assert isinstance(data.get("knowledgeDocsCount"), int)

    assert "retrieverProbeReturnedSegments" in data
    assert "lightRagProbeLatencyMs" in data

    if data.get("ragMode") == "lightrag" and data.get("knowledgeDocsCount", 0) > 0 and data.get("lightRagOk") is True:
        returned = data.get("lightRagProbeReturnedChunks")
        if returned is not None:
            assert returned >= 1
