import pytest


pytestmark = pytest.mark.integration


def test_register_login_returns_jwt_like_token(user) -> None:
    assert user.token.count(".") == 2


def test_upload_contract_then_list_contains_it(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    created = api.upload_contract_text(user.token, "contract.txt", "租房合同测试文本：押金、违约金、维修。")
    contract_id = created.get("id")
    assert contract_id is not None

    contracts = api.list_contracts(user.token)
    ids = {c.get("id") for c in contracts}
    assert contract_id in ids

    api.delete_contract(user.token, contract_id)


def test_get_analysis_result_initially_null(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    created = api.upload_contract_text(user.token, "contract2.txt", "租房合同测试文本：仅用于 result=null 断言。")
    contract_id = created.get("id")
    assert contract_id is not None

    resp = api.get_analysis_result(user.token, contract_id)
    assert resp.status_code == 200
    if not resp.text or not resp.text.strip():
        assert True
    else:
        assert resp.json() is None

    api.delete_contract(user.token, contract_id)


def test_chat_history_initially_empty(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    created = api.upload_contract_text(user.token, "contract3.txt", "租房合同测试文本：仅用于 history=[] 断言。")
    contract_id = created.get("id")
    assert contract_id is not None

    resp = api.get_chat_history(user.token, contract_id)
    assert resp.status_code == 200
    assert resp.json() == []

    api.delete_contract(user.token, contract_id)


def test_delete_contract_then_not_in_list(api, backend_available: bool, user) -> None:
    if not backend_available:
        pytest.skip("backend unreachable")

    created = api.upload_contract_text(user.token, "contract4.txt", "租房合同测试文本：仅用于删除断言。")
    contract_id = created.get("id")
    assert contract_id is not None

    d = api.delete_contract(user.token, contract_id)
    assert d.status_code == 204

    contracts = api.list_contracts(user.token)
    ids = {c.get("id") for c in contracts}
    assert contract_id not in ids
