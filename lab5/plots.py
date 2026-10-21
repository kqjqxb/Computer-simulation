"""Графіки результатів моделювання (за файлами results/task*.json)."""
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

RESULTS = Path(__file__).resolve().parent / "results"
BLUE, ORANGE, GRID = "#2a78d6", "#eb6834", "#d9d9d9"


def load(name):
    return json.loads((RESULTS / name).read_text(encoding="utf-8"))


def style(ax, xlabel, ylabel):
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(color=GRID, lw=0.6)
    ax.set_axisbelow(True)
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}".replace(".", ",")))
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)


def task1():
    hist = load("task1.json")["hist"]
    width, counts = 2 * hist["width"], hist["counts"]
    counts = [sum(counts[i:i + 2]) for i in range(0, len(counts), 2)]
    total = sum(counts)
    limit = int(80 / width)
    fig, ax = plt.subplots(figsize=(7, 3.4))
    ax.bar([i * width for i in range(limit)], [c / total / width for c in counts[:limit]], width=width * 0.9,
           align="edge", color=BLUE)
    style(ax, "час перебування деталі в системі, хв", "щільність")
    fig.tight_layout()
    fig.savefig(RESULTS / "task1_time.png", dpi=200)
    plt.close(fig)


def task2():
    sweep = load("task2.json")["sweep"]
    fig, axes = plt.subplots(1, 2, figsize=(10, 3.6))
    for variant, color in (("1", BLUE), ("2", ORANGE)):
        xs = [float(a) for a in sweep[variant]]
        rows = list(sweep[variant].values())
        axes[0].errorbar(xs, [r["throughput"][0] for r in rows], yerr=[r["throughput"][1] for r in rows],
                         color=color, marker="o", ms=4, lw=2, capsize=3, label=f"варіант {variant}")
        axes[1].errorbar(xs, [r["time_in_system"][0] for r in rows],
                         yerr=[r["time_in_system"][1] for r in rows],
                         color=color, marker="o", ms=4, lw=2, capsize=3, label=f"варіант {variant}")
    xs = [float(a) for a in sweep["1"]]
    axes[0].plot(xs, [3600 / a for a in xs], color="#888888", ls="--", lw=1,
                 label="інтенсивність надходження")
    style(axes[0], "середній інтервал надходження, с", "деталей за годину")
    style(axes[1], "середній інтервал надходження, с", "час перебування, с")
    axes[1].set_yscale("log")
    for ax in axes:
        ax.legend(frameon=False, fontsize=9)
    fig.tight_layout()
    fig.savefig(RESULTS / "task2_sweep.png", dpi=200)
    plt.close(fig)


def task3():
    sweep = load("task3.json")["sweep"]
    n = [r["n"] for r in sweep]
    fig, axes = plt.subplots(1, 2, figsize=(10, 3.6))
    axes[0].errorbar(n, [r["wait"][0] for r in sweep], yerr=[r["wait"][1] for r in sweep], color=BLUE,
                     marker="o", ms=3.5, lw=2, capsize=2)
    style(axes[0], "кількість місць n", "середнє очікування в черзі, хв")
    axes[1].errorbar(n, [r["revenue"][0] for r in sweep], yerr=[r["revenue"][1] for r in sweep], color=BLUE,
                     marker="o", ms=3.5, lw=2, capsize=2)
    axes[1].axhline(0, color="#888888", lw=1)
    style(axes[1], "кількість місць n", "виручка за день, грн")
    fig.tight_layout()
    fig.savefig(RESULTS / "task3_sweep.png", dpi=200)
    plt.close(fig)


def task4():
    points = load("task4.json")["trajectory"]
    t = [p[0] for p in points]
    fig, ax = plt.subplots(figsize=(10, 3.4))
    ax.step(t, [p[2] for p in points], where="post", color=ORANGE, lw=1.6, label="поточний стан запасу")
    ax.step(t, [p[1] for p in points], where="post", color=BLUE, lw=1.6, label="наявний запас")
    ax.axhline(18, color="#888888", ls="--", lw=1)
    ax.text(0.3, 19.5, "точка замовлення 18", fontsize=9, color="#555555")
    for k in range(4, 61, 4):
        ax.axvline(k, color=GRID, lw=0.6, zorder=0)
    style(ax, "час, тижні", "холодильників")
    ax.grid(False)
    ax.set_ylim(-3, 88)
    ax.legend(frameon=False, fontsize=9, loc="upper center", ncol=2)
    fig.tight_layout()
    fig.savefig(RESULTS / "task4_trajectory.png", dpi=200)
    plt.close(fig)


if __name__ == "__main__":
    task1()
    task2()
    task3()
    task4()
