import time
import uuid
from dataclasses import dataclass

import requests


def _now_suffix() -> str:
    return f"{int(time.time())}_{uuid.uuid4().hex[:8]}"


def _jwt_like(token: str) -> bool:
    if not token or "." not in token:
        return False
    parts = token.split(".")
    return len(parts) == 3 and all(parts)


@dataclass
class TestUser:
    username: str
    password: str
    email: str
    token: str


class ApiClient:
    def __init__(self, base_url: str = "http://localhost:8080", timeout: float = 10.0):
        self.base_url = (base_url or "").strip().rstrip("/")
        self.timeout = timeout

    def reachable(self) -> bool:
        try:
            requests.get(f"{self.base_url}/error", timeout=2)
            return True
        except requests.RequestException:
            return False

    def register(self, username: str, email: str, password: str) -> requests.Response:
        return requests.post(
            f"{self.base_url}/api/auth/register",
            json={"username": username, "email": email, "password": password},
            timeout=self.timeout,
        )

    def login(self, username: str, password: str) -> requests.Response:
        return requests.post(
            f"{self.base_url}/api/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )

    def create_user(self) -> TestUser:
        username = f"pytest_{_now_suffix()}"
        password = f"Passw0rd!{uuid.uuid4().hex[:6]}"
        email = f"{username}@example.com"

        r = self.register(username=username, email=email, password=password)
        if r.status_code != 200:
            raise AssertionError(f"register failed: {r.status_code} {r.text}")

        login = self.login(username=username, password=password)
        if login.status_code != 200:
            raise AssertionError(f"login failed: {login.status_code} {login.text}")

        token = (login.json() or {}).get("token")
        if not _jwt_like(token):
            raise AssertionError("login did not return jwt-like token")

        return TestUser(username=username, password=password, email=email, token=token)

    def auth_headers(self, token: str) -> dict:
        return {"Authorization": f"Bearer {token}"}

    def upload_contract_text(self, token: str, filename: str, text: str) -> dict:
        files = {"file": (filename, text, "text/plain")}
        r = requests.post(
            f"{self.base_url}/api/contracts/upload",
            headers=self.auth_headers(token),
            files=files,
            timeout=30,
        )
        if r.status_code != 200:
            raise AssertionError(f"upload failed: {r.status_code} {r.text}")
        return r.json()

    def list_contracts(self, token: str) -> list:
        r = requests.get(
            f"{self.base_url}/api/contracts",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )
        if r.status_code != 200:
            raise AssertionError(f"list contracts failed: {r.status_code} {r.text}")
        return r.json() or []

    def get_contract(self, token: str, contract_id: int) -> requests.Response:
        return requests.get(
            f"{self.base_url}/api/contracts/{contract_id}",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )

    def delete_contract(self, token: str, contract_id: int) -> requests.Response:
        return requests.delete(
            f"{self.base_url}/api/contracts/{contract_id}",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )

    def get_analysis_result(self, token: str, contract_id: int) -> requests.Response:
        return requests.get(
            f"{self.base_url}/api/analysis/contracts/{contract_id}/result",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )

    def get_chat_history(self, token: str, contract_id: int) -> requests.Response:
        return requests.get(
            f"{self.base_url}/api/analysis/contracts/{contract_id}/chat/history",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )

    def knowledge_status(self, token: str) -> requests.Response:
        return requests.get(
            f"{self.base_url}/api/knowledge/status",
            headers=self.auth_headers(token),
            timeout=self.timeout,
        )
