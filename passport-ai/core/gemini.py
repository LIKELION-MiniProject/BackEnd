"""
Gemini API 호출 집중. 모델명·키는 환경변수에서만. AI 는 방향별 코드 + '이유 2개'만 생성.
(이유①(성향맞춤)은 서버가 별도 규칙으로 생성 → 여기서 요구하지 않는다.)
B안: AI 호출은 최초 1회. 5방향 전부를 한 번의 응답으로 받는다.
"""
import os
from pydantic import BaseModel, Field


class DirItem(BaseModel):
    courseCode: str
    reasons: list[str] = Field(min_length=1, max_length=2)  # AI: 특성·요건/전공 관점 2개


class DirBlock(BaseModel):
    directionId: str
    items: list[DirItem]


class DirectionsResponse(BaseModel):
    directions: list[DirBlock]


def _client():
    from google import genai
    return genai.Client()  # GEMINI_API_KEY 환경변수 자동 사용


def list_models() -> list[str]:
    """이 키가 쓸 수 있는 모델 목록. GEMINI_MODEL 확정용."""
    out = []
    for m in _client().models.list():
        name = getattr(m, "name", str(m))
        actions = getattr(m, "supported_actions", None) or getattr(m, "supported_generation_methods", [])
        if not actions or "generateContent" in actions:
            out.append(name)
    return out


def generate(system: str, user_prompt: str) -> DirectionsResponse:
    model = os.environ["GEMINI_MODEL"]  # 하드코딩 금지 — .env 로 주입
    resp = _client().models.generate_content(
        model=model,
        contents=user_prompt,
        config={
            "system_instruction": system,
            "response_mime_type": "application/json",
            "response_schema": DirectionsResponse,
            "temperature": 0.3,
        },
    )
    parsed = getattr(resp, "parsed", None)
    if isinstance(parsed, DirectionsResponse):
        return parsed
    return DirectionsResponse.model_validate_json(resp.text)
