import csv
import re
import subprocess
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

# Absolute paths (Windows-safe)
X264_PATH = str((SCRIPT_DIR / "x264.exe").resolve())
INPUT_YUV = str((SCRIPT_DIR / "foreman-cif.yuv").resolve())
INPUT_RES = "352x288"  # foreman-cif is CIF resolution

QP_START, QP_END = 1, 51

OUT_DIR = SCRIPT_DIR / "outputs"
RES_DIR = SCRIPT_DIR / "results"
OUT_DIR.mkdir(exist_ok=True)
RES_DIR.mkdir(exist_ok=True)

# More flexible: x264 output formatting can vary
FPS_REGEX = re.compile(r"encoded\s+\d+\s+frames.*?([\d.]+)\s+fps", re.IGNORECASE)

def run_x264(qp: int) -> dict:
    out_file = OUT_DIR / f"out_qp{qp:02d}.264"

    cmd = [
        X264_PATH,
        "--qp", str(qp),
        "--input-res", INPUT_RES,
        "--input-csp", "i420",
        # "--frames", "300",  # uncomment only if needed
        "-o", str(out_file),
        INPUT_YUV,
    ]

    start = time.perf_counter()
    proc = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(SCRIPT_DIR)  # force working directory
    )
    elapsed = time.perf_counter() - start

    # x264 often writes progress to stderr, but we combine both
    log = (proc.stderr or "") + "\n" + (proc.stdout or "")

    fps = None
    m = FPS_REGEX.search(log)
    if m:
        try:
            fps = float(m.group(1))
        except ValueError:
            fps = None

    # Ensure file exists and is flushed
    size_bytes = out_file.stat().st_size if out_file.exists() else None

    # Debug when something is missing
    if proc.returncode != 0 or size_bytes is None or fps is None:
        # Print only last ~20 lines to avoid spam
        tail = "\n".join(log.splitlines()[-20:])
        print(f"\n--- DEBUG QP={qp} ---")
        print(f"Return code: {proc.returncode}")
        print(f"Expected output: {out_file}")
        print(f"Output exists: {out_file.exists()}")
        print("Last lines of x264 log:")
        print(tail)
        print("--- END DEBUG ---\n")

    return {
        "qp": qp,
        "elapsed_sec": round(elapsed, 4),
        "fps": fps,
        "size_bytes": size_bytes,
        "size_mb": (size_bytes / (1024 * 1024)) if size_bytes is not None else None,
        "return_code": proc.returncode
    }

def main():
    results = []

    for qp in range(QP_START, QP_END + 1):
        print(f"Running QP={qp}...")
        r = run_x264(qp)
        results.append(r)

    csv_path = RES_DIR / "results.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)

    print(f"\nDone! Results saved to: {csv_path}")

if __name__ == "__main__":
    main()