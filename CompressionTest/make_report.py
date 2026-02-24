import csv
from pathlib import Path

import pandas as pd

RESULTS_CSV = Path("results/results.csv")
OUT_XLSX = Path("results/qp_report.xlsx")

def main():
    if not RESULTS_CSV.exists():
        raise FileNotFoundError(f"Missing: {RESULTS_CSV}")

    df = pd.read_csv(RESULTS_CSV)

    # Ensure numeric
    for col in ["qp", "elapsed_sec", "fps", "size_bytes", "size_mb", "return_code"]:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    # Create Excel with charts
    with pd.ExcelWriter(OUT_XLSX, engine="xlsxwriter") as writer:
        df.to_excel(writer, index=False, sheet_name="Data")

        workbook  = writer.book
        worksheet = writer.sheets["Data"]

        # Format header
        header_fmt = workbook.add_format({"bold": True})
        worksheet.set_row(0, None, header_fmt)

        # Autofit-ish widths
        worksheet.set_column("A:A", 6)   # qp
        worksheet.set_column("B:B", 12)  # elapsed_sec
        worksheet.set_column("C:C", 10)  # fps
        worksheet.set_column("D:D", 12)  # size_bytes
        worksheet.set_column("E:E", 10)  # size_mb
        worksheet.set_column("F:F", 12)  # return_code

        # Find column indexes by name
        cols = {name: i for i, name in enumerate(df.columns)}
        last_row = len(df)

        # Chart 1: QP vs Size (MB)
        if "qp" in cols and "size_mb" in cols:
            chart1 = workbook.add_chart({"type": "line"})
            chart1.add_series({
                "name": "Size (MB)",
                "categories": ["Data", 1, cols["qp"], last_row, cols["qp"]],
                "values":     ["Data", 1, cols["size_mb"], last_row, cols["size_mb"]],
            })
            chart1.set_title({"name": "QP vs Output Size"})
            chart1.set_x_axis({"name": "QP"})
            chart1.set_y_axis({"name": "Output size (MB)"})
            worksheet.insert_chart("H2", chart1, {"x_scale": 1.35, "y_scale": 1.35})

        # Chart 2: QP vs FPS (or elapsed if fps missing)
        if "qp" in cols:
            if "fps" in cols and df["fps"].notna().any():
                chart2 = workbook.add_chart({"type": "line"})
                chart2.add_series({
                    "name": "FPS",
                    "categories": ["Data", 1, cols["qp"], last_row, cols["qp"]],
                    "values":     ["Data", 1, cols["fps"], last_row, cols["fps"]],
                })
                chart2.set_title({"name": "QP vs Encoding Speed (FPS)"})
                chart2.set_x_axis({"name": "QP"})
                chart2.set_y_axis({"name": "FPS"})
                worksheet.insert_chart("H20", chart2, {"x_scale": 1.35, "y_scale": 1.35})
            elif "elapsed_sec" in cols:
                chart2 = workbook.add_chart({"type": "line"})
                chart2.add_series({
                    "name": "Elapsed time (sec)",
                    "categories": ["Data", 1, cols["qp"], last_row, cols["qp"]],
                    "values":     ["Data", 1, cols["elapsed_sec"], last_row, cols["elapsed_sec"]],
                })
                chart2.set_title({"name": "QP vs Encoding Time"})
                chart2.set_x_axis({"name": "QP"})
                chart2.set_y_axis({"name": "Time (sec)"})
                worksheet.insert_chart("H20", chart2, {"x_scale": 1.35, "y_scale": 1.35})

    print(f"Excel report generated: {OUT_XLSX}")

if __name__ == "__main__":
    main()