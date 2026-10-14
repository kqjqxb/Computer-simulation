import statistics

import fun_rand

# квантилі t-розподілу Стьюдента рівня 0.975 за кількістю ступенів вільності
T_95 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 19: 2.093}


def replicate(build_model, time, seeds, warmup=0.0, collect=None):
    """Запускає модель з кількома зернами генератора; повертає collect(model) для кожного прогону
    (за замовчуванням – model.results())."""
    runs = []
    for s in seeds:
        fun_rand.seed(s)
        model = build_model()
        model.simulate(time, warmup=warmup)
        runs.append(collect(model) if collect else model.results())
    return runs


def summary(values):
    """Середнє та півширина 95 % довірчого інтервалу за t-розподілом Стьюдента."""
    mean = statistics.mean(values)
    if len(values) < 2:
        return mean, 0.0
    return mean, T_95.get(len(values) - 1, 1.96) * statistics.stdev(values) / len(values) ** 0.5


def metric(runs, path):
    values = []
    for r in runs:
        value = r
        for key in path:
            value = value[key]
        values.append(value)
    return summary(values)
