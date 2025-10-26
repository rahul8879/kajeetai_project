"""Smoke tests for the agentic conversation service."""

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_developer_flow_activation_type():
    payload = {
        "session_id": "test-session-1",
        "mode": "developer",
        "message": "What's the code impact of adding a new field called activationType in the Activation tab?"
    }
    response = client.post("/chat", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert any(ref.startswith("ActivationDaoImpl.java") for ref in data["references"])
    assert data["mode"] == "developer"
    assert any("Inspect" in action for action in data["actions"])
    tools = {trace["tool"] for trace in data["tool_traces"]}
    assert "java_rag" in tools


def test_sales_flow_smartbus():
    payload = {
        "session_id": "test-session-2",
        "mode": "sales",
        "message": "Draft a proposal email for a school district interested in Kajeet SmartBus Wi-Fi."
    }
    response = client.post("/chat", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "sales"
    assert "SmartBus overview brochure" in data["references"]
    assert "Key value points" in data["answer"]


def test_support_flow_throttling():
    payload = {
        "session_id": "test-session-3",
        "mode": "support",
        "message": "500 Internal Server Error: Too Many Requests (Service: AmazonApiGateway; Status Code: 429)"
    }
    response = client.post("/chat", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "support"
    assert "API Gateway is throttling requests" in data["answer"]
    assert any("exponential backoff" in action.lower() for action in data["actions"])
