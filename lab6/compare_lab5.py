"""Порівняння мереж і результатів практикуму 6 (PetriSim) з мережами та результатами практикуму 5."""
import json
import math
import sys
from pathlib import Path

from scipy import stats

HERE = Path(__file__).resolve().parent
LAB5 = HERE.parent / "lab5" / "results"
RESULTS = HERE / "results"
PAIRS = [("conveyor", "net1_conveyor", "task1", "task1"), ("inventory", "net4_inventory", "task4", "task2")]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def structure(net):
    """Склад мережі без урахування порядку елементів; перенесення маркерів (carry) у PetriSim не існує."""
    items = {("P", p["name"], p["mark"]) for p in net["places"]}
    for t in net["transitions"]:
        items.add(("T", t["name"], t["distribution"], round(t["mean"], 9), t["priority"],
                   round(t["probability"], 9)))
        items |= {("I", a["place"], t["name"], a["n"]) for a in t["inputs"]}
        items |= {("II", a["place"], t["name"], a["n"]) for a in t["info_inputs"]}
        items |= {("O", t["name"], a["place"], a["n"]) for a in t["outputs"]}
    return items


def compare_results(lab5, lab6, runs5, runs6):
    """Середні з 95 % довірчими інтервалами двох серій прогонів і p-значення критерію Велча."""
    old = {r["metric"]: r for r in lab5["table"]}
    rows = []
    for metric, new in lab6["table"].items():
        if metric not in old:
            continue
        m5, c5, m6, c6 = old[metric]["mean"], old[metric]["ci"], new["mean"], new["ci"]
        s5 = c5 / stats.t.ppf(0.975, runs5 - 1)
        s6 = c6 / stats.t.ppf(0.975, runs6 - 1)
        se = math.hypot(s5, s6)
        z = (m6 - m5) / se if se > 0 else 0.0
        df = se ** 4 / (s5 ** 4 / (runs5 - 1) + s6 ** 4 / (runs6 - 1)) if se > 0 else 1
        p = 2 * stats.t.sf(abs(z), df)
        rows.append({"metric": metric, "lab5": [m5, c5], "lab6": [m6, c6], "diff": m6 - m5, "p": p})
    return rows


def main():
    report, ok = {}, True
    for name, net5, res5, res6 in PAIRS:
        s5 = structure(load(LAB5 / f"{net5}.json"))
        s6 = structure(load(RESULTS / f"net_{name}.json"))
        same = s5 == s6
        ok &= same
        print(f"{name}: структура мережі {'збігається' if same else 'НЕ збігається'} з мережею практикуму 5 "
              f"({sum(1 for i in s6 if i[0] == 'P')} позицій, {sum(1 for i in s6 if i[0] == 'T')} переходів, "
              f"{sum(1 for i in s6 if i[0] in ('I', 'II', 'O'))} дуг)")
        for item in sorted(s5 ^ s6, key=str):
            print("   ", "лише ЛР5" if item in s5 else "лише ЛР6", item)
        lab6 = load(RESULTS / f"{res6}.json")
        runs5 = 20 if name == "conveyor" else 100
        rows = compare_results(load(LAB5 / f"{res5}.json"), lab6, runs5, lab6["runs"])
        for r in rows:
            (m5, c5), (m6, c6) = r["lab5"], r["lab6"]
            print(f"  {r['metric']:46} ЛР5 {m5:9.4f} ± {c5:.4f}   ЛР6 {m6:9.4f} ± {c6:.4f}   p = {r['p']:.3f}")
        report[name] = {"same_structure": same, "rows": rows}
    (RESULTS / "lab5_comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=1),
                                                  encoding="utf-8")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
