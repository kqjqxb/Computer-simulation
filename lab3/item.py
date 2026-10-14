class Item:
    """Вимога, що рухається моделлю: зберігає тип, момент створення та довільні атрибути."""

    _next_id = 0

    def __init__(self, t_create, kind=1, **attrs):
        self.id = Item._next_id
        Item._next_id += 1
        self.t_create = t_create
        self.kind = kind
        self.t_queue = t_create
        self.attrs = attrs
