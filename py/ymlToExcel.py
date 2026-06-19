import argparse
from pathlib import Path
import yaml
import pandas as pd
from openpyxl import Workbook


def load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def collect_last_attempts(response_folder: Path) -> list[dict]:
    if not response_folder.exists() or not response_folder.is_dir():
        return []

    last_attempts: dict[str, dict] = {}
    for response_path in sorted(response_folder.glob("*_try_*.yml")):
        data = load_yaml(response_path)
        if not data:
            continue

        player = data.get("player") or "unknown"
        uuid = data.get("uuid") or player
        key = uuid if uuid != "unknown" else player

        last_attempts[key] = data

    return list(last_attempts.values())


def build_question_columns(questionnaire: dict) -> list[tuple[str, str]]:
    questions = questionnaire.get("questions", {}) or {}
    ordered_keys = sorted(
        questions.keys(),
        key=lambda k: int(str(k)) if str(k).isdigit() else str(k),
    )

    columns = []
    for key in ordered_keys:
        question = questions[key] or {}
        prompt = str(question.get("prompt", "")).strip()
        if prompt:
            header = f"question_{key}: {prompt}"
        else:
            header = f"question_{key}"
        answer_key = f"question_{key}"
        columns.append((answer_key, header))

    return columns


def build_rows(responses: list[dict], question_columns: list[tuple[str, str]]) -> list[dict]:
    rows = []
    for response in responses:
        row = {
            "player": response.get("player", ""),
            "uuid": response.get("uuid", ""),
            "quest": response.get("quest", ""),
            "try": response.get("try", ""),
            "timestamp": response.get("timestamp", ""),
            "timestamp_human": response.get("timestamp_human", ""),
        }

        answers = response.get("answers", {}) or {}
        for answer_key, header in question_columns:
            row[header] = answers.get(answer_key, "")

        rows.append(row)
    return rows


def unique_column_names(names: list[str]) -> list[str]:
    seen: dict[str, int] = {}
    unique_names: list[str] = []
    for name in names:
        if name in seen:
            seen[name] += 1
            unique_names.append(f"{name} ({seen[name]})")
        else:
            seen[name] = 1
            unique_names.append(name)
    return unique_names


def build_transposed_matrix(rows: list[dict], question_columns: list[tuple[str, str]]) -> tuple[list[str], list[list[str]]]:
    meta_fields = ["player", "uuid", "quest", "try", "timestamp", "timestamp_human"]
    labels = meta_fields + [header for _, header in question_columns]

    columns = []
    column_names = []
    for idx, response in enumerate(rows, start=1):
        name = response.get("player") or response.get("uuid") or f"player_{idx}"
        column_names.append(name)
        values = [response.get(field, "") for field in meta_fields]
        values.extend(response.get(header, "") for _, header in question_columns)
        columns.append(values)

    unique_names = unique_column_names(column_names)
    header_row = ["field"] + unique_names

    data_rows = []
    for row_index, label in enumerate(labels):
        data_row = [label] + [column[row_index] for column in columns]
        data_rows.append(data_row)

    return header_row, data_rows


def write_excel_with_pandas(
    sheets: dict[str, tuple[list[dict], list[tuple[str, str]]]],
    output_file: Path,
    transpose: bool,
) -> None:
    if pd is None:
        raise ImportError("pandas no está instalado. Instala con: pip install pandas openpyxl")

    with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
        for sheet_name, (rows, question_columns) in sheets.items():
            if not rows:
                df = pd.DataFrame([{"note": "No hay respuestas de primer intento"}])
            elif transpose:
                header_row, data_rows = build_transposed_matrix(rows, question_columns)
                df = pd.DataFrame(data_rows, columns=header_row)
            else:
                df = pd.DataFrame(rows)
                if df.empty:
                    df = pd.DataFrame([{"note": "No hay respuestas de primer intento"}])
            df.to_excel(writer, sheet_name=sheet_name[:31], index=False)


def write_excel_with_openpyxl(
    sheets: dict[str, tuple[list[dict], list[tuple[str, str]]]],
    output_file: Path,
    transpose: bool,
) -> None:
    if Workbook is None:
        raise ImportError("openpyxl no está instalado. Instala con: pip install openpyxl")

    workbook = Workbook()
    first_sheet = True

    for sheet_name, (rows, question_columns) in sheets.items():
        if first_sheet:
            worksheet = workbook.active
            worksheet.title = sheet_name[:31]
            first_sheet = False
        else:
            worksheet = workbook.create_sheet(title=sheet_name[:31])

        if not rows:
            worksheet.append(["No hay respuestas de primer intento"])
            continue

        if transpose:
            header_row, data_rows = build_transposed_matrix(rows, question_columns)
            worksheet.append(header_row)
            for data_row in data_rows:
                worksheet.append(data_row)
            continue

        headers = list(rows[0].keys())
        worksheet.append(headers)
        for row in rows:
            worksheet.append([row.get(header, "") for header in headers])

    workbook.save(output_file)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Convierte cuestionarios YAML y respuestas de primer intento a un archivo Excel con una hoja por cuestionario."
    )
    parser.add_argument(
        "--data-dir",
        default="data",
        help="Carpeta que contiene los archivos de cuestionarios y las subcarpetas de respuestas.",
    )
    parser.add_argument(
        "--output",
        default="resultados_cuestionarios.xlsx",
        help="Archivo Excel de salida.",
    )
    parser.add_argument(
        "--transpose",
        action="store_true",
        help="Transponer cada hoja para que las preguntas queden hacia abajo y los jugadores horizontalmente.",
    )
    args = parser.parse_args()

    base_dir = Path(args.data_dir)
    if not base_dir.exists() or not base_dir.is_dir():
        raise FileNotFoundError(f"No se encontró la carpeta de datos: {base_dir}")

    sheets: dict[str, tuple[list[dict], list[tuple[str, str]]]] = {}
    for questionnaire_path in sorted(base_dir.glob("*.yml")):
        questionnaire = load_yaml(questionnaire_path)
        if not questionnaire:
            continue

        sheet_name = str(questionnaire.get("name") or questionnaire_path.stem).strip() or questionnaire_path.stem
        response_folder = base_dir / questionnaire_path.stem
        responses = collect_last_attempts(response_folder)
        question_columns = build_question_columns(questionnaire)
        rows = build_rows(responses, question_columns)
        sheets[sheet_name] = (rows, question_columns)

    output_path = Path(args.output)
    if pd is not None:
        write_excel_with_pandas(sheets, output_path, transpose=args.transpose)
    else:
        write_excel_with_openpyxl(sheets, output_path, transpose=args.transpose)

    print(f"Archivo Excel generado: {output_path}")


if __name__ == "__main__":
    main()
