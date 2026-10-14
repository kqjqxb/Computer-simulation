"""Завдання 3: приймання хворих у лікарню (приймальне відділення, супровідні, лабораторія)."""
import json
import math
import statistics
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from item import Item
from model import Model
from runner import replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
ARRIVAL_MEAN = 15.0
TYPES = {1: (0.5, 15.0), 2: (0.1, 40.0), 3: (0.4, 30.0)}  # тип: (відносна частота, середній час реєстрації)
TIME, WARMUP, SEEDS = 100_000.0, 2_000.0, range(1, 21)

# очікуване завантаження з балансу потоків: частка хворих, що проходять пристрій, / 15 · τ / кількість каналів
LAB_SHARE, WARD_SHARE = TYPES[2][0] + TYPES[3][0], TYPES[1][0] + TYPES[2][0]
EXPECTED_LOAD = {
    "Приймальне відділення": (sum(share * mean for share, mean in TYPES.values())
                              + TYPES[2][0] * TYPES[1][1]) / ARRIVAL_MEAN / 2,
    "Супровідні": WARD_SHARE / ARRIVAL_MEAN * 5.5 / 3,
    "Перехід до лабораторії": LAB_SHARE / ARRIVAL_MEAN * 3.5,
    "Реєстратура лабораторії": LAB_SHARE / ARRIVAL_MEAN * 4.5,
    "Лаборанти": LAB_SHARE / ARRIVAL_MEAN * 4.0 / 2,
    "Повернення до приймального": TYPES[2][0] / ARRIVAL_MEAN * 3.5,
}


def new_patient(t):
    z, cumulative = fun_rand.uniform01(), 0.0
    for kind, (share, _) in TYPES.items():
        cumulative += share
        if z < cumulative:
            return Item(t, kind, origin=kind)
    return Item(t, 3, origin=3)


def become_type1(item):
    item.kind = 1


def build():
    create = Create(ARRIVAL_MEAN, name="Надходження", item_factory=new_patient)
    reception = Process(lambda item: fun_rand.exp(TYPES[item.kind][1]), channels=2,
                        queue_key=lambda item: 0 if item.kind == 1 else 1, name="Приймальне відділення")
    escort = Process(lambda item: fun_rand.unif(3, 8), channels=3, name="Супровідні")
    to_lab = Process(lambda item: fun_rand.unif(2, 5), channels=math.inf, name="Перехід до лабораторії")
    registry = Process(lambda item: fun_rand.erlang(4.5, 3), record_arrivals=True,
                       name="Реєстратура лабораторії")
    lab = Process(lambda item: fun_rand.erlang(4.0, 2), channels=2, name="Лаборанти")
    back = Process(lambda item: fun_rand.unif(2, 5), channels=math.inf, name="Повернення до приймального")
    back.on_enter = become_type1
    ward = Dispose("Палата", group=lambda item: item.attrs["origin"])
    lab_exit = Dispose("Вихід з лабораторії", group=lambda item: item.attrs["origin"])

    create.add_route(reception)
    reception.add_route(escort, condition=lambda item: item.kind == 1)
    reception.add_route(to_lab, condition=lambda item: item.kind != 1)
    escort.add_route(ward)
    to_lab.add_route(registry)
    registry.add_route(lab)
    lab.add_route(back, condition=lambda item: item.kind == 2)
    lab.add_route(lab_exit, condition=lambda item: item.kind == 3)
    back.add_route(reception)
    return Model([create, reception, escort, to_lab, registry, lab, back, ward, lab_exit])


def collect(model):
    r = model.results()
    elements = {e.name: e for e in model.elements}
    ward, lab_exit = elements["Палата"], elements["Вихід з лабораторії"]
    lab_arrivals = elements["Реєстратура лабораторії"].arrival_times
    out = {
        "type1": statistics.mean(ward.times[1]),
        "type2": statistics.mean(ward.times[2]),
        "type3": statistics.mean(lab_exit.times[3]),
        "all": r["mean_time_in_system"],
        "lab_interval": (lab_arrivals[-1] - lab_arrivals[0]) / (len(lab_arrivals) - 1),
        "patients": r["created"],
    }
    for name, p in r["processes"].items():
        out[f"load: {name}"] = p["mean_load"]
        out[f"queue: {name}"] = p["mean_queue"]
        out[f"wait: {name}"] = p["mean_wait"]
    return out


def main():
    fun_rand.seed(3)
    model = build()
    print(f"Лікарня: надходження exp({ARRIVAL_MEAN}) хв, типи хворих {TYPES}")
    model.simulate(300.0, trace_events=4)

    runs = replicate(build, TIME, SEEDS, warmup=WARMUP, collect=collect)

    def stat(key):
        return summary([r[key] for r in runs])

    print(f"\nРЕЗУЛЬТАТИ ({len(SEEDS)} прогонів по {TIME:.0f} хв, розгін {WARMUP:.0f} хв, "
          "95 % довірчий інтервал):")
    main_rows = [
        ("час у системі, хворі типу 1", stat("type1")),
        ("час у системі, хворі типу 2", stat("type2")),
        ("час у системі, хворі типу 3", stat("type3")),
        ("час у системі, усі хворі", stat("all")),
        ("інтервал між прибуттями в лабораторію", stat("lab_interval")),
    ]
    for label, (mean, ci) in main_rows:
        print(f"  {label:40} {mean:9.3f} ± {ci:.3f} хв")
    detail = []
    print("\nХарактеристики пристроїв:")
    for name in EXPECTED_LOAD:
        values = {k: stat(f"{k}: {name}") for k in ("load", "queue", "wait")}
        detail.append({"element": name, "expected_load": EXPECTED_LOAD[name],
                       **{k: v[0] for k, v in values.items()},
                       **{f"{k}_ci": v[1] for k, v in values.items()}})
        print(f"  {name:28} завантаження {values['load'][0]:.4f} ± {values['load'][1]:.4f} "
              f"(баланс потоків {EXPECTED_LOAD[name]:.4f}), черга {values['queue'][0]:.4f}, "
              f"очікування {values['wait'][0]:.3f} хв")

    print(f"\nОчікуваний інтервал між прибуттями в лабораторію = {ARRIVAL_MEAN} / ({LAB_SHARE:.1f}) = "
          f"{ARRIVAL_MEAN / LAB_SHARE:.1f} хв")

    with open(RESULTS / "task3.json", "w", encoding="utf-8") as f:
        json.dump({"main": [{"metric": m, "mean": v[0], "ci": v[1]} for m, v in main_rows], "detail": detail,
                   "expected_lab_interval": ARRIVAL_MEAN / LAB_SHARE}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
