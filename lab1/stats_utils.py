import numpy as np
from scipy import stats


def describe(data):
    data = np.asarray(data, dtype=float)
    return {
        "n": data.size,
        "mean": data.mean(),
        "var": data.var(ddof=1),
        "std": data.std(ddof=1),
        "min": data.min(),
        "max": data.max(),
        "median": float(np.median(data)),
        "skew": float(stats.skew(data, bias=False)),
    }


def merge_small_bins(observed, expected, edges, min_count=5):
    """Об'єднує сусідні інтервали, поки в кожному очікувана й спостережувана кількість не стане
    не меншою за min_count."""
    observed, expected, edges = list(observed), list(expected), list(edges)
    i = 0
    while i < len(observed) and len(observed) > 1:
        if observed[i] >= min_count and expected[i] >= min_count:
            i += 1
            continue
        j = i + 1 if i + 1 < len(observed) else i - 1
        lo, hi = min(i, j), max(i, j)
        observed[lo] += observed[hi]
        expected[lo] += expected[hi]
        del observed[hi], expected[hi], edges[hi]
        i = lo
    return np.array(observed), np.array(expected), np.array(edges)


def chi_square(data, cdf, n_params=0, bins=20, edges=None, alpha=0.05, open_tails=True):
    """Критерій згоди χ² (формула 1.29) для неперервного закону з функцією розподілу cdf.

    Інтервали за замовчуванням рівні на [min; max] (формули 1.20-1.21). При open_tails крайні інтервали
    розширюються до меж носія закону, щоб сума теоретичних імовірностей дорівнювала 1.
    """
    data = np.asarray(data, dtype=float)
    n = data.size
    if edges is None:
        edges = np.linspace(data.min(), data.max(), bins + 1)
    edges = np.asarray(edges, dtype=float)
    observed, _ = np.histogram(data, bins=edges)

    cdf_values = cdf(edges)
    if open_tails:
        cdf_values[0], cdf_values[-1] = 0.0, 1.0
    expected = n * np.diff(cdf_values)

    observed, expected, merged_edges = merge_small_bins(observed, expected, edges)
    k = observed.size
    statistic = float(np.sum((observed - expected) ** 2 / expected))
    dof = k - 1 - n_params
    critical = float(stats.chi2.ppf(1 - alpha, dof))
    return {
        "chi2": statistic,
        "critical": critical,
        "dof": dof,
        "k": k,
        "p_value": float(stats.chi2.sf(statistic, dof)),
        "accepted": statistic < critical,
        "observed": observed,
        "expected": expected,
        "edges": merged_edges,
    }


def correlation_test(z, max_lag=10):
    """Перевірка незалежності за формулами (1.6)-(1.7): ρ_j та A_j = ρ_j / sqrt(D(ρ_j))."""
    z = np.asarray(z, dtype=float)
    rows = []
    for j in range(1, max_lag + 1):
        h = z.size - j
        rho = 12.0 / h * np.dot(z[:h], z[j:]) - 3.0
        a = rho / np.sqrt((13 * h - 6) / h ** 2)
        rows.append((j, rho, a))
    return rows


RUNS_A = np.array([
    [4529.4, 9044.9, 13568, 18091, 22615, 27892],
    [9044.9, 18097, 27139, 36187, 45234, 55789],
    [13568, 27139, 40721, 54281, 67852, 83685],
    [18091, 36187, 54281, 72414, 90470, 111580],
    [22615, 45234, 67852, 90470, 113262, 139476],
    [27892, 55789, 83685, 111580, 139476, 172860],
])
RUNS_B = np.array([1 / 6, 5 / 24, 11 / 120, 19 / 720, 29 / 5040, 1 / 840])


def runs_up_test(z):
    """Критерій серій (формула 1.4): довжини зростаючих серій, R порівнюється з χ²(6; 0.9) = 10.6."""
    z = np.asarray(z, dtype=float)
    counts = np.zeros(6)
    length = 1
    for prev, cur in zip(z[:-1], z[1:]):
        if cur > prev:
            length += 1
        else:
            counts[min(length, 6) - 1] += 1
            length = 1
    counts[min(length, 6) - 1] += 1
    n = z.size
    d = counts - n * RUNS_B
    r = float(d @ RUNS_A @ d / n)
    return {"runs": counts.astype(int), "R": r, "critical": 10.6, "accepted": r < 10.6}
