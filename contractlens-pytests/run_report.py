import os
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

import requests


def ensure_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def parse_junit(path: str) -> dict:
    tree = ET.parse(path)
    root = tree.getroot()

    suites = []
    if root.tag == "testsuites":
        suites = list(root.findall("testsuite"))
    elif root.tag == "testsuite":
        suites = [root]

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0}
    cases = []

    for suite in suites:
        totals["tests"] += int(suite.attrib.get("tests", 0))
        totals["failures"] += int(suite.attrib.get("failures", 0))
        totals["errors"] += int(suite.attrib.get("errors", 0))
        totals["skipped"] += int(suite.attrib.get("skipped", 0))
        totals["time"] += float(suite.attrib.get("time", 0.0))

        for case in suite.findall("testcase"):
            classname = case.attrib.get("classname", "")
            name = case.attrib.get("name", "")
            status = "passed"
            if case.find("failure") is not None or case.find("error") is not None:
                status = "failed"
            elif case.find("skipped") is not None:
                status = "skipped"
            cases.append({"classname": classname, "name": name, "status": status})

    return {"totals": totals, "cases": cases}


def categorize(case: dict) -> str:
    classname = (case.get("classname") or "").lower()
    name = (case.get("name") or "").lower()
    key = f"{classname}::{name}"
    if "test_tool_" in key:
        return "tool"
    if "test_security_" in key:
        return "security"
    if "test_integration_" in key or "requirements_test" in key:
        return "integration"
    if "test_retrieval_" in key:
        return "retrieval"
    return "other"


def backend_reachable(base_url: str) -> bool:
    try:
        resp = requests.get(f"{base_url.rstrip('/')}/error", timeout=2)
        return resp is not None
    except requests.RequestException:
        return False


def try_probe_knowledge_status(base_url: str) -> dict | None:
    if not backend_reachable(base_url):
        return None

    username = f"pytest_report_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    password = f"Passw0rd!{uuid.uuid4().hex[:6]}"
    email = f"{username}@example.com"

    try:
        requests.post(
            f"{base_url.rstrip('/')}/api/auth/register",
            json={"username": username, "email": email, "password": password},
            timeout=5,
        )
        login = requests.post(
            f"{base_url.rstrip('/')}/api/auth/login",
            json={"username": username, "password": password},
            timeout=5,
        )
        token = (login.json() or {}).get("token")
        if not token:
            return None
        status = requests.get(
            f"{base_url.rstrip('/')}/api/knowledge/status",
            headers={"Authorization": f"Bearer {token}"},
            timeout=8,
        )
        if status.status_code != 200:
            return {"status_code": status.status_code}
        data = status.json()
        return {
            "ragMode": data.get("ragMode"),
            "knowledgeDocsCount": data.get("knowledgeDocsCount"),
            "retrieverProbeReturnedSegments": data.get("retrieverProbeReturnedSegments"),
            "lightRagProbeReturnedChunks": data.get("lightRagProbeReturnedChunks"),
            "lightRagProbeLatencyMs": data.get("lightRagProbeLatencyMs"),
        }
    except requests.RequestException:
        return None


def main() -> int:
    base_url = os.environ.get("CONTRACTLENS_BASE_URL", "http://localhost:8080").strip()
    root = os.path.dirname(os.path.abspath(__file__))
    reports_dir = os.path.join(root, "reports")
    ensure_dir(reports_dir)

    junit_path = os.path.join(reports_dir, "junit.xml")
    report_md_path = os.path.join(reports_dir, "TEST_REPORT.md")

    cmd = [
        sys.executable,
        "-m",
        "pytest",
        "-q",
        f"--junitxml={junit_path}",
    ]

    proc = subprocess.run(cmd, cwd=root)

    junit = parse_junit(junit_path) if os.path.exists(junit_path) else None
    probe = try_probe_knowledge_status(base_url)

    if junit is None:
        with open(report_md_path, "w", encoding="utf-8") as f:
            f.write("# ContractLens pytest 测试报告\n\n")
            f.write("- 未生成 junit.xml，无法统计结果。\n")
        return proc.returncode

    totals = junit["totals"]
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    pass_rate = (passed / totals["tests"]) if totals["tests"] else 0.0

    by_layer = {}
    for case in junit["cases"]:
        layer = categorize(case)
        stat = by_layer.setdefault(layer, {"passed": 0, "failed": 0, "skipped": 0, "total": 0})
        stat["total"] += 1
        stat[case["status"]] += 1

    with open(report_md_path, "w", encoding="utf-8") as f:
        f.write("# ContractLens pytest 测试报告\n\n")
        f.write(f"- 总用例数：{totals['tests']}\n")
        f.write(f"- 通过：{passed}\n")
        f.write(f"- 失败：{totals['failures'] + totals['errors']}\n")
        f.write(f"- 跳过：{totals['skipped']}\n")
        f.write(f"- 通过率：{pass_rate:.2%}\n\n")

        f.write("## 分层级统计\n")
        for layer in sorted(by_layer.keys()):
            stat = by_layer[layer]
            f.write(f"- {layer}: {stat['passed']} passed, {stat['failed']} failed, {stat['skipped']} skipped, {stat['total']} total\n")
        f.write("\n")

        f.write("## 评估数据（knowledge/status 探测）\n")
        if probe is None:
            f.write("- 后端不可达或探测失败\n")
        else:
            for k, v in probe.items():
                f.write(f"- {k}: {v}\n")

    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
