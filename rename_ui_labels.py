from pathlib import Path
import re

FILES = [
    Path("src/main/java/com/spothelper/client/ModMenuIntegration.java"),
    Path("src/main/java/com/spothelper/client/SpotHelperConfigScreen.java"),
    Path("src/main/java/com/spothelper/client/SpotHelperScreen.java"),
    Path("src/main/resources/assets/spothelper/lang/ru_ru.json"),
    Path("src/main/resources/fabric.mod.json"),
]

REPLACEMENTS = {
    "Настройки ESP": "Настройки визуальных меток",
    "Включить ESP": "Включить визуальные метки",
    "ESP через стены": "Отображать сквозь стены",
    "ESP радиус": "Радиус отображения",
    "ESP блоков": "Визуальные метки",
    "Добавить блок в ESP": "Добавить блок в метки",
    "Автоинструмент": "Умный выбор инструмента",
    "Настройки автоинструмента": "Настройки выбора инструмента",
    "Включить автоинструмент": "Включить умный выбор инструмента",
    "Авто-инструмент": "Умный выбор инструмента",
    "AutoTool": "Умный выбор инструмента",
}

# Меняем только содержимое строковых литералов.
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')

def replace_inside_string(match: re.Match) -> str:
    literal = match.group(0)
    content = literal[1:-1]

    old_content = content

    for old, new in REPLACEMENTS.items():
        content = content.replace(old, new)

    if content != old_content:
        return '"' + content + '"'

    return literal

for path in FILES:
    if not path.exists():
        print(f"SKIP: {path}")
        continue

    text = path.read_text(encoding="utf-8")

    new_text = STRING_RE.sub(replace_inside_string, text)

    if new_text != text:
        path.write_text(new_text, encoding="utf-8", newline="\n")
        print(f"UPDATED: {path}")
    else:
        print(f"NO CHANGES: {path}")

print("Done.")