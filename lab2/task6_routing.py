"""Завдання 6: вихід з PROCESS у два і більше наступних блоків, зокрема з поверненням у попередні блоки."""
import json
import math
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from model import Model
from queueing_theory import jackson_rates, mmck
from runner import metric, replicate

RESULTS = Path(__file__).resolve().parent / "results"
CREATE_MEAN = 2.0
NODES = [("PROCESS 1", 1.0, 1), ("PROCESS 2", 1.6, 2), ("PROCESS 3", 1.2, 1)]
# ймовірності переходів між пристроями; решта ймовірності з PROCESS 3 – вихід з моделі
ROUTING = [
    [0.0, 1.0, 0.0],
    [0.3, 0.0, 0.7],
    [0.0, 0.2, 0.0],
]
EXIT_P3 = 0.8


def build(maxqueue=math.inf):
    create = Create(CREATE_MEAN, name="CREATE")
    p1, p2, p3 = [Process(mean, channels=ch, maxqueue=maxqueue, name=name) for name, mean, ch in NODES]
    dispose = Dispose()
    create.set_next_element(p1)
    p1.set_next_element(p2)
    p2.add_route(p3, ROUTING[1][2])
    p2.add_route(p1, ROUTING[1][0])
    p3.add_route(dispose, EXIT_P3)
    p3.add_route(p2, ROUTING[2][1])
    return Model([create, p1, p2, p3, dispose])


def main():
    fun_rand.seed(6)
    model = build()
    print("CREATE exp(2.0) -> PROCESS 1 exp(1.0) -> PROCESS 2 exp(1.6), 2 канали -> "
          "{PROCESS 3: 0.7; PROCESS 1: 0.3}; PROCESS 3 exp(1.2) -> {DISPOSE: 0.8; PROCESS 2: 0.2}")
    model.simulate(1000.0, trace_events=5)
    single = model.print_result()
    elements = {e.name: e for e in model.elements}
    print("Фактичні частки переходів за маршрутами:")
    for name in ("PROCESS 2", "PROCESS 3"):
        e = elements[name]
        total = sum(e.route_counts)
        shares = ", ".join(f"{target.name}: {count / total:.3f} (задано {prob})"
                           for (target, prob), count in zip(e.routes, e.route_counts))
        print(f"  {name}: {shares}")

    rates = jackson_rates([1 / CREATE_MEAN, 0.0, 0.0], ROUTING)
    print(f"\nРівняння балансу потоків: λ1 = {rates[0]:.4f}, λ2 = {rates[1]:.4f}, λ3 = {rates[2]:.4f}")
    runs = replicate(build, 50_000.0, range(1, 21))
    rows = []
    total_l = 0.0
    print("ПОРІВНЯННЯ З ТЕОРІЄЮ МЕРЕЖ ДЖЕКСОНА (необмежені черги, 20 прогонів по 50000 одиниць часу)")
    for (name, mean, channels), rate in zip(NODES, rates):
        th = mmck(rate, mean, channels)
        th["visit_ratio"] = rate * CREATE_MEAN
        total_l += th["mean_in_system"]
        visits = [r["processes"][name]["arrivals"] / r["created"] for r in runs]
        sim_v = sum(visits) / len(visits)
        for label, key in [("завантаження", "mean_load"), ("середня черга", "mean_queue")]:
            sim, ci = metric(runs, ["processes", name, key])
            rows.append({"element": name, "metric": label, "sim": sim, "ci": ci, "theory": th[key]})
            print(f"{name:10} {label:22} імітація = {sim:.4f} ± {ci:.4f}, теорія = {th[key]:.4f}")
        rows.append({"element": name, "metric": "надходжень на одну вимогу", "sim": sim_v, "ci": None,
                     "theory": th["visit_ratio"]})
        print(f"{name:10} {'надходжень на 1 вимогу':22} імітація = {sim_v:.4f}, "
              f"теорія = {th['visit_ratio']:.4f}")
    t_theory = total_l * CREATE_MEAN
    sim, ci = metric(runs, ["mean_time_in_system"])
    rows.append({"element": "модель", "metric": "час перебування", "sim": sim, "ci": ci, "theory": t_theory})
    print(f"{'модель':10} {'час перебування':22} імітація = {sim:.4f} ± {ci:.4f}, теорія = {t_theory:.4f}")

    print("\nТА САМА МЕРЕЖА З ОБМЕЖЕНИМИ ЧЕРГАМИ (maxqueue = 2), 10 прогонів по 20000 одиниць часу:")
    limited = replicate(lambda: build(2), 20_000.0, range(1, 11))
    finite = {}
    for name, _, _ in NODES:
        values = {key: metric(limited, ["processes", name, key])[0]
                  for key in ("failure_probability", "mean_queue", "mean_load")}
        finite[name] = values
        print(f"{name:10} Pвідм = {values['failure_probability']:.4f}, черга = {values['mean_queue']:.4f}, "
              f"завантаження = {values['mean_load']:.4f}")
    p_fail, _ = metric(limited, ["failure_probability"])
    t_sys, _ = metric(limited, ["mean_time_in_system"])
    print(f"частка втрачених вимог = {p_fail:.4f}, середній час перебування = {t_sys:.4f}")
    finite["model"] = {"failure_probability": p_fail, "mean_time_in_system": t_sys}

    shares = {name: [c / sum(elements[name].route_counts) for c in elements[name].route_counts]
              for name in ("PROCESS 2", "PROCESS 3")}
    with open(RESULTS / "task6.json", "w", encoding="utf-8") as f:
        json.dump({"single": single, "rates": rates, "theory": rows, "finite": finite, "route_shares": shares},
                  f, ensure_ascii=False, indent=1, default=lambda v: None)


if __name__ == "__main__":
    main()
