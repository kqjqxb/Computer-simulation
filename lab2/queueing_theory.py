"""Аналітичні формули теорії масового обслуговування для верифікації імітаційної моделі."""
import math


def mmck(arrival_rate, service_mean, channels=1, capacity_queue=math.inf):
    """Система M/M/c/K: c каналів, черга до capacity_queue місць (K = c + capacity_queue)."""
    a = arrival_rate * service_mean
    c = channels
    if capacity_queue == math.inf:
        rho = a / c
        if rho >= 1:
            raise ValueError("система нестаціонарна: ρ ≥ 1")
        p0 = 1 / (sum(a ** n / math.factorial(n) for n in range(c))
                  + a ** c / (math.factorial(c) * (1 - rho)))
        lq = p0 * a ** c * rho / (math.factorial(c) * (1 - rho) ** 2)
        return {"failure_probability": 0.0, "mean_queue": lq, "mean_load": rho,
                "mean_in_system": lq + a, "throughput": arrival_rate}

    k = c + int(capacity_queue)
    weights = [a ** n / math.factorial(n) if n <= c else a ** n / (math.factorial(c) * c ** (n - c))
               for n in range(k + 1)]
    total = sum(weights)
    p = [w / total for w in weights]
    p_fail = p[k]
    lq = sum((n - c) * p[n] for n in range(c + 1, k + 1))
    busy = sum(min(n, c) * p[n] for n in range(k + 1))
    return {"failure_probability": p_fail, "mean_queue": lq, "mean_load": busy / c,
            "mean_in_system": lq + busy, "throughput": arrival_rate * (1 - p_fail)}


def jackson_rates(external, routing):
    """Розв'язує рівняння балансу потоків λ_j = λ0_j + Σ_i λ_i · r_ij методом простої ітерації."""
    n = len(external)
    rates = list(external)
    for _ in range(10_000):
        new = [external[j] + sum(rates[i] * routing[i][j] for i in range(n)) for j in range(n)]
        if max(abs(x - y) for x, y in zip(new, rates)) < 1e-13:
            return new
        rates = new
    return rates
