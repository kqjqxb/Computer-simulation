"""Графіки результатів моделі серверів (завдання 3) за results/task3.json."""
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

RESULTS = Path(__file__).resolve().parent / "results"
BLUE, ORANGE, GRAY, INK = "#2a78d6", "#eb6834", "#7a7974", "#52514e"


def style(ax):
    ax.grid(True, color="#e4e3df", linewidth=0.6)
    ax.set_axisbelow(True)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    ax.tick_params(colors=INK, labelsize=9)


def main():
    data = json.loads((RESULTS / "task3.json").read_text(encoding="utf-8"))
    fig, (left, right) = plt.subplots(1, 2, figsize=(11, 4.2))

    load = data["load"]
    for threads, color, marker in (([32, 16], BLUE, "o"), ([12, 6], ORANGE, "s")):
        rows = [r for r in load if r["threads"] == threads]
        x = [r["rate"] for r in rows]
        y = [r["response"]["mean"] for r in rows]
        e = [r["response"]["ci"] for r in rows]
        left.errorbar(x, y, yerr=e, color=color, marker=marker, markersize=6, linewidth=2, capsize=3,
                      label=f"потоків С1/С2 = {threads[0]}/{threads[1]}")
    theory = [(r["rate"], r["theory"]) for r in load if r["threads"] == [32, 16] and r["theory"] is not None]
    left.plot(*zip(*theory), color=GRAY, linestyle="--", linewidth=1.5, label="мережа Джексона", zorder=4)
    left.set_yscale("log")
    left.set_xlabel("інтенсивність надходження, запитів/с", color=INK)
    left.set_ylabel("середній час відповіді, мс", color=INK)
    left.set_title("Час відповіді залежно від навантаження", fontsize=11)
    left.legend(frameon=False, fontsize=9)
    style(left)

    rows = data["threads"]
    x = [r["threads"] for r in rows]
    right.errorbar(x, [r["response"]["mean"] for r in rows], yerr=[r["response"]["ci"] for r in rows],
                   color=BLUE, marker="o", markersize=6, linewidth=2, capsize=3, label="модель")
    right.axhline(data["analytic"][0], color=GRAY, linestyle="--", linewidth=1.5, label="мережа Джексона",
                  zorder=4)
    right.set_yscale("log")
    right.set_xlabel("кількість потоків головного сервера", color=INK)
    right.set_ylabel("час відповіді С1, мс", color=INK)
    right.set_title("Час відповіді С1 при 200 запитах/с", fontsize=11)
    right.legend(frameon=False, fontsize=9)
    style(right)

    fig.tight_layout()
    fig.savefig(RESULTS / "servers.png", dpi=200)
    print(RESULTS / "servers.png")


if __name__ == "__main__":
    main()
