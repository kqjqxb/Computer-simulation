"""Завдання 2, крок 2: ідентифікація закону розподілу часу сортування та імітаційні генератори."""
import csv
import json
import random
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats

from stats_utils import chi_square, describe

HERE = Path(__file__).resolve().parent
DATA = HERE / "data" / "sort_times.csv"
RESULTS = HERE / "results"
BINS = 20
GENERATED = 10_000
SEED = 7

CANDIDATES = [
    ("нормальний", stats.norm),
    ("логнормальний зі зсувом", stats.lognorm),
    ("гамма (Ерланга) зі зсувом", stats.gamma),
    ("Вейбулла зі зсувом", stats.weibull_min),
]
TABLE_PROBS = np.unique(np.concatenate([np.linspace(0, 1, 21), [0.97, 0.98, 0.99, 0.995]]))


def load_times():
    with open(DATA, newline="") as f:
        return np.array([float(row["seconds"]) for row in csv.DictReader(f)])


def fit(dist, t):
    if dist is stats.norm:
        return dist.fit(t)
    # початкові наближення: зсув трохи нижче мінімуму, інакше MLE часто збігається до виродженого розв'язку
    loc = t.min() - 0.5 * t.std()
    if dist is stats.lognorm:
        return dist.fit(t, 0.8, loc=loc, scale=np.median(t) - loc)
    return dist.fit(t, 1.0, loc=loc, scale=t.std())


def identify(t):
    rows = []
    for name, dist in CANDIDATES:
        params = fit(dist, t)
        test = chi_square(t, lambda x, d=dist, p=params: d.cdf(x, *p), n_params=len(params), bins=BINS)
        rows.append({"law": name, "dist": dist, "params": params, **test})
    return rows


class TableGenerator:
    """Табличний метод (формули 1.13-1.14): кусково-лінійна апроксимація емпіричної функції розподілу."""

    def __init__(self, sample, probs=TABLE_PROBS):
        self.a = np.asarray(probs)
        self.x = np.quantile(sample, self.a)

    def __call__(self, rng):
        z = rng.random()
        i = int(np.searchsorted(self.a, z, side="left"))
        i = min(max(i, 1), len(self.a) - 1)
        return self.x[i - 1] + (self.x[i] - self.x[i - 1]) / (self.a[i] - self.a[i - 1]) * (z - self.a[i - 1])

    def cdf(self, t):
        return np.interp(t, self.x, self.a, left=0.0, right=1.0)


class InverseGenerator:
    """Метод оберненої функції: r = F^(-1)(ξ) для підібраного параметричного закону."""

    def __init__(self, dist, params):
        self.dist, self.params = dist, params

    def __call__(self, rng):
        return float(self.dist.ppf(1.0 - rng.random(), *self.params))

    def cdf(self, t):
        return self.dist.cdf(t, *self.params)


def generate(generator, n, seed):
    rng = random.Random(seed)
    return np.array([generator(rng) for _ in range(n)])


def homogeneity(real, simulated, bins=BINS):
    """Двовибірковий χ² (однорідність) на спільних інтервалах
    та двовибірковий критерій Колмогорова-Смірнова."""
    edges = np.quantile(real, np.linspace(0, 1, bins + 1))
    edges[0], edges[-1] = -np.inf, np.inf
    obs_real = np.histogram(real, bins=edges)[0]
    obs_sim = np.histogram(simulated, bins=edges)[0]
    table = np.vstack([obs_real, obs_sim])
    table = table[:, table.sum(axis=0) > 0]
    chi2, p, dof, _ = stats.chi2_contingency(table)
    ks = stats.ks_2samp(real, simulated)
    return {"chi2": float(chi2), "dof": int(dof), "critical": float(stats.chi2.ppf(0.95, dof)), "p": float(p),
            "ks": float(ks.statistic), "ks_p": float(ks.pvalue)}


def accuracy(real, simulated):
    def q(x, p):
        return float(np.quantile(x, p))

    metrics = [("середнє", np.mean), ("ст. відхилення", lambda x: np.std(x, ddof=1)),
               ("мінімум", np.min), ("медіана", lambda x: q(x, 0.5)), ("90 %-квантиль", lambda x: q(x, 0.9)),
               ("99 %-квантиль", lambda x: q(x, 0.99))]
    return [(name, float(f(real)), float(f(simulated)), abs(f(simulated) - f(real)) / f(real) * 100)
            for name, f in metrics]


def plot_series(t):
    fig, ax = plt.subplots(figsize=(10, 3.4))
    ax.plot(np.arange(1, t.size + 1), t * 1000, ".", markersize=3, color="#2c5d8f")
    ax.axhline(t.mean() * 1000, color="#c0392b", linewidth=1.2, label=f"середнє {t.mean() * 1000:.1f} мс")
    ax.set_xlabel("номер прогону")
    ax.set_ylabel("час сортування, мс")
    ax.grid(alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(RESULTS / "task2_series.png", dpi=200)
    plt.close(fig)


def plot_histogram(t, rows):
    fig, ax = plt.subplots(figsize=(10, 4.8))
    counts, edges, _ = ax.hist(t * 1000, bins=BINS, color="#7da7d9", edgecolor="black", linewidth=0.6,
                               label="спостережувані частоти $n_i$")
    centers = (edges[:-1] + edges[1:]) / 2 / 1000
    colors = ["#7f8c8d", "#c0392b", "#27ae60", "#8e44ad"]
    for row, color in zip(rows, colors):
        expected = t.size * np.diff(row["dist"].cdf(edges / 1000, *row["params"]))
        ax.plot(centers * 1000, expected, "o-", markersize=3, linewidth=1.3, color=color, label=row["law"])
    ax.set_xlabel("час сортування, мс")
    ax.set_ylabel("кількість влучень")
    ax.grid(alpha=0.3)
    ax.legend(fontsize=9)
    fig.tight_layout()
    fig.savefig(RESULTS / "task2_histogram.png", dpi=200)
    plt.close(fig)


def plot_comparison(t, simulated):
    lo, hi = t.min(), np.quantile(t, 0.995)
    edges = np.linspace(lo, hi, 31)
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2))
    ax = axes[0]
    ax.hist(t * 1000, bins=edges * 1000, density=True, alpha=0.55, color="#2c5d8f", label="реальні заміри")
    for label, sample, color in simulated:
        ax.hist(sample * 1000, bins=edges * 1000, density=True, histtype="step", linewidth=1.6, color=color,
                label=label)
    ax.set_xlabel("час сортування, мс")
    ax.set_ylabel("відносна частота / мс")
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8)

    ax = axes[1]
    ax.plot(np.sort(t) * 1000, np.arange(1, t.size + 1) / t.size, color="#2c5d8f", linewidth=2.2,
            label="реальні заміри")
    for label, sample, color in simulated:
        ecdf = np.arange(1, sample.size + 1) / sample.size
        ax.plot(np.sort(sample) * 1000, ecdf, color=color, linewidth=1.2, label=label)
    ax.set_xlim(lo * 1000, hi * 1000)
    ax.set_xlabel("час сортування, мс")
    ax.set_ylabel("емпірична функція розподілу")
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8, loc="lower right")
    fig.tight_layout()
    fig.savefig(RESULTS / "task2_comparison.png", dpi=200)
    plt.close(fig)


def main():
    RESULTS.mkdir(exist_ok=True)
    t = load_times()
    d = describe(t)
    print(f"ЧАС СОРТУВАННЯ 1 000 000 ОБ'ЄКТІВ: n = {d['n']}")
    print(f"середнє = {d['mean'] * 1000:.3f} мс, ст. відхилення = {d['std'] * 1000:.3f} мс, "
          f"дисперсія = {d['var'] * 1e6:.4f} мс², медіана = {d['median'] * 1000:.3f} мс")
    print(f"мінімум = {d['min'] * 1000:.3f} мс, максимум = {d['max'] * 1000:.3f} мс, "
          f"асиметрія = {d['skew']:.3f}, "
          f"коефіцієнт варіації = {d['std'] / d['mean'] * 100:.2f} %")

    rows = identify(t)
    print(f"\nПЕРЕВІРКА ГІПОТЕЗ (χ², {BINS} інтервалів до об'єднання, α = 0.05)")
    for r in rows:
        params = ", ".join(f"{v:.6g}" for v in r["params"])
        print(f"{r['law']:28} параметри ({params}); k = {r['k']}, ст.в. = {r['dof']}, χ² = {r['chi2']:.2f}, "
              f"χ²кр = {r['critical']:.2f}, p = {r['p_value']:.4f} -> "
              f"{'прийнято' if r['accepted'] else 'відхилено'}")
    best = max(rows, key=lambda r: r["p_value"])
    print(f"Найкраща відповідність: {best['law']}")

    plot_series(t)
    plot_histogram(t, rows)

    table_gen = TableGenerator(t)
    inverse_gen = InverseGenerator(best["dist"], best["params"])
    generators = [("табличний метод", table_gen, "#c0392b"), (f"{best['law']}", inverse_gen, "#27ae60")]

    print("\nТАБЛИЦЯ ТАБЛИЧНОГО ГЕНЕРАТОРА (a_i = F(x_i), x_i у мс):")
    print("  ".join(f"{a:.3f}:{x * 1000:.2f}" for a, x in zip(table_gen.a, table_gen.x)))

    simulated, evaluation = [], []
    for i, (label, gen, color) in enumerate(generators):
        sample = generate(gen, GENERATED, SEED + i)
        simulated.append((label, sample, color))
        hom = homogeneity(t, sample)
        acc = accuracy(t, sample)
        self_test = chi_square(t, gen.cdf, bins=BINS)
        evaluation.append({"label": label, "homogeneity": hom, "accuracy": acc,
                           "fit_chi2": self_test["chi2"], "fit_critical": self_test["critical"],
                           "fit_dof": self_test["dof"]})
        print(f"\nГЕНЕРАТОР «{label}», згенеровано {GENERATED} значень:")
        print(f"{'характеристика':16} {'реальні, мс':>12} {'імітація, мс':>13} {'похибка, %':>11}")
        for name, real_v, sim_v, err in acc:
            print(f"{name:16} {real_v * 1000:>12.3f} {sim_v * 1000:>13.3f} {err:>11.2f}")
        print(f"χ² однорідності = {hom['chi2']:.2f} (ст.в. {hom['dof']}, χ²кр = {hom['critical']:.2f}, "
              f"p = {hom['p']:.3f}); КС: D = {hom['ks']:.4f}, p = {hom['ks_p']:.3f}")

    first, second = t[: t.size // 2], t[t.size // 2:]
    drift = stats.mannwhitneyu(first, second)
    drift_ks = stats.ks_2samp(first, second)
    print(f"\nСТАЦІОНАРНІСТЬ: прогони 1-{first.size}: середнє {first.mean() * 1000:.2f} мс, "
          f"ст. відх. {first.std(ddof=1) * 1000:.2f} мс; прогони {first.size + 1}-{t.size}: середнє "
          f"{second.mean() * 1000:.2f} мс, ст. відх. {second.std(ddof=1) * 1000:.2f} мс")
    print(f"Манна-Вітні p = {drift.pvalue:.2e}, КС p = {drift_ks.pvalue:.2e}")

    train, test = t[0::2], t[1::2]
    print(f"\nПЕРЕХРЕСНА ПЕРЕВІРКА: побудова за непарними прогонами ({train.size}), "
          f"порівняння з парними ({test.size})")
    best_dist = best["dist"]
    cross = []
    for label, gen in [("табличний метод", TableGenerator(train)),
                       (best["law"], InverseGenerator(best_dist, fit(best_dist, train)))]:
        sample = generate(gen, GENERATED, SEED + 10)
        hom = homogeneity(test, sample)
        err = abs(sample.mean() - test.mean()) / test.mean() * 100
        cross.append({"label": label, "homogeneity": hom, "mean_error": err})
        print(f"{label:28} похибка середнього = {err:.2f} %, χ² = {hom['chi2']:.2f} "
              f"(χ²кр = {hom['critical']:.2f}, "
              f"p = {hom['p']:.3f}), КС p = {hom['ks_p']:.3f}")

    plot_comparison(t, simulated)

    with open(RESULTS / "task2.json", "w", encoding="utf-8") as f:
        json.dump({
            "describe": {k: float(v) for k, v in d.items()},
            "laws": [{"law": r["law"], "params": [float(v) for v in r["params"]], "chi2": r["chi2"],
                      "critical": r["critical"], "dof": r["dof"], "k": r["k"], "p": r["p_value"],
                      "accepted": bool(r["accepted"])} for r in rows],
            "best": best["law"],
            "table": [[float(a), float(x)] for a, x in zip(table_gen.a, table_gen.x)],
            "evaluation": evaluation, "cross": cross,
            "drift": {"first_mean": float(first.mean()), "first_std": float(first.std(ddof=1)),
                      "second_mean": float(second.mean()), "second_std": float(second.std(ddof=1)),
                      "mw_p": float(drift.pvalue), "ks_p": float(drift_ks.pvalue)},
        }, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
