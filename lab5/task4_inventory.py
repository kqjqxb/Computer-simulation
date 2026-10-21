"""Завдання 4: управління запасами холодильників з періодичним переглядом запасу."""
import json
import math
import statistics
from pathlib import Path

import fun_rand
from petri import PetriNet, const, exp
from runner import fmt, replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
DEMAND_MEAN = 0.2  # тижні між замовленнями покупців
LOST_PROBABILITY = 0.8
REVIEW, LEAD = 4.0, 3.0
LEVEL, REORDER = 72, 18
TIME, WARMUP, SEEDS = 5000.0, 52.0, range(1, 101)


def build():
    net = PetriNet("Управління запасами")
    for name, mark in (("Генератор попиту", 1), ("Покупець", 0), ("Запас", LEVEL), ("Продано", 0),
                       ("Відкладений попит", 0), ("Втрачені продажі", 0), ("Нестача до рівня", 0),
                       ("Таймер перегляду", 1), ("Перегляд", 0), ("Формування замовлення", 0)):
        net.place(name, mark)
    net.transition("Надходження покупця", exp(DEMAND_MEAN), inputs={"Генератор попиту": 1},
                   outputs={"Генератор попиту": 1, "Покупець": 1})
    net.transition("Продаж", inputs={"Покупець": 1, "Запас": 1},
                   outputs={"Продано": 1, "Нестача до рівня": 1},
                   priority=3)
    net.transition("Відмова від покупки", inputs={"Покупець": 1}, outputs={"Втрачені продажі": 1}, priority=1,
                   probability=LOST_PROBABILITY)
    net.transition("Повторне замовлення", inputs={"Покупець": 1},
                   outputs={"Відкладений попит": 1, "Нестача до рівня": 1}, priority=1,
                   probability=1 - LOST_PROBABILITY)
    net.transition("Видача відкладеного", inputs={"Запас": 1, "Відкладений попит": 1}, outputs={"Продано": 1},
                   priority=4)
    net.transition("Перегляд запасу", const(REVIEW), inputs={"Таймер перегляду": 1},
                   outputs={"Таймер перегляду": 1, "Перегляд": 1})
    # поточний стан запасу ≤ 18  ⇔  нестача до контрольного рівня 72 ≥ 54
    net.transition("Рішення замовити", inputs={"Перегляд": 1}, info={"Нестача до рівня": LEVEL - REORDER},
                   outputs={"Формування замовлення": 1}, priority=2)
    net.transition("Без замовлення", inputs={"Перегляд": 1})
    # кожен холодильник нестачі замовляється окремим маркером; усі маркери входять у перехід одночасно
    # і одночасно виходять через 3 тижні – це одна партія
    net.transition("Доставка", const(LEAD), inputs={"Нестача до рівня": 1}, info={"Формування замовлення": 1},
                   outputs={"Запас": 1}, priority=1)
    net.transition("Кінець замовлення", inputs={"Формування замовлення": 1})
    return net


def position(net):
    """Поточний стан запасу: наявний запас + замовлені раніше − невдоволений попит."""
    return net.mark("Запас") + len(net.transitions["Доставка"].buffer) - net.mark("Відкладений попит")


def check_invariant(net):
    """P-інваріант з урахуванням маркерів усередині переходів: перехід «Продаж» уже забрав холодильник
    із запасу, але ще не додав одиницю нестачі."""
    inside = len(net.transitions["Продаж"].buffer)
    assert position(net) + inside + net.mark("Нестача до рівня") == LEVEL


def run(warmup=WARMUP, time=TIME, hooks=(), trace=0, out=print):
    net = build()
    lost, orders = [], []

    def moments(target):
        """Спостерігач виходу з переходу: запам'ятовує моменти після розгону."""
        return lambda t, consumed: target.append(t) if t >= warmup else None

    net.transitions["Відмова від покупки"].on_out = moments(lost)
    net.transitions["Рішення замовити"].on_out = moments(orders)
    net.watch("Немає в запасі", lambda n: n.mark("Запас") == 0)
    net.watch("Поточний стан запасу", position)
    r = net.simulate(time, warmup=warmup, hooks=hooks, trace=trace, out=out)
    tr = r["transitions"]
    demand = tr["Надходження покупця"]["fired"]
    gaps = [b - a for a, b in zip(lost, lost[1:])]
    return {
        "stock": r["places"]["Запас"]["mean"],
        "lost_interval": statistics.fmean(gaps) if gaps else math.nan,
        "lost_rate": len(lost) / r["time"],
        "lost_share": len(lost) / demand,
        "backorder_share": tr["Повторне замовлення"]["fired"] / demand,
        "backorders": r["places"]["Відкладений попит"]["mean"],
        "stockout_time": r["watches"]["Немає в запасі"]["mean"],
        "position": r["watches"]["Поточний стан запасу"]["mean"],
        "orders": len(orders),
        "order_size": tr["Доставка"]["entered"] / max(len(orders), 1),
        "demand_rate": demand / r["time"],
        "sold_rate": (tr["Продаж"]["fired"] + tr["Видача відкладеного"]["fired"]) / r["time"],
        "lost_gaps": gaps,
    }


def direct(time=TIME, warmup=WARMUP):
    """Незалежна перевірка: та сама політика управління запасами, змодельована подієво без мережі Петрі."""
    stock, backlog, on_order, arrival = LEVEL, 0, 0, math.inf
    t, next_demand, next_review = 0.0, fun_rand.exp(DEMAND_MEAN), REVIEW
    area, lost = 0.0, []
    while True:
        t_next = min(next_demand, next_review, arrival, time)
        if t_next > warmup:
            area += stock * (t_next - max(t, warmup))
        t = t_next
        if t >= time:
            break
        if t == arrival:
            stock += on_order
            on_order, arrival = 0, math.inf
            served = min(stock, backlog)
            stock, backlog = stock - served, backlog - served
        elif t == next_review:
            next_review += REVIEW
            ip = stock + on_order - backlog
            if ip <= REORDER:
                on_order, arrival = LEVEL - ip, t + LEAD
        else:
            next_demand = t + fun_rand.exp(DEMAND_MEAN)
            if stock > 0:
                stock -= 1
            elif fun_rand.uniform01() < LOST_PROBABILITY:
                if t >= warmup:
                    lost.append(t)
            else:
                backlog += 1
    gaps = [b - a for a, b in zip(lost, lost[1:])]
    return {"stock": area / (time - warmup), "lost_interval": statistics.fmean(gaps)}


def trajectory(weeks=60.0):
    points = []
    run(warmup=0.0, time=weeks, hooks=[lambda n: points.append((n.time, n.mark("Запас"), position(n)))])
    return points


def main():
    net = build()
    with open(RESULTS / "net4_inventory.json", "w", encoding="utf-8") as f:
        json.dump(net.describe(), f, ensure_ascii=False, indent=1)
    print(f"Запаси: попит exp({DEMAND_MEAN}) тижня, перегляд кожні {REVIEW:g} тижні, рівень {LEVEL}, точка "
          f"замовлення {REORDER}, доставка {LEAD:g} тижні, втрата продажу з ймовірністю {LOST_PROBABILITY}\n")
    print("Трасування подій навколо першого замовлення:\n")
    fun_rand.seed(3)
    lines = []
    run(warmup=0.0, time=20.0, trace=10 ** 6, out=lines.append)
    first = next(i for i, s in enumerate(lines) if "Рішення замовити" in s)
    print("\n".join(lines[first - 1:first + 3]))
    delivery = next(i for i, s in enumerate(lines) if "вихід з переходів: Доставка" in s)
    print("\n".join(lines[delivery - 1:delivery + 2]))

    fun_rand.seed(100)
    checked = []
    one = run(hooks=[check_invariant, lambda n: checked.append(1)])
    print(f"Перевірка інваріанта «стан запасу + нестача до рівня = {LEVEL}»: {len(checked)} подій, "
          f"порушень немає")
    print(f"Один прогін: середній запас {one['stock']:.4f}, інтервал між втраченими продажами "
          f"{one['lost_interval']:.4f}")

    runs = replicate(run, SEEDS)
    table = [
        ("середня кількість холодильників у запасі", summary(r["stock"] for r in runs)),
        ("середній час між втраченими продажами, тижні", summary(r["lost_interval"] for r in runs)),
        ("втрачених продажів за тиждень", summary(r["lost_rate"] for r in runs)),
        ("частка втрачених продажів", summary(r["lost_share"] for r in runs)),
        ("частка відкладеного попиту", summary(r["backorder_share"] for r in runs)),
        ("середній невдоволений попит", summary(r["backorders"] for r in runs)),
        ("частка часу без запасу", summary(r["stockout_time"] for r in runs)),
        ("середній поточний стан запасу", summary(r["position"] for r in runs)),
        ("середній розмір замовлення", summary(r["order_size"] for r in runs)),
        ("середній інтервал між замовленнями, тижні", summary((TIME - WARMUP) / r["orders"] for r in runs)),
    ]
    print(f"\nРЕЗУЛЬТАТИ ({len(SEEDS)} прогонів по {TIME:.0f} тижнів, розгін {WARMUP:.0f} тижнів, "
          f"95 % довірчий інтервал):")
    for label, value in table:
        print(f"  {label:46} {fmt(value)}")
    gaps = [g for r in runs for g in r["lost_gaps"]]
    short = sum(g < REVIEW for g in gaps) / len(gaps)
    print(f"\nІнтервали між втраченими продажами: медіана {statistics.median(gaps):.4f}, коефіцієнт варіації "
          f"{statistics.stdev(gaps) / statistics.fmean(gaps):.4f}, частка інтервалів до 4 тижнів {short:.4f}")

    demand = statistics.fmean(r["demand_rate"] for r in runs)
    sold = statistics.fmean(r["sold_rate"] for r in runs)
    lost = table[2][1][0]
    print(f"\nПеревірка: інтенсивність попиту {demand:.4f} (задано {1 / DEMAND_MEAN:g}), "
          f"продано {sold:.4f} + "
          f"втрачено {lost:.4f} = {sold + lost:.4f}")
    print(f"1 / інтенсивність втрачених продажів = {1 / lost:.4f} тижня")
    direct_runs = replicate(direct, range(1001, 1101))
    ds, dl = summary(r["stock"] for r in direct_runs), summary(r["lost_interval"] for r in direct_runs)
    print(f"Незалежна подієва модель без мережі Петрі: запас {fmt(ds)}, "
          f"інтервал між втраченими продажами {fmt(dl)}")

    fun_rand.seed(7)
    points = trajectory()
    with open(RESULTS / "task4.json", "w", encoding="utf-8") as f:
        json.dump({"table": [{"metric": m, "mean": v[0], "ci": v[1]} for m, v in table],
                   "gaps": {"median": statistics.median(gaps), "short_share": short,
                            "cv": statistics.stdev(gaps) / statistics.fmean(gaps)},
                   "demand_rate": demand, "sold_rate": sold, "direct": {"stock": ds, "lost_interval": dl},
                   "check_events": len(checked), "trajectory": points}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
