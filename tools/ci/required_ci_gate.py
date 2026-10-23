"""Reduce all selected CI job results to one stable required check."""

from __future__ import annotations

import argparse
from collections.abc import Mapping, Sequence


PASSING_RESULTS: frozenset[str] = frozenset({"success", "skipped"})


def evaluate_results(results: Mapping[str, str]) -> tuple[bool, tuple[str, ...]]:
    """Return whether every job succeeded or was intentionally skipped."""

    failures = tuple(
        f"{name} result was {result}"
        for name, result in results.items()
        if result not in PASSING_RESULTS
    )
    return not failures, failures


def _parser() -> argparse.ArgumentParser:
    """Build the command-line parser for name=result job outcomes."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--result", action="append", required=True, metavar="NAME=RESULT")
    return parser


def main(arguments: Sequence[str] | None = None) -> int:
    """Print the aggregate decision and return its shell exit status."""

    parser = _parser()
    entries: dict[str, str] = {}
    for value in parser.parse_args(arguments).result:
        name, separator, result = value.partition("=")
        if not separator or not name or not result:
            parser.error(f"invalid result {value!r}; expected NAME=RESULT")
        entries[name] = result

    passed, failures = evaluate_results(entries)
    for name, result in entries.items():
        print(f"{name}: {result}")
    for failure in failures:
        print(f"Required CI failure: {failure}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
