"""Baixa as ilustrações dos exercícios nativos do free-exercise-db (domínio público, Unlicense).

Cada exercício do app (id de data/Seed.kt) aponta para uma pasta do free-exercise-db, que tem
duas fotos: início (0.jpg) e fim (1.jpg) do movimento. As fotos são reduzidas e gravadas em WebP em
app/src/main/assets/exercises/<id>/0.webp e 1.webp, que o app lê pelo id (ExerciseIllustration).

As imagens geradas vão para o repositório: o build não depende de rede. Rode de novo só ao adicionar
um exercício nativo ou trocar uma foto:

    pip install pillow
    python scripts/fetch_exercise_images.py
"""

import io
import sys
import urllib.request
from pathlib import Path

from PIL import Image

# Commit fixo do free-exercise-db, para o resultado ser reproduzível.
SOURCE_COMMIT = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
SOURCE_URL = f"https://raw.githubusercontent.com/yuhonas/free-exercise-db/{SOURCE_COMMIT}/exercises/{{folder}}/{{frame}}.jpg"

SIZE_PX = 480
WEBP_QUALITY = 72
FRAMES = 2

OUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "exercises"

# id do app -> pasta no free-exercise-db
EXERCISE_IMAGES = {
    # Peito
    "supino_reto": "Barbell_Bench_Press_-_Medium_Grip",
    "supino_inclinado": "Barbell_Incline_Bench_Press_-_Medium_Grip",
    "supino_halteres": "Dumbbell_Bench_Press",
    "supino_maquina": "Machine_Bench_Press",
    "crucifixo": "Dumbbell_Flyes",
    "crucifixo_maquina": "Butterfly",
    "peck_deck": "Butterfly",
    "flexao": "Pushups",
    "mergulho": "Dips_-_Chest_Version",
    # Costas
    "barra_fixa": "Pullups",
    "remada_curvada": "Bent_Over_Barbell_Row",
    "remada_serrote": "One-Arm_Dumbbell_Row",
    "pulley_frente": "Wide-Grip_Lat_Pulldown",
    "pulley_apertado": "V-Bar_Pulldown",
    "remada_maquina": "Leverage_Iso_Row",
    "levantamento_terra": "Barbell_Deadlift",
    "remada_cabo": "Seated_Cable_Rows",
    "hiperextensao": "Hyperextensions_Back_Extensions",
    # Perna
    "agachamento": "Barbell_Squat",
    "leg_press": "Leg_Press",
    "extensao_joelho": "Leg_Extensions",
    "flexao_joelho": "Lying_Leg_Curls",
    "stiff": "Stiff-Legged_Barbell_Deadlift",
    "afundo": "Dumbbell_Lunges",
    "elevacao_panturrilha": "Standing_Calf_Raises",
    "cadeira_adutora": "Thigh_Adductor",
    "agachamento_hack": "Hack_Squat",
    # Ombros
    "desenvolvimento_barra": "Barbell_Shoulder_Press",
    "desenvolvimento_halteres": "Dumbbell_Shoulder_Press",
    "elevacao_lateral": "Side_Lateral_Raise",
    "elevacao_frontal": "Front_Dumbbell_Raise",
    "remada_alta": "Upright_Barbell_Row",
    "face_pull": "Face_Pull",
    # Bíceps
    "rosca_direta": "Barbell_Curl",
    "rosca_halteres": "Dumbbell_Bicep_Curl",
    "rosca_martelo": "Hammer_Curls",
    "rosca_scott": "Preacher_Curl",
    "rosca_cabo": "Standing_Biceps_Cable_Curl",
    # Tríceps
    "triceps_corda": "Triceps_Pushdown_-_Rope_Attachment",
    "triceps_testa": "EZ-Bar_Skullcrusher",
    "triceps_frances": "Standing_Dumbbell_Triceps_Extension",
    "triceps_maquina": "Machine_Triceps_Extension",
    # Abdômen
    "abdominal_crunch": "Crunches",
    "prancha": "Plank",
}


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def convert(data: bytes) -> bytes:
    image = Image.open(io.BytesIO(data)).convert("RGB")
    image.thumbnail((SIZE_PX, SIZE_PX), Image.LANCZOS)
    out = io.BytesIO()
    image.save(out, "WEBP", quality=WEBP_QUALITY, method=6)
    return out.getvalue()


def main() -> int:
    failures = []
    total_bytes = 0
    for exercise_id, folder in EXERCISE_IMAGES.items():
        target = OUT_DIR / exercise_id
        target.mkdir(parents=True, exist_ok=True)
        for frame in range(FRAMES):
            url = SOURCE_URL.format(folder=folder, frame=frame)
            try:
                webp = convert(fetch(url))
            except Exception as error:  # noqa: BLE001 - relata e segue para os outros
                failures.append(f"{exercise_id} ({url}): {error}")
                continue
            (target / f"{frame}.webp").write_bytes(webp)
            total_bytes += len(webp)
        print(f"ok  {exercise_id:<26} <- {folder}")

    print(f"\n{len(EXERCISE_IMAGES)} exercícios, {total_bytes / 1024:.0f} KB em {OUT_DIR}")
    if failures:
        print("\nFalhas:", *failures, sep="\n  ", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
