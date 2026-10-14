import math

import fun_rand


class Route:
    def __init__(self, target, probability=1.0, priority=0, condition=None):
        self.target = target
        self.probability = probability
        self.priority = priority
        self.condition = condition
        self.count = 0


class Element:
    """Базовий елемент моделі.

    Затримка задається числом (середнє значення закону distribution) або функцією item -> затримка.
    Маршрут виходу обирається за ймовірністю ("probability"), за пріоритетом ("priority": перший за
    пріоритетом блок, що може прийняти вимогу) або функцією routing(item, routes) -> route.
    Умова маршруту condition(item) дозволяє розводити вимоги за типом.
    """

    _next_id = 0

    def __init__(self, name=None, delay=1.0, delay_dev=0.0, distribution="exp", routing="probability"):
        self.id = Element._next_id
        Element._next_id += 1
        self.name = name or f"element{self.id}"
        self.delay = delay
        self.delay_dev = delay_dev
        self.distribution = distribution
        self.routing = routing
        self.routes = []
        self.on_enter = None
        self.listener = None
        self.tnext = math.inf
        self.tcurr = 0.0
        self.state = 0
        self.quantity = 0

    def add_route(self, target, probability=1.0, priority=0, condition=None):
        self.routes.append(Route(target, probability, priority, condition))
        return self

    def can_accept(self):
        return True

    def _before_change(self):
        if self.listener:
            self.listener.before(self)

    def _after_change(self):
        if self.listener:
            self.listener.after(self)

    def choose_route(self, item):
        candidates = [r for r in self.routes if r.condition is None or r.condition(item)]
        if not candidates:
            return None
        if callable(self.routing):
            route = self.routing(item, candidates)
        elif self.routing == "priority":
            ordered = sorted(candidates, key=lambda r: -r.priority)
            route = next((r for r in ordered if r.target.can_accept()), ordered[0])
        else:
            z = fun_rand.uniform01() * sum(r.probability for r in candidates)
            route = candidates[-1]
            cumulative = 0.0
            for r in candidates:
                cumulative += r.probability
                if z < cumulative:
                    route = r
                    break
        route.count += 1
        return route.target

    def send(self, item):
        target = self.choose_route(item)
        if target is not None:
            target.in_act(item)

    def get_delay(self, item=None):
        if callable(self.delay):
            return self.delay(item)
        kind = self.distribution.lower()
        if kind == "exp":
            return fun_rand.exp(self.delay)
        if kind == "norm":
            return max(0.0, fun_rand.norm(self.delay, self.delay_dev))
        if kind == "unif":
            return fun_rand.unif(self.delay - self.delay_dev, self.delay + self.delay_dev)
        if kind == "erlang":
            return fun_rand.erlang(self.delay, int(self.delay_dev))
        return self.delay

    def in_act(self, item):
        pass

    def out_act(self):
        self.quantity += 1

    def do_statistics(self, delta):
        pass

    def reset_statistics(self):
        self.quantity = 0

    def print_info(self):
        print(f"{self.name} state = {self.state} quantity = {self.quantity} tnext = {self.tnext:.4f}")
