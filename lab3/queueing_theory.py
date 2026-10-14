"""Аналітичні формули теорії масового обслуговування для перевірки універсального алгоритму."""
import math


def mmck(arrival_rate, service_mean, channels=1, capacity_queue=math.inf):
    """Система M/M/c/K: c каналів, черга до capacity_queue місць."""
    a = arrival_rate * service_mean
    c = channels
    if capacity_queue == math.inf:
        rho = a / c
        p0 = 1 / (sum(a ** n / math.factorial(n) for n in range(c))
                  + a ** c / (math.factorial(c) * (1 - rho)))
        lq = p0 * a ** c * rho / (math.factorial(c) * (1 - rho) ** 2)
        return {"failure_probability": 0.0, "mean_queue": lq, "mean_load": rho}
    k = c + int(capacity_queue)
    weights = [a ** n / math.factorial(n) if n <= c else a ** n / (math.factorial(c) * c ** (n - c))
               for n in range(k + 1)]
    p = [w / sum(weights) for w in weights]
    lq = sum((n - c) * p[n] for n in range(c + 1, k + 1))
    busy = sum(min(n, c) * p[n] for n in range(k + 1))
    return {"failure_probability": p[k], "mean_queue": lq, "mean_load": busy / c,
            "mean_in_system": lq + busy}


def erlang_b(channels, load):
    """Ймовірність втрати в системі з відмовами M/M/c/0 (формула Ерланга B, рекурентно)."""
    b = 1.0
    for n in range(1, channels + 1):
        b = load * b / (n + load * b)
    return b


def priority_waits(rates, service_means, second_moments):
    """Середній час очікування класів у M/G/1 з відносними пріоритетами (формула Кобхема).

    Клас 0 має найвищий пріоритет.
    """
    w0 = sum(lam * m2 for lam, m2 in zip(rates, second_moments)) / 2
    waits, sigma = [], 0.0
    for lam, mean in zip(rates, service_means):
        previous = sigma
        sigma += lam * mean
        waits.append(w0 / ((1 - previous) * (1 - sigma)))
    return waits
