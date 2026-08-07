import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLAN_PATH = ROOT / "routines.json"
RESOURCE_DIRS = [
    ROOT / "app" / "src" / "main" / "res" / "drawable",
    ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi",
]
RESOURCE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".xml"}


def load_days():
    data = json.loads(PLAN_PATH.read_text())
    return data if isinstance(data, list) else data.get("routines", [])


def resource_names():
    names = set()
    for resource_dir in RESOURCE_DIRS:
        if not resource_dir.exists():
            continue
        for path in resource_dir.iterdir():
            if path.suffix.lower() in RESOURCE_EXTENSIONS:
                names.add(path.stem)
    return names


def referenced_assets(days):
    assets = set()
    for day in days:
        for item in day.get("items", []):
            for workout in item.get("workouts", []) or []:
                asset = (workout.get("imageAsset") or "").strip()
                if asset:
                    assets.add(asset)
    return assets


def main():
    assets = referenced_assets(load_days())
    resources = resource_names()
    missing = sorted(assets - resources)
    unused = sorted((resources - assets) - {"ic_launcher_foreground", "traincue_training"})

    if missing:
        print("Missing image resources:")
        for asset in missing:
            print(f"  - {asset}")
    else:
        print("All referenced image assets exist.")

    if unused:
        print("Unused image resources:")
        for asset in unused:
            print(f"  - {asset}")

    raise SystemExit(1 if missing else 0)


if __name__ == "__main__":
    main()
