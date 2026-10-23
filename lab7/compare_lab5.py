"""Порівняння Петрі-об'єктних моделей практикуму 7 з мережами та результатами практикуму 5."""
import json
import math
import sys
from pathlib import Path

from scipy import stats

HERE = Path(__file__).resolve().parent
LAB5 = HERE.parent / "lab5" / "results"
RESULTS = HERE / "results"
SWEEP = {"throughput": "пропускна здатність", "time_in_system": "час перебування, с"}
NETS = [("fms_variant1", "net2_fms_variant1"), ("fms_variant2", "net2_fms_variant2"),
        ("minibus", "net3_minibus"), ("conveyor", "net1_conveyor")]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def structure(net):
    """Склад мережі без урахування порядку елементів, разом з дугами, що переносять мітки маркерів."""
    items = {("P", p["name"], p["mark"]) for p in net["places"]}
    for t in net["transitions"]:
        items.add(("T", t["name"], t["distribution"], round(t["mean"], 9), round(t["deviation"], 9),
                   t["priority"], round(t["probability"], 9)))
        items |= {("I", a["place"], t["name"], a["n"]) for a in t["inputs"]}
        items |= {("II", a["place"], t["name"], a["n"]) for a in t["info_inputs"]}
        items |= {("O", t["name"], a["place"], a["n"], a.get("carry")) for a in t["outputs"]}
    return items


def welch(m5, c5, n5, m7, c7, n7):
    """p-значення критерію Велча за середніми і півширинами 95 % довірчих інтервалів."""
    s5 = c5 / stats.t.ppf(0.975, n5 - 1)
    s7 = c7 / stats.t.ppf(0.975, n7 - 1)
    se = math.hypot(s5, s7)
    if se == 0:
        return 1.0
    df = se ** 4 / (s5 ** 4 / (n5 - 1) + s7 ** 4 / (n7 - 1))
    return 2 * stats.t.sf(abs((m7 - m5) / se), df)


def row(metric, old, new, n5, n7):
    m5, c5 = old
    m7, c7 = new["mean"], new["ci"]
    return {"metric": metric, "lab5": [m5, c5], "lab7": [m7, c7], "p": float(welch(m5, c5, n5, m7, c7, n7))}


def show(rows, nd=4):
    for r in rows:
        (m5, c5), (m7, c7) = r["lab5"], r["lab7"]
        print(f"  {r['metric']:46} ЛР5 {m5:10.{nd}f} ± {c5:.{nd}f}   ЛР7 {m7:10.{nd}f} ± {c7:.{nd}f}   "
              f"p = {r['p']:.3f}")


def compare_structure(report):
    ok = True
    print("Структура мережі Петрі-об'єктної моделі (після об'єднання позицій) і мережі практикуму 5:")
    for name, net5 in NETS:
        s5, s7 = structure(load(LAB5 / f"{net5}.json")), structure(load(RESULTS / f"net_{name}.json"))
        same = s5 == s7
        ok &= same
        places = sum(1 for i in s7 if i[0] == "P")
        transitions = sum(1 for i in s7 if i[0] == "T")
        arcs = sum(1 for i in s7 if i[0] in ("I", "II", "O"))
        report[name] = {"same": same, "places": places, "transitions": transitions, "arcs": arcs}
        verdict = "збігається" if same else "НЕ збігається"
        print(f"  {name}: {verdict} ({places} позицій, {transitions} переходів, {arcs} дуг, "
              "перенесення міток теж)")
        for item in sorted(s5 ^ s7, key=str):
            print("     ", "лише ЛР5" if item in s5 else "лише ЛР7", item)
    return ok


def compare_fms(report):
    t5, t7 = load(LAB5 / "task2.json"), load(RESULTS / "task2.json")
    n = t7["runs"]
    labels = dict(t7["metrics"]) | {"arrivals": "інтенсивність надходження, деталей/год"}
    for variant in ("1", "2"):
        for mode in ("base", "capacity"):
            old, new = t5[mode][variant], t7[mode][variant]
            rows = [row(labels[k], v, new[k], 20, n) for k, v in old.items()
                    if k in new and k in labels]
            if variant == "1" and mode == "base":
                rows += [row(f"завантаження робота {k + 1}", v, new["robot_load"][k], 20, n)
                         for k, v in enumerate(old["robot_load"])]
            report[f"{mode}_{variant}"] = rows
            title = "інтервал 40 с" if mode == "base" else "необмежене надходження"
            print(f"\nГВС, варіант {variant}, {title}:")
            show(rows, 2)
        sweep = []
        for a, v in t5["sweep"][variant].items():
            for k in ("throughput", "time_in_system"):
                label = f"{SWEEP[k]}, інтервал {float(a):.0f} с"
                sweep.append(row(label, v[k], t7["sweep"][variant][a][k], 20, n))
        report[f"sweep_{variant}"] = sweep
        print(f"ГВС, варіант {variant}, залежність від інтервалу надходження:")
        show(sweep, 2)


def compare_minibus(report):
    t5, t7 = load(LAB5 / "task3.json"), load(RESULTS / "task3.json")
    names = {"wait": "очікування, хв", "wait_queued": "очікування тих, хто чекав, хв",
             "queue": "середня черга", "boarded": "перевезено пасажирів", "lost": "пішли до іншого маршруту",
             "revenue": "виручка, грн"}
    rows = []
    for r5, r7 in zip(t5["sweep"], t7["sweep"]):
        for k, label in names.items():
            rows.append({**row(label, r5[k], r7[k], 50, t7["runs"]), "n": r5["n"]})
    small = int(sum(r["p"] < 0.05 for r in rows))
    report.update({"rows": rows, "tests": len(rows), "p_below_005": small})
    print(f"\nМікроавтобуси: {len(rows)} порівнянь (n = 1…25, 6 показників), p < 0,05 – "
          f"{small} (очікувано випадково ≈ {0.05 * len(rows):.1f}); n = 25:")
    show([r for r in rows if r["n"] == 25], 2)


def compare_conveyor(report):
    t5, t7 = load(LAB5 / "task1.json"), load(RESULTS / "task4.json")
    rows = [row(r["metric"], [r["mean"], r["ci"]], t7["table"][r["metric"]], 20, t7["runs"])
            for r in t5["table"]]
    keys = ("mean", "stdev", "median", "p90", "p95", "max")
    times = {k: [t5["time_stats"][k], t7["time_stats"][k]] for k in keys}
    report.update({"rows": rows, "time_stats": times})
    print("\nКонвеєр (задача 1 ЛР5):")
    show(rows)
    print("  розподіл часу перебування, ЛР5 / ЛР7: "
          + ", ".join(f"{k} {a:.2f} / {b:.2f}" for k, (a, b) in times.items()))


def main():
    report = {"structure": {}, "fms": {}, "minibus": {}, "conveyor": {}}
    ok = compare_structure(report["structure"])
    compare_fms(report["fms"])
    compare_minibus(report["minibus"])
    compare_conveyor(report["conveyor"])
    text = json.dumps(report, ensure_ascii=False, indent=1)
    (RESULTS / "lab5_comparison.json").write_text(text, encoding="utf-8")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
