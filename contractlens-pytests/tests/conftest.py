import os

import pytest

from contractlens_testkit import ApiClient


@pytest.fixture(scope="session")
def contractlens_base_url() -> str:
    return os.environ.get("CONTRACTLENS_BASE_URL", "http://localhost:8080").strip()


@pytest.fixture(scope="session")
def api(contractlens_base_url: str) -> ApiClient:
    return ApiClient(base_url=contractlens_base_url, timeout=15)


@pytest.fixture(scope="session")
def backend_available(api: ApiClient) -> bool:
    return api.reachable()


@pytest.fixture(scope="session")
def user(api: ApiClient, backend_available: bool):
    if not backend_available:
        pytest.skip("backend unreachable")
    return api.create_user()


@pytest.fixture(scope="session")
def user2(api: ApiClient, backend_available: bool):
    if not backend_available:
        pytest.skip("backend unreachable")
    return api.create_user()
