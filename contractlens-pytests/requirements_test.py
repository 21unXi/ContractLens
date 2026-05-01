import os
import time
import uuid

import pytest
import requests


def base_url() -> str:
    return os.environ.get("CONTRACTLENS_BASE_URL", "http://localhost:8080").strip().rstrip("/")


def backend_reachable() -> bool:
    try:
        requests.get(f"{base_url()}/error", timeout=2)
        return True
    except requests.RequestException:
        return False


def register_and_login() -> str:
    username = f"pytest_req_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    password = f"Passw0rd!{uuid.uuid4().hex[:6]}"
    email = f"{username}@example.com"

    r = requests.post(
        f"{base_url()}/api/auth/register",
        json={"username": username, "email": email, "password": password},
        timeout=8,
    )
    if r.status_code != 200:
        raise AssertionError(f"register failed: {r.status_code} {r.text}")

    login = requests.post(
        f"{base_url()}/api/auth/login",
        json={"username": username, "password": password},
        timeout=8,
    )
    if login.status_code != 200:
        raise AssertionError(f"login failed: {login.status_code} {login.text}")
    token = (login.json() or {}).get("token")
    if not token:
        raise AssertionError("login returned empty token")
    return token


pytestmark = pytest.mark.integration


def test_requirement_auth_endpoints_public() -> None:
    if not backend_reachable():
        pytest.skip("backend unreachable")

    resp = requests.get(f"{base_url()}/api/auth/login", timeout=5)
    assert resp.status_code in (400, 405)


@pytest.mark.security
def test_requirement_protected_endpoints_require_jwt() -> None:
    if not backend_reachable():
        pytest.skip("backend unreachable")

    resp = requests.get(f"{base_url()}/api/contracts", timeout=5)
    assert resp.status_code == 401


def test_requirement_contract_result_can_be_null_before_analysis() -> None:
    if not backend_reachable():
        pytest.skip("backend unreachable")

    token = register_and_login()
    headers = {"Authorization": f"Bearer {token}"}

    files = {"file": ("sample.txt", "租房合同测试文本", "text/plain")}
    upload = requests.post(f"{base_url()}/api/contracts/upload", headers=headers, files=files, timeout=20)
    assert upload.status_code == 200
    contract_id = (upload.json() or {}).get("id")
    assert contract_id is not None

    result = requests.get(f"{base_url()}/api/analysis/contracts/{contract_id}/result", headers=headers, timeout=10)
    assert result.status_code == 200
    if not result.text or not result.text.strip():
        assert True
        return
    assert result.json() is None
