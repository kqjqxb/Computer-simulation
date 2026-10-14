"""Завдання 4: верифікація моделі з рисунка 2.1 зміною вхідних змінних і параметрів."""
import json
import math
from pathlib import Path

import fun_rand
from queueing_theory import mmck
from runner import metric, replicate
from task3_chain import build

RESULTS = Path(__file__).resolve().parent / "results"
TIME = 10_000.0
INF = math.inf

EXPERIMENTS = [
    ("базові параметри", {}),
    ("Create: exp(1.0)", {"create_mean": 1.0}),
    ("Create: exp(4.0)", {"create_mean": 4.0}),
    ("PROCESS 2: exp(3.0)", {"means": (1.0, 3.0, 1.2)}),
    ("maxqueue = 0", {"maxqueues": (0, 0, 0)}),
    ("maxqueue = 1", {"maxqueues": (1, 1, 1)}),
    ("maxqueue = ∞", {"maxqueues": (INF, INF, INF)}),
    ("усі затримки сталі", {"create_dist": "", "dists": ("", "", "")}),
    ("PROCESS: unif(mean ± 0.5)", {"dists": ("unif",) * 3, "devs": (0.5,) * 3}),
    ("PROCESS: norm(mean; 0.3)", {"dists": ("norm",) * 3, "devs": (0.3,) * 3}),
    ("PROCESS: Ерланга k = 4", {"dists": ("erlang",) * 3, "devs": (4,) * 3}),
]


def variation_table():
    rows = []
    print(f"ВЕРИФІКАЦІЯ: зміна вхідних змінних і параметрів (час моделювання {TIME:.0f}, однакове зерно)")
    header = (f"{'№':>2} {'зміна':26} {'створ.':>6} {'вийшло':>6} {'відмови P1/P2/P3':>17} "
              f"{'черга P1/P2/P3':>17} {'завант. P1/P2/P3':>17} {'Pвідм':>6} {'Tсист':>7} {'баланс':>6}")
    print(header)
    for i, (label, params) in enumerate(EXPERIMENTS, 1):
        fun_rand.seed(4)
        model = build(params)
        model.simulate(TIME)
        r = model.results()
        p = list(r["processes"].values())
        balance = r["created"] - r["disposed"] - r["failures"] - r["in_system_now"]
        rows.append({"label": label, "created": r["created"], "disposed": r["disposed"],
                     "failures": [x["failure"] for x in p], "queues": [x["mean_queue"] for x in p],
                     "loads": [x["mean_load"] for x in p], "p_fail": r["failure_probability"],
                     "t_system": r["mean_time_in_system"], "balance": balance})
        print(f"{i:>2} {label:26} {r['created']:>6} {r['disposed']:>6} "
              f"{'/'.join(str(x['failure']) for x in p):>17} "
              f"{'/'.join(f'{x['mean_queue']:.2f}' for x in p):>17} "
              f"{'/'.join(f'{x['mean_load']:.3f}' for x in p):>17} "
              f"{r['failure_probability']:>6.3f} {r['mean_time_in_system']:>7.3f} {balance:>6}")
    return rows


def theory_table():
    seeds = range(1, 11)
    out = []
    print("\nПОРІВНЯННЯ З ТЕОРІЄЮ (10 прогонів по 20000 одиниць часу, 95 % довірчий інтервал)")

    print("\n1) Необмежені черги: кожен пристрій – система M/M/1 з λ = 0.5 (теорема Берка)")
    runs = replicate(lambda: build({"maxqueues": (INF, INF, INF)}), 20_000.0, seeds)
    t_total = 0.0
    for name, mean in zip(["PROCESS 1", "PROCESS 2", "PROCESS 3"], [1.0, 1.5, 1.2]):
        th = mmck(0.5, mean)
        t_total += th["mean_in_system"] / 0.5
        for label, key in [("завантаження", "mean_load"), ("середня черга", "mean_queue")]:
            sim, ci = metric(runs, ["processes", name, key])
            out.append({"case": "M/M/1", "element": name, "metric": label, "sim": sim, "ci": ci,
                        "theory": th[key]})
            print(f"{name:10} {label:14} імітація = {sim:.4f} ± {ci:.4f}, теорія = {th[key]:.4f}")
    sim, ci = metric(runs, ["mean_time_in_system"])
    out.append({"case": "M/M/1", "element": "модель", "metric": "час перебування", "sim": sim, "ci": ci,
                "theory": t_total})
    print(f"{'модель':10} {'час у системі':14} імітація = {sim:.4f} ± {ci:.4f}, теорія = {t_total:.4f}")

    print("\n2) PROCESS 1 з обмеженою чергою: система M/M/1/K, λ = 0.5, середній час обслуговування 1.0")
    for q in (0, 1, 2, 5):
        runs = replicate(lambda q=q: build({"maxqueues": (q, INF, INF)}), 20_000.0, seeds)
        th = mmck(0.5, 1.0, 1, q)
        for label, key in [("ймовірність відмови", "failure_probability"), ("завантаження", "mean_load"),
                           ("середня черга", "mean_queue")]:
            sim, ci = metric(runs, ["processes", "PROCESS 1", key])
            out.append({"case": f"M/M/1/{q + 1}", "element": "PROCESS 1", "metric": label,
                        "sim": sim, "ci": ci, "theory": th[key]})
            print(f"maxqueue = {q}: {label:20} імітація = {sim:.4f} ± {ci:.4f}, теорія = {th[key]:.4f}")
    return out


def main():
    rows = variation_table()
    theory = theory_table()
    with open(RESULTS / "task4.json", "w", encoding="utf-8") as f:
        json.dump({"variations": rows, "theory": theory}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
