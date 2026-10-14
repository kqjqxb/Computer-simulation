"""Завдання 1: експоненційний генератор (метод оберненої функції)
та мультиплікативний конгруентний генератор."""
import json
import math
import random
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats

from stats_utils import chi_square, correlation_test, describe, runs_up_test

N = 10_000
BINS = 20
SEED = 2026
REPEATS = 200
RESULTS = Path(__file__).resolve().parent / "results"

LAMBDAS = [0.5, 1.0, 2.0, 5.0]

LCG_PARAMS = [
    ("5^13", 5 ** 13, "2^31", 2 ** 31, "параметри завдання"),
    ("5^13", 5 ** 13, "2^40", 2 ** 40, "більший модуль"),
    ("5^17", 5 ** 17, "2^48", 2 ** 48, "більші a і c"),
    ("65539", 65539, "2^31", 2 ** 31, "RANDU"),
    ("5", 5, "2^31", 2 ** 31, "малий множник"),
    ("5^13", 5 ** 13, "2^12", 2 ** 12, "малий модуль"),
    ("6", 6, "2^31", 2 ** 31, "парний множник"),
]


def tex(label):
    base, _, power = label.partition("^")
    return f"${base}^{{{power}}}$" if power else f"${base}$"


def exponential_sample(lam, n, rng):
    # 1 - random() лежить у (0; 1], тож логарифм завжди визначений
    return np.array([-math.log(1.0 - rng.random()) / lam for _ in range(n)])


def lcg(a, c, z0, n):
    z = z0
    out = np.empty(n)
    for i in range(n):
        z = a * z % c
        out[i] = z / c
    return out


def lcg_period(a, c, z0, limit=10 ** 7):
    """Довжина періоду послідовності z_i. Для непарного a і c = 2^B період дорівнює порядку a за модулем c."""
    is_power_of_two = c & (c - 1) == 0
    if a % 2 == 1 and z0 % 2 == 1 and is_power_of_two:
        order, power = 1, a % c
        while power != 1:
            power = power * power % c
            order *= 2
        return order
    seen = {}
    z = z0
    for i in range(limit):
        if z in seen:
            return i - seen[z]
        seen[z] = i
        z = a * z % c
    return None


def exponential_part(rng):
    rows = []
    fig, axes = plt.subplots(2, 2, figsize=(10, 7))
    for lam, ax in zip(LAMBDAS, axes.flat):
        x = exponential_sample(lam, N, rng)
        d = describe(x)
        lam_hat = 1 / d["mean"]
        test = chi_square(x, lambda t: stats.expon.cdf(t, scale=1 / lam_hat), n_params=1, bins=BINS)

        accepted = 0
        for _ in range(REPEATS):
            y = exponential_sample(lam, N, rng)
            repeat = chi_square(y, lambda t: stats.expon.cdf(t, scale=y.mean()), n_params=1, bins=BINS)
            accepted += repeat["accepted"]

        rows.append({
            "lambda": lam, "mean": d["mean"], "var": d["var"],
            "theory_mean": 1 / lam, "theory_var": 1 / lam ** 2,
            "lambda_hat": lam_hat, "chi2": test["chi2"], "critical": test["critical"], "dof": test["dof"],
            "k": test["k"], "p_value": test["p_value"], "accepted": bool(test["accepted"]),
            "accept_rate": accepted / REPEATS,
            "table": [[float(test["edges"][i]), float(test["edges"][i + 1]), int(o), float(e)]
                      for i, (o, e) in enumerate(zip(test["observed"], test["expected"]))],
        })

        counts, edges, _ = ax.hist(x, bins=BINS, color="#7da7d9", edgecolor="black", linewidth=0.6,
                                   label="спостережувані частоти")
        centers = (edges[:-1] + edges[1:]) / 2
        ax.plot(centers, N * np.diff(stats.expon.cdf(edges, scale=1 / lam_hat)), "o-", color="#c0392b",
                markersize=3.5, linewidth=1.4, label=f"$np_i^T$, λ̂ = {lam_hat:.4f}")
        ax.set_title(f"λ = {lam}")
        ax.set_xlabel("x")
        ax.set_ylabel("кількість влучень $n_i$")
        ax.grid(alpha=0.3)
        ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(RESULTS / "task1_exponential_hist.png", dpi=200)
    plt.close(fig)
    return rows


def lcg_part(rng):
    rows = []
    samples = {}
    for a_label, a, c_label, c, note in LCG_PARAMS:
        z0 = 1
        x = lcg(a, c, z0, N)
        samples[(a_label, c_label)] = x
        d = describe(x)
        test = chi_square(x, lambda t: np.clip(t, 0, 1), bins=BINS, edges=np.linspace(0, 1, BINS + 1))
        corr = correlation_test(x)
        runs = runs_up_test(x)
        period = lcg_period(a, c, z0)

        accepted = np.zeros(3)
        for _ in range(REPEATS):
            y = lcg(a, c, rng.randrange(1, c, 2), N)
            accepted += [
                chi_square(y, lambda t: np.clip(t, 0, 1), edges=np.linspace(0, 1, BINS + 1))["accepted"],
                all(abs(A) < 1.645 for _, _, A in correlation_test(y)),
                runs_up_test(y)["accepted"],
            ]
        rates = accepted / REPEATS

        rows.append({
            "a": a_label, "c": c_label, "note": note, "mean": d["mean"], "var": d["var"],
            "distinct": int(np.unique(x).size), "period": period,
            "chi2": test["chi2"], "critical": test["critical"], "dof": test["dof"], "k": test["k"],
            "p_value": test["p_value"], "accepted": bool(test["accepted"]), "accept_rate": rates[0],
            "corr_rate": rates[1], "runs_rate": rates[2],
            "max_abs_A": float(max(abs(r[2]) for r in corr)),
            "corr": [[j, float(r), float(A)] for j, r, A in corr],
            "runs_R": runs["R"], "runs_ok": bool(runs["accepted"]), "runs": runs["runs"].tolist(),
            "counts": [int(v) for v in np.histogram(x, bins=np.linspace(0, 1, BINS + 1))[0]],
        })

    fig, axes = plt.subplots(2, 2, figsize=(10, 7))
    shown = [("5^13", "2^31"), ("5^13", "2^40"), ("5^17", "2^48"), ("65539", "2^31")]
    for (a_label, c_label), ax in zip(shown, axes.flat):
        ax.hist(samples[(a_label, c_label)], bins=np.linspace(0, 1, BINS + 1), color="#8fcf9f",
                edgecolor="black", linewidth=0.6)
        ax.axhline(N / BINS, color="#c0392b", linestyle="--", linewidth=1.4, label="N/m = 500")
        ax.set_ylim(0, 650)
        ax.set_title(f"a = {tex(a_label)}, c = {tex(c_label)}")
        ax.set_xlabel("x")
        ax.set_ylabel("кількість влучень $n_i$")
        ax.grid(alpha=0.3)
        ax.legend(fontsize=8, loc="lower right")
    fig.tight_layout()
    fig.savefig(RESULTS / "task1_lcg_hist.png", dpi=200)
    plt.close(fig)

    fig, axes = plt.subplots(1, 3, figsize=(12, 4.2))
    for (a_label, c_label), ax in zip([("5^13", "2^31"), ("5", "2^31"), ("5^13", "2^12")], axes):
        x = samples[(a_label, c_label)]
        ax.scatter(x[:-1], x[1:], s=0.6, color="#2c5d8f")
        ax.set_title(f"a = {tex(a_label)}, c = {tex(c_label)}")
        ax.set_xlabel("$x_i$")
        ax.set_ylabel("$x_{i+1}$")
        ax.set_aspect("equal")
    fig.tight_layout()
    fig.savefig(RESULTS / "task1_lcg_pairs.png", dpi=200)
    plt.close(fig)

    fig, axes = plt.subplots(1, 2, figsize=(11, 4))
    for (a_label, c_label), ax in zip([("5^13", "2^31"), ("65539", "2^31")], axes):
        x = samples[(a_label, c_label)]
        s = 9 * x[:-2] - 6 * x[1:-1] + x[2:]
        ax.scatter(x[:-2], s, s=0.6, color="#2c5d8f")
        ax.set_title(f"a = {tex(a_label)}, c = {tex(c_label)}")
        ax.set_xlabel("$x_i$")
        ax.set_ylabel("$9x_i - 6x_{i+1} + x_{i+2}$")
        ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(RESULTS / "task1_lcg_triples.png", dpi=200)
    plt.close(fig)
    return rows


def print_report(exp_rows, lcg_rows):
    print(f"ЕКСПОНЕНЦІЙНИЙ ГЕНЕРАТОР x = -ln(ξ)/λ, N = {N}, α = 0.05")
    print(f"{'λ':>5} {'середнє':>9} {'1/λ':>7} {'дисперсія':>10} {'1/λ²':>7} {'λ̂':>8} "
          f"{'k':>3} {'ст.в.':>5} {'χ²':>8} {'χ²кр':>7} {'p':>6}  рішення   частка прийнять")
    for r in exp_rows:
        print(f"{r['lambda']:>5} {r['mean']:>9.5f} {r['theory_mean']:>7.4f} {r['var']:>10.5f} "
              f"{r['theory_var']:>7.4f} {r['lambda_hat']:>8.5f} {r['k']:>3} {r['dof']:>5} {r['chi2']:>8.3f} "
              f"{r['critical']:>7.3f} {r['p_value']:>6.3f}  {'прийнято' if r['accepted'] else 'відхилено':9} "
              f"{r['accept_rate']:.3f}")

    print(f"\nКОНГРУЕНТНИЙ ГЕНЕРАТОР z(i+1) = a·z(i) mod c, x = z/c, z0 = 1, N = {N}, m = {BINS}, α = 0.05")
    print(f"{'a':>6} {'c':>5} {'середнє':>9} {'дисперсія':>10} {'різних':>7} {'період':>12} "
          f"{'χ²':>9} {'χ²кр':>7} {'p':>6}  {'рішення':9} {'max|A|':>8} {'R':>9}  "
          "частка прийнять χ²/кор./серії")
    for r in lcg_rows:
        print(f"{r['a']:>6} {r['c']:>5} {r['mean']:>9.5f} {r['var']:>10.5f} {r['distinct']:>7} "
              f"{str(r['period']):>12} {r['chi2']:>9.2f} {r['critical']:>7.3f} {r['p_value']:>6.3f}  "
              f"{'прийнято' if r['accepted'] else 'відхилено':9} {r['max_abs_A']:>8.2f} {r['runs_R']:>9.2f}  "
              f"{r['accept_rate']:.3f}/{r['corr_rate']:.3f}/{r['runs_rate']:.3f}")
    print("Теоретично: середнє 0.5, дисперсія 1/12 = 0.08333; |A_j| < 1.645 (j = 1..10); R < 10.6")
    print(f"Частки прийнять обчислено за {REPEATS} вибірками з різними непарними z0")


def main():
    RESULTS.mkdir(exist_ok=True)
    rng = random.Random(SEED)
    exp_rows = exponential_part(rng)
    lcg_rows = lcg_part(rng)
    print_report(exp_rows, lcg_rows)
    with open(RESULTS / "task1.json", "w", encoding="utf-8") as f:
        json.dump({"exponential": exp_rows, "lcg": lcg_rows}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
