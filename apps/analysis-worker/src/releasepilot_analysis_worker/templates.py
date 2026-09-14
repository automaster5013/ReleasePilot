import hashlib
import re
from dataclasses import dataclass
from pathlib import Path
from string import Template

import yaml


@dataclass(frozen=True)
class RenderedQuery:
    template_id: str
    query: str
    query_hash: str


class QueryTemplateRegistry:
    def __init__(self, path: Path):
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
        self._name = document["name"]
        self._metrics = document["metrics"]
        self._variables = document["allowedVariables"]

    def render(self, metric_key: str, variables: dict[str, str]) -> RenderedQuery:
        if metric_key not in self._metrics:
            raise ValueError(f"unknown metric template: {metric_key}")
        if set(variables) != set(self._variables):
            raise ValueError("query variables do not match the allowlist")
        for name, value in variables.items():
            rule = self._variables[name]
            if "enum" in rule and value not in rule["enum"]:
                raise ValueError(f"invalid query variable: {name}")
            if "pattern" in rule and re.fullmatch(rule["pattern"], value) is None:
                raise ValueError(f"invalid query variable: {name}")
        rendered = Template(self._metrics[metric_key]["promql"]).substitute(variables)
        query = " ".join(rendered.split())
        return RenderedQuery(
            template_id=f"{self._name}:{metric_key}",
            query=query,
            query_hash=hashlib.sha256(query.encode()).hexdigest(),
        )
