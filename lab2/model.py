import math

from elements import Create, Dispose, Process


class Model:
    def __init__(self, elements):
        self.elements = elements
        self.tcurr = 0.0
        self.in_system_area = 0.0

    def in_system(self):
        return sum(e.queue + e.busy for e in self.elements if isinstance(e, Process))

    def simulate(self, time, trace_events=0):
        events = 0
        while self.tcurr < time:
            tnext = min(e.tnext for e in self.elements)
            delta = min(tnext, time) - self.tcurr
            for e in self.elements:
                e.do_statistics(delta)
            self.in_system_area += self.in_system() * delta
            if tnext > time:
                self.tcurr = time
                break

            self.tcurr = tnext
            for e in self.elements:
                e.tcurr = self.tcurr
            active = [e for e in self.elements if e.tnext == self.tcurr]
            for e in active:
                e.out_act()

            events += 1
            if events <= trace_events:
                names = ", ".join(e.name for e in active)
                print(f"\nIt's time for event in {names}, time = {self.tcurr:.4f}")
                for e in self.elements:
                    e.print_info()
        for e in self.elements:
            e.tcurr = self.tcurr

    def results(self):
        total = self.tcurr
        created = sum(e.quantity for e in self.elements if isinstance(e, Create))
        disposed = sum(e.quantity for e in self.elements if isinstance(e, Dispose))
        failures = sum(e.failure for e in self.elements if isinstance(e, Process))
        mean_in_system = self.in_system_area / total
        throughput = disposed / total
        out = {
            "time": total, "created": created, "disposed": disposed, "failures": failures,
            "in_system_now": self.in_system(),
            "failure_probability": failures / created if created else 0.0,
            "mean_in_system": mean_in_system,
            # формула Літтла: середній час перебування = середня кількість у системі / інтенсивність виходу
            "mean_time_in_system": mean_in_system / throughput if throughput else math.nan,
            "processes": {},
        }
        for e in self.elements:
            if isinstance(e, Process):
                out["processes"][e.name] = {
                    "arrivals": e.arrivals, "served": e.quantity, "failure": e.failure,
                    "failure_probability": e.failure_probability(),
                    "mean_queue": e.mean_queue(total), "mean_load": e.mean_load(total),
                }
        return out

    def print_result(self):
        r = self.results()
        print("\n-------------RESULTS-------------")
        for e in self.elements:
            e.print_result()
            if isinstance(e, Process):
                p = r["processes"][e.name]
                print(f"    failure = {e.failure}, failure probability = {p['failure_probability']:.4f}")
                print(f"    mean length of queue = {p['mean_queue']:.4f}")
                print(f"    mean load = {p['mean_load']:.4f}")
        print(f"created = {r['created']}, disposed = {r['disposed']}, failures = {r['failures']}, "
              f"in system at the end = {r['in_system_now']}")
        print(f"failure probability = {r['failure_probability']:.4f}, "
              f"mean number in system = {r['mean_in_system']:.4f}, "
              f"mean time in system = {r['mean_time_in_system']:.4f}")
        return r
