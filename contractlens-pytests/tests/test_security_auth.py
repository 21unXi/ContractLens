import pytest
import requests


pytestmark = pytest.mark.security


def test_requires_jwt_for_contracts_list(contractlens_base_url: str, backend_available: bool) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")
    r = requests.get(f"{contractlens_base_url.rstrip('/')}/api/contracts", timeout=8)
    assert r.status_code == 401


def test_requires_jwt_for_knowledge_status(contractlens_base_url: str, backend_available: bool) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")
    r = requests.get(f"{contractlens_base_url.rstrip('/')}/api/knowledge/status", timeout=8)
    assert r.status_code == 401


def test_cross_user_contract_access_returns_404(api, backend_available: bool, user, user2) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    created = api.upload_contract_text(user.token, "a.txt", "租房合同A")
    contract_id = created.get("id")
    assert contract_id is not None

    resp = api.get_contract(user2.token, contract_id)
    assert resp.status_code == 404

    api.delete_contract(user.token, contract_id)
