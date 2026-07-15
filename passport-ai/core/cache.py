"""추천 결과 캐시 (파일 기반). 발표 재현성을 위해 항상 저장, 장애 시 폴백 소스. v2(directions) 포맷."""
import json
from pathlib import Path
from .models import Result, Reco, Direction

CACHE_DIR = Path(__file__).parent.parent / "cache"


def _path(student_key: str) -> Path:
    safe = "".join(ch for ch in student_key if ch.isalnum() or ch in "-_")
    return CACHE_DIR / f"{safe}.json"


def write_cache(student_key: str, result: Result) -> None:
    CACHE_DIR.mkdir(exist_ok=True)
    json.dump(result.to_dict(), open(_path(student_key), "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)


def _reco(d: dict) -> Reco:
    return Reco(courseCode=d["courseCode"], courseName=d["courseName"], credit=d["credit"],
                reasons=list(d.get("reasons", [])), category=d.get("category"))


def read_cache(student_key: str) -> Result | None:
    p = _path(student_key)
    if not p.exists():
        return None
    d = json.load(open(p, encoding="utf-8"))

    if d.get("directions"):
        directions = [
            Direction(directionId=dd["directionId"], name=dd["name"],
                      description=dd.get("description", ""), caution=dd.get("caution"),
                      recommendations=[_reco(r) for r in dd.get("recommendations", [])],
                      thin=dd.get("thin", False))
            for dd in d["directions"]
        ]
        default = d.get("defaultDirectionId") or (directions[0].directionId if directions else "FAST_GRAD")
    else:
        recos = [_reco(r) for r in d.get("recommendations", [])]
        directions = [Direction("FAST_GRAD", "졸업요건 집중형",
                                "부족한 졸업 요건부터 확실하게 채우는 전략이에요.", None, recos, False)]
        default = "FAST_GRAD"

    return Result(directions=directions, defaultDirectionId=default,
                  source="cache", generatedAt=d.get("generatedAt", ""), persona=d.get("persona"))
