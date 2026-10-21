"""Завдання 1: конвеєрна система з п'ятьма обслуговуючими пристроями."""
import json
import statistics
from pathlib import Path

import fun_rand
from petri import PetriNet, const, exp
from runner import fmt, replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
DEVICES = 5
ARRIVAL, SERVICE_MEAN, STEP, BACK = 0.25, 1.0, 1.0, 5.0  # хвилини
TIME, WARMUP, SEEDS = 10000.0, 1000.0, range(1, 21)
TAIL = 300.0  # час перебування рахується для деталей, що надійшли не пізніше TIME − TAIL


def build():
    net = PetriNet("Конвеєрна система")
    net.place("Генератор", 1)
    for i in range(1, DEVICES + 1):
        net.place(f"Деталь біля П{i}")
        net.place(f"П{i} вільний", 1)
    net.place("Оброблені деталі")
    net.transition("Надходження", const(ARRIVAL), inputs={"Генератор": 1},
                   outputs={"Генератор": 1, "Деталь біля П1": 1})
    for i in range(1, DEVICES + 1):
        part, free = f"Деталь біля П{i}", f"П{i} вільний"
        net.transition(f"Обробка П{i}", exp(SERVICE_MEAN), inputs={part: 1, free: 1},
                       outputs={free: 1, "Оброблені деталі": 1}, carry={"Оброблені деталі": part}, priority=1)
        if i < DEVICES:
            name, delay, target = f"Рух до П{i + 1}", STEP, f"Деталь біля П{i + 1}"
        else:
            name, delay, target = "Повернення до П1", BACK, "Деталь біля П1"
        net.transition(name, const(delay), inputs={part: 1}, outputs={target: 1}, carry={target: part})
    net.watch("Зайняті пристрої", busy_devices)
    return net


def busy_devices(net):
    return sum(1 - net.mark(f"П{i} вільний") for i in range(1, DEVICES + 1))


def in_system(net):
    """Деталі в системі: на конвеєрі (у переходах руху та повернення) і на обробці."""
    return sum(len(tr.buffer) for name, tr in net.transitions.items() if name != "Надходження") \
        + sum(net.mark(f"Деталь біля П{i}") for i in range(1, DEVICES + 1))


def run(warmup=WARMUP, hooks=(), trace=0, out=print):
    net = build()
    times = []

    def record(t, consumed):
        token = next(iter(consumed.values()))[0]
        if warmup <= token.born <= TIME - TAIL:
            times.append(t - token.born)

    for i in range(1, DEVICES + 1):
        net.transitions[f"Обробка П{i}"].on_out = record
    r = net.simulate(TIME, warmup=warmup, hooks=hooks, trace=trace, out=out)
    tr = r["transitions"]
    served = sum(tr[f"Обробка П{i}"]["fired"] for i in range(1, DEVICES + 1))
    in_sys = sum(v["mean_busy"] for k, v in tr.items() if k != "Надходження") \
        + sum(r["places"][f"Деталь біля П{i}"]["mean"] for i in range(1, DEVICES + 1))
    return {
        "times": times,
        "mean_time": statistics.fmean(times),
        "load": [tr[f"Обробка П{i}"]["mean_busy"] for i in range(1, DEVICES + 1)],
        "share": [tr[f"Обробка П{i}"]["fired"] / served for i in range(1, DEVICES + 1)],
        "busy": r["watches"]["Зайняті пристрої"]["mean"],
        "busy_dist": r["watches"]["Зайняті пристрої"]["distribution"],
        "throughput": served / r["time"],
        "returns_per_part": tr["Повернення до П1"]["fired"] / served,
        "in_system": in_sys,
    }


class Invariants:
    """Перевірка P-інваріантів після кожної події та підрахунок найбільшої кількості деталей у системі."""

    def __init__(self):
        self.checked, self.max_in_system = 0, 0

    def __call__(self, net):
        for i in range(1, DEVICES + 1):
            assert net.mark(f"П{i} вільний") + len(net.transitions[f"Обробка П{i}"].buffer) == 1
        assert net.mark("Генератор") + len(net.transitions["Надходження"].buffer) == 1
        created = net.transitions["Надходження"].fired
        assert created == net.mark("Оброблені деталі") + in_system(net)
        self.max_in_system = max(self.max_in_system, in_system(net))
        self.checked += 1


def histogram(times, width=0.5):
    counts = {}
    for t in times:
        k = int(t // width)
        counts[k] = counts.get(k, 0) + 1
    return {"width": width, "counts": [counts.get(k, 0) for k in range(max(counts) + 1)]}


def main():
    net = build()
    with open(RESULTS / "net1_conveyor.json", "w", encoding="utf-8") as f:
        json.dump(net.describe(), f, ensure_ascii=False, indent=1)
    print(f"Конвеєр: надходження кожні {ARRIVAL} хв, обробка exp({SERVICE_MEAN}), рух між пристроями "
          f"{STEP} хв, повернення {BACK} хв, {DEVICES} пристроїв\n")
    print("Трасування перших подій:\n")
    fun_rand.seed(1)
    run(warmup=0.0, trace=12, out=print)

    fun_rand.seed(100)
    check = Invariants()
    one = run(warmup=0.0, hooks=[check], out=print)
    print(f"Перевірка інваріантів: {check.checked} подій, порушень немає; найбільше деталей у системі "
          f"{check.max_in_system}")

    runs = replicate(run, SEEDS)
    times = [t for r in runs for t in r["times"]]
    q = statistics.quantiles(times, n=20)
    table = [("середній час перебування, хв", summary(r["mean_time"] for r in runs))]
    table += [(f"завантаження П{i + 1}", summary(r["load"][i] for r in runs)) for i in range(DEVICES)]
    table += [
        ("середня кількість зайнятих пристроїв", summary(r["busy"] for r in runs)),
        ("пропускна здатність, деталей/хв", summary(r["throughput"] for r in runs)),
        ("повернень до П1 на одну деталь", summary(r["returns_per_part"] for r in runs)),
        ("середня кількість деталей у системі", summary(r["in_system"] for r in runs)),
    ]
    dist = {k: statistics.fmean(r["busy_dist"].get(k, 0.0) for r in runs) for k in range(DEVICES + 1)}
    share = [statistics.fmean(r["share"][i] for r in runs) for i in range(DEVICES)]
    time_stats = {"mean": statistics.fmean(times), "stdev": statistics.stdev(times), "min": min(times),
                  "median": statistics.median(times), "p90": q[17], "p95": q[18], "max": max(times),
                  "count": len(times)}

    print(f"\nРЕЗУЛЬТАТИ ({len(SEEDS)} прогонів по {TIME:.0f} хв, розгін {WARMUP:.0f} хв, "
          f"95 % довірчий інтервал):")
    for label, value in table:
        print(f"  {label:40} {fmt(value)}")
    print("\nЧас перебування деталі (усі прогони): "
          + ", ".join(f"{k} = {v:.4f}" for k, v in time_stats.items() if k != "count")
          + f", деталей {len(times)}")
    print("Розподіл кількості зайнятих пристроїв: " + ", ".join(f"P({k}) = {p:.4f}" for k, p in dist.items()))
    print("Частка деталей, оброблених на пристрої: "
          + ", ".join(f"П{i + 1} {s:.4f}" for i, s in enumerate(share)))

    lam = statistics.fmean(r["throughput"] for r in runs)
    w = table[0][1][0]
    print(f"\nПеревірка: пропускна здатність {lam:.4f} при інтенсивності надходження {1 / ARRIVAL:.1f}; "
          f"сума завантажень {sum(v[0] for _, v in table[1:6]):.4f}, "
          f"λ·E[S] = {1 / ARRIVAL * SERVICE_MEAN:.1f}")
    print(f"Формула Літтла: λ·W = {lam:.4f} · {w:.4f} = {lam * w:.4f}, середня кількість деталей у системі "
          f"{table[-1][1][0]:.4f}")

    with open(RESULTS / "task1.json", "w", encoding="utf-8") as f:
        json.dump({"table": [{"metric": m, "mean": v[0], "ci": v[1]} for m, v in table],
                   "time_stats": time_stats,
                   "busy_distribution": dist, "share": share, "throughput": lam, "little": lam * w,
                   "check_events": check.checked, "max_in_system": check.max_in_system,
                   "single_mean_time": one["mean_time"],
                   "hist": histogram(times)}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
