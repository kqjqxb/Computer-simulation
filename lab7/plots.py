"""Графіки результатів практикуму 7 і порівняння з практикумом 5."""
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

HERE = Path(__file__).resolve().parent
RESULTS = HERE / "results"
LAB5 = HERE.parent / "lab5" / "results"
BLUE, ORANGE, GRAY, INK = "#2a78d6", "#eb6834", "#7a7974", "#52514e"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def style(ax):
    ax.grid(True, color="#e4e3df", linewidth=0.6)
    ax.set_axisbelow(True)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    ax.tick_params(colors=INK, labelsize=9)


def minibus():
    t5, t7 = load(LAB5 / "task3.json"), load(RESULTS / "task3.json")
    fig, (left, right) = plt.subplots(1, 2, figsize=(11, 4.2))
    for ax, key, title, label in ((left, "wait", "Середній час очікування пасажира", "хв"),
                                  (right, "revenue", "Виручка за день", "грн")):
        for data, color, marker, name, get in ((t5, GRAY, "s", "ЛР5 (мережа Петрі)", lambda r: r[key]),
                                               (t7, BLUE, "o", "ЛР7 (Петрі-об'єктна модель)",
                                                lambda r: (r[key]["mean"], r[key]["ci"]))):
            n = [r["n"] for r in data["sweep"]]
            y = [get(r) for r in data["sweep"]]
            new = color == BLUE
            ax.errorbar(n, [v[0] for v in y], yerr=[v[1] for v in y], color=color, marker=marker,
                        markersize=5 if new else 7, linewidth=2 if new else 1.2, capsize=2, label=name,
                        markerfacecolor=color if new else "none")
        ax.set_xlabel("кількість місць n", color=INK)
        ax.set_ylabel(label, color=INK)
        ax.set_title(title, fontsize=11)
        ax.legend(frameon=False, fontsize=9)
        style(ax)
    fig.tight_layout()
    fig.savefig(RESULTS / "minibus.png", dpi=200)
    plt.close(fig)


def fms():
    t5, t7 = load(LAB5 / "task2.json"), load(RESULTS / "task2.json")
    fig, ax = plt.subplots(figsize=(7, 4.2))
    for variant, color in (("1", BLUE), ("2", ORANGE)):
        keys = list(t7["sweep"][variant])
        x = [float(k) for k in keys] + [40.0]
        y7 = [t7["sweep"][variant][k]["throughput"] for k in keys] + [t7["base"][variant]["throughput"]]
        y5 = [t5["sweep"][variant][k]["throughput"] for k in keys] + [t5["base"][variant]["throughput"]]
        order = sorted(range(len(x)), key=lambda i: x[i])
        ax.errorbar([x[i] for i in order], [y7[i]["mean"] for i in order], yerr=[y7[i]["ci"] for i in order],
                    color=color, marker="o", markersize=5, linewidth=2, capsize=2,
                    label=f"варіант {variant}, ЛР7")
        ax.plot([x[i] for i in order], [y5[i][0] for i in order], color=color, marker="s", markersize=7,
                markerfacecolor="none", linestyle="none", label=f"варіант {variant}, ЛР5")
    ax.set_xlabel("середній інтервал надходження деталей, с", color=INK)
    ax.set_ylabel("пропускна здатність, деталей/год", color=INK)
    ax.set_title("Пропускна здатність ГВС", fontsize=11)
    ax.legend(frameon=False, fontsize=9)
    style(ax)
    fig.tight_layout()
    fig.savefig(RESULTS / "fms.png", dpi=200)
    plt.close(fig)


def conveyor():
    t7 = load(RESULTS / "task4.json")
    fig, ax = plt.subplots(figsize=(7, 4.2))
    for key, color, name in (("return_first", BLUE, "пріоритет об'єкта П5 вищий (як у ЛР5)"),
                             ("equal", ORANGE, "однакові пріоритети об'єктів")):
        s = t7["order"][key]["times"]["survival"]
        t = [2 * i for i in range(len(s))]
        pts = [(a, b) for a, b in zip(t, s) if b > 0]
        ax.plot(*zip(*pts), color=color, linewidth=2, label=name)
    ax.set_yscale("log")
    ax.set_xlabel("t, хв", color=INK)
    ax.set_ylabel("частка деталей з часом перебування > t", color=INK)
    ax.set_title("Хвіст розподілу часу перебування деталі", fontsize=11)
    ax.legend(frameon=False, fontsize=9)
    style(ax)
    fig.tight_layout()
    fig.savefig(RESULTS / "conveyor_order.png", dpi=200)
    plt.close(fig)


def main():
    minibus()
    fms()
    conveyor()
    print(RESULTS / "minibus.png", RESULTS / "fms.png", RESULTS / "conveyor_order.png")


if __name__ == "__main__":
    main()
