# x264 QP Analysis (Compression vs Performance)

This project automates running the **x264** encoder with different **QP (Quantization Parameter)** values and generates a report showing how QP affects:

- **Compression**: output bitstream file size
- **Performance**: encoding speed (FPS) and/or execution time

Video quality analysis is intentionally ignored (per assignment requirements).

---

## What is QP?

**QP (Quantization Parameter)** controls compression strength:

- **Lower QP** → less compression → **larger output** (usually better quality)
- **Higher QP** → more compression → **smaller output** (usually worse quality)

QP can also affect encoding speed, measured as **FPS** (frames per second) or total execution time.

---

## Files in this repo

- `run_x264_qp_sweep.py`  
  Runs x264 for QP values **1–51**, collects results, and saves them into `results/results.csv`.

- `make_report.py`  
  Reads `results/results.csv` and generates an Excel spreadsheet report with charts at `results/qp_report.xlsx`.

- `foreman-cif.yuv`  
  Input raw YUV file (CIF resolution 352x288).

- `x264.exe`  
  x264 encoder (Windows).  
  **Note:** this build requires `cygwin1.dll` next to `x264.exe`.

- `cygwin1.dll`  
  Required runtime dependency for `x264.exe`.

---

## Requirements:
- Windows PowerShell / terminal
- Python 3.9+ (works with Python 3.x)
- `x264.exe` and its dependency `cygwin1.dll` in the same folder as scripts
- Python libraries for report generation:
  - `pandas`
  - `xlsxwriter`

Install Python dependencies:
Install Python through Microsoft Store

```powershell
pip install pandas xlsxwriter

---

## Running the script
Step 1: Run the QP sweep  -  This script runs x264 multiple times, once per QP value (default range 1–51), and writes results to CSV.

Run:
python run_x264_qp_sweep.py

Outputs:
Encoded bitstreams per QP:
outputs/out_qp01.264
outputs/out_qp02.264
...

Results table:
results/results.csv

CSV columns:
qp: QP value used for that run
elapsed_sec: total run time measured by the script
fps: parsed from x264 output (if available)
size_bytes: output file size in bytes
size_mb: output file size in MB
return_code: x264 process return code (0 means success)
The scripts will create outputs/ and results/ folders automatically if they don't exist.

Step 2: Generate the Excel report (charts)  -  After results/results.csv exists, generate a spreadsheet report

Run:
python make_report.py

Output:
results/qp_report.xlsx

The Excel file includes:
Data sheet with all QP results
Chart: QP vs Output Size (MB)
Chart: QP vs FPS (if FPS is available)
Otherwise it falls back to QP vs elapsed time

---

## About failures / return codes

If a row has return_code != 0, the encode failed for that QP value.
In that case, size and fps may be missing/empty because no output file was generated.

Example:
QP 1–4 may fail on some x264 builds (return_code = 255).
This depends on the specific x264 binary build and its internal limits.

To investigate failures, run a failing QP manually:
.\x264.exe --qp 1 --input-res 352x288 -o test_qp01.264 foreman-cif.yuv 2>&1
echo $LASTEXITCODE

---

## Notes / Assumptions

Input file: foreman-cif.yuv
Input resolution: 352x288 (CIF)
Output bitstream extension is .264 (equivalent to raw H.264 bitstream as required by assignment)

Troubleshooting
x264.exe prints nothing and exits with e.g "-1073741515"
This usually means a missing DLL dependency.

For this project, the fix was adding:
cygwin1.dll in the same folder as x264.exe

Verify x264 works:
.\x264.exe --help
echo $LASTEXITCODE

You should see help output and exit code 0.

Output size/fps columns empty

This happens when encoding fails (return_code != 0) and no output file is created.
Check the return code and run that QP manually to see the error message.

Deliverables produced

results/results.csv – raw measurements for each QP
results/qp_report.xlsx – spreadsheet report with charts
outputs/*.264 – encoded bitstreams for each QP

ChatGPT conversation used:
https://chatgpt.com/share/699e1a51-bc30-8012-9178-7ee58321d51b