"""Завдання 1-4: модель з N послідовних СМО, експериментальна і теоретична оцінка складності
алгоритму імітації для послідовної та розгалужених структур мережі."""
import json
import math
import platform
import sys
import time
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

import fun_rand
from fast_model import FastModel
from model import Model
from networks import ARRIVAL_MEAN, STRUCTURES, TITLES, chain, path_length, sojourn_time

RESULTS = Path(__file__).resolve().parent / "results"
TIME = 500.0
REPEATS = 3
SIZES = {
    Model: {"chain": [5, 10, 20, 50, 100, 150, 200], "feedback": [5, 10, 20, 50, 100, 150, 200],
            "parallel": [5, 10, 20, 50, 100, 150, 200], "tree": [3, 7, 15, 31, 63, 127, 255]},
    FastModel: {"chain": [5, 10, 20, 50, 100, 200, 500, 1000],
                "feedback": [5, 10, 20, 50, 100, 200, 500, 1000],
                "parallel": [5, 10, 20, 50, 100, 200, 500, 1000],
                "tree": [3, 7, 15, 31, 63, 127, 255, 511, 1023]},
}
ENGINE_NAMES = {Model: "базовий алгоритм", FastModel: "календар подій"}
COLORS = {"chain": "#2c5d8f", "feedback": "#c0392b", "tree": "#27ae60", "parallel": "#8e44ad"}


def demo():
    """Модель з N = 5 послідовних СМО: трасування перших подій і результати."""
    fun_rand.seed(1)
    n = 5
    model = chain(n)
    print(f"Модель з N = {n} послідовних СМО: надходження exp({ARRIVAL_MEAN}), обслуговування exp(0.3), "
          f"час моделювання 1000")
    model.simulate(1000.0, trace_events=3)
    r = model.results()
    print("\n-------------RESULTS-------------")
    for name, p in r["processes"].items():
        print(f"{name}: обслуговано {p['served']}, завантаження {p['mean_load']:.4f}, "
              f"черга {p['mean_queue']:.4f}")
    print(f"створено {r['created']}, вийшло {r['disposed']}, "
          f"середній час перебування {r['mean_time_in_system']:.4f}")
    print(f"кількість подій {model.events}; оцінка λ·T·(N + 1) = {1000 / ARRIVAL_MEAN * (n + 1):.0f}")
    return {"n": n, "events": model.events, "created": r["created"], "disposed": r["disposed"],
            "time_in_system": r["mean_time_in_system"],
            "loads": [p["mean_load"] for p in r["processes"].values()]}


def measure(structure, n, engine):
    """Час обчислення відрізка модельного часу TIME у сталому режимі: спочатку без заміру часу
    виконується розгін тривалістю у два середні часи проходження мережі."""
    warmup = 2 * sojourn_time(structure, n) + 10
    best = math.inf
    for repeat in range(REPEATS):
        fun_rand.seed(100 + repeat)
        model = STRUCTURES[structure](n, engine)
        model.simulate(warmup)
        events_before = model.events
        start = time.perf_counter()
        model.simulate(warmup + TIME)
        best = min(best, time.perf_counter() - start)
    return {"structure": structure, "engine": ENGINE_NAMES[engine], "n": n, "elements": n + 2,
            "events": model.events - events_before, "seconds": best, "path": path_length(structure, n),
            "warmup": warmup, "expected_events": TIME / ARRIVAL_MEAN * (1 + path_length(structure, n))}


def fit_cost(rows, feature):
    """МНК для моделі t = E · (c0 + c1 · feature(N)): вартість однієї події лінійна за feature."""
    e = np.array([r["events"] for r in rows], dtype=float)
    f = np.array([feature(r["n"]) for r in rows], dtype=float)
    t = np.array([r["seconds"] for r in rows])
    # відносна похибка важливіша за абсолютну, тому рівняння діляться на t
    a = np.column_stack([e, e * f]) / t[:, None]
    (c0, c1), *_ = np.linalg.lstsq(a, np.ones_like(t), rcond=None)
    predicted = e * (c0 + c1 * f)
    for r, p in zip(rows, predicted):
        r["predicted"] = float(p)
    return float(c0), float(c1), float(np.max(np.abs(predicted - t) / t))


def slope(rows):
    """Показник степеня b у залежності t ~ N^b за трьома найбільшими N."""
    tail = sorted(rows, key=lambda r: r["n"])[-3:]
    x = np.log([r["n"] for r in tail])
    y = np.log([r["seconds"] for r in tail])
    return float(np.polyfit(x, y, 1)[0])


def plot(rows, engine_name, filename, title):
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.3))
    for structure in STRUCTURES:
        data = sorted((r for r in rows if r["structure"] == structure and r["engine"] == engine_name),
                      key=lambda r: r["n"])
        n = [r["n"] for r in data]
        color = COLORS[structure]
        axes[0].plot(n, [r["seconds"] for r in data], "o-", color=color, label=TITLES[structure])
        axes[0].plot(n, [r["predicted"] for r in data], "--", color=color, linewidth=1)
        axes[1].plot(n, [r["events"] for r in data], "o-", color=color, label=TITLES[structure])
        axes[2].plot(n, [r["seconds"] / r["events"] * 1e6 for r in data], "o-", color=color,
                     label=TITLES[structure])
    for ax in axes[:2]:
        ax.set_xscale("log")
        ax.set_yscale("log")
    axes[2].set_xlim(left=0)
    axes[0].set_ylabel("час обчислення, с")
    axes[1].set_ylabel(f"кількість подій за T = {TIME:.0f}")
    axes[2].set_ylabel("час на одну подію, мкс")
    for ax in axes:
        ax.set_xlabel("кількість СМО N")
        ax.grid(alpha=0.3, which="both")
        ax.legend(fontsize=8)
    fig.suptitle(title)
    fig.tight_layout()
    fig.savefig(RESULTS / filename, dpi=200)
    plt.close(fig)


def plot_engines(rows):
    fig, ax = plt.subplots(figsize=(8, 4.5))
    for engine_name, style in (("базовий алгоритм", "o-"), ("календар подій", "s--")):
        data = sorted((r for r in rows if r["structure"] == "chain" and r["engine"] == engine_name),
                      key=lambda r: r["n"])
        ax.plot([r["n"] for r in data], [r["seconds"] for r in data], style, label=engine_name)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel("кількість СМО N")
    ax.set_ylabel("час обчислення, с")
    ax.set_title("Послідовний ланцюг: базовий алгоритм і календар подій")
    ax.grid(alpha=0.3, which="both")
    ax.legend()
    fig.tight_layout()
    fig.savefig(RESULTS / "engines.png", dpi=200)
    plt.close(fig)


def main():
    RESULTS.mkdir(exist_ok=True)
    summary = {"demo": demo(), "time": TIME, "repeats": REPEATS,
               "python": sys.version.split()[0], "platform": platform.platform()}
    rows = []
    for engine, sizes in SIZES.items():
        for structure, values in sizes.items():
            for n in values:
                row = measure(structure, n, engine)
                rows.append(row)
                print(f"{row['engine']:17} {structure:9} N = {n:5}: подій {row['events']:8} "
                      f"(оцінка {row['expected_events']:9.0f}), час {row['seconds']:8.4f} с, "
                      f"{row['seconds'] / row['events'] * 1e6:6.2f} мкс/подію", flush=True)

    base = [r for r in rows if r["engine"] == "базовий алгоритм"]
    fast = [r for r in rows if r["engine"] == "календар подій"]
    summary["base_fit"] = dict(zip(("c0", "c1", "max_error"), fit_cost(base, lambda n: n + 2)))
    # у паралельній структурі на кожне надходження додається вибір одного з N маршрутів, тому її
    # вартість оцінюється окремо: t = E · c0 + λT · c1 · N
    summary["fast_fit"] = dict(zip(("c0", "c1", "max_error"),
                                   fit_cost([r for r in fast if r["structure"] != "parallel"],
                                            lambda n: math.log2(n + 2))))
    parallel = [r for r in fast if r["structure"] == "parallel"]
    summary["fast_parallel_fit"] = dict(zip(("c0", "c1", "max_error"),
                                            fit_cost(parallel, lambda n: n / 2)))
    summary["slopes"] = {engine: {s: slope([r for r in rows if r["engine"] == engine and r["structure"] == s])
                                  for s in STRUCTURES} for engine in ENGINE_NAMES.values()}
    summary["rows"] = rows

    print("\nМодель вартості базового алгоритму: t = E · (c0 + c1 · (N + 2)), "
          f"c0 = {summary['base_fit']['c0'] * 1e6:.3f} мкс, c1 = {summary['base_fit']['c1'] * 1e9:.2f} нс, "
          f"макс. відносна похибка {summary['base_fit']['max_error'] * 100:.1f} %")
    print("Модель вартості з календарем подій (ланцюг, дерево, повернення): t = E · (c0 + c1 · log2(N + 2)), "
          f"c0 = {summary['fast_fit']['c0'] * 1e6:.3f} мкс, c1 = {summary['fast_fit']['c1'] * 1e9:.2f} нс, "
          f"макс. відносна похибка {summary['fast_fit']['max_error'] * 100:.1f} %")
    pf = summary["fast_parallel_fit"]
    print("Модель вартості з календарем подій (паралельні гілки): t = E · (c0 + c1 · N / 2), "
          f"c0 = {pf['c0'] * 1e6:.3f} мкс, c1 = {pf['c1'] * 1e9:.2f} нс, "
          f"макс. відносна похибка {pf['max_error'] * 100:.1f} %")
    for engine, values in summary["slopes"].items():
        print(f"Показник степеня t ~ N^b ({engine}): " +
              ", ".join(f"{TITLES[s]} {b:.2f}" for s, b in values.items()))

    plot(rows, "базовий алгоритм", "complexity_base.png", "Базовий алгоритм імітації (пошук найближчої події "
         "перебором)")
    plot(rows, "календар подій", "complexity_fast.png", "Алгоритм з календарем подій (двійкова купа)")
    plot_engines(rows)
    with open(RESULTS / "complexity.json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
