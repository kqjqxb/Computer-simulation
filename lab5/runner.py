import statistics

from scipy import stats

import fun_rand


def replicate(run, seeds):
    """Виконує run() для кожного зерна генератора; повертає список результатів прогонів."""
    results = []
    for s in seeds:
        fun_rand.seed(s)
        results.append(run())
    return results


def summary(values):
    """Середнє та півширина 95 % довірчого інтервалу за t-розподілом Стьюдента."""
    values = list(values)
    mean = statistics.fmean(values)
    if len(values) < 2:
        return mean, 0.0
    return mean, stats.t.ppf(0.975, len(values) - 1) * statistics.stdev(values) / len(values) ** 0.5


def fmt(mean_ci, nd=4):
    mean, ci = mean_ci
    return f"{mean:.{nd}f} ± {ci:.{nd}f}"
