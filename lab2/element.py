import math

import fun_rand


class Element:
    _next_id = 0

    def __init__(self, name=None, delay_mean=1.0, delay_dev=0.0, distribution="exp"):
        self.id = Element._next_id
        Element._next_id += 1
        self.name = name or f"element{self.id}"
        self.delay_mean = delay_mean
        self.delay_dev = delay_dev
        self.distribution = distribution
        self.tnext = math.inf
        self.tcurr = 0.0
        self.state = 0
        self.quantity = 0
        self.routes = []
        self.route_counts = []

    def set_next_element(self, element):
        self.routes = [(element, 1.0)]
        self.route_counts = [0]

    def add_route(self, element, probability):
        """Додає ще один вихід з елемента; сума ймовірностей усіх виходів має дорівнювати 1."""
        self.routes.append((element, probability))
        self.route_counts.append(0)

    def next_element(self):
        if not self.routes:
            return None
        index = 0
        if len(self.routes) > 1:
            z = fun_rand.uniform01()
            cumulative = 0.0
            for index, (_, probability) in enumerate(self.routes):
                cumulative += probability
                if z < cumulative:
                    break
        self.route_counts[index] += 1
        return self.routes[index][0]

    def get_delay(self):
        kind = self.distribution.lower()
        if kind == "exp":
            return fun_rand.exp(self.delay_mean)
        if kind == "norm":
            return max(0.0, fun_rand.norm(self.delay_mean, self.delay_dev))
        if kind == "unif":
            return fun_rand.unif(self.delay_mean - self.delay_dev, self.delay_mean + self.delay_dev)
        if kind == "erlang":
            return fun_rand.erlang(self.delay_mean, int(self.delay_dev))
        return self.delay_mean

    def in_act(self):
        pass

    def out_act(self):
        self.quantity += 1

    def do_statistics(self, delta):
        pass

    def print_info(self):
        print(f"{self.name} state = {self.state} quantity = {self.quantity} tnext = {self.tnext:.4f}")

    def print_result(self):
        print(f"{self.name} quantity = {self.quantity}")
