"""Evaluate the selected E2E workflow results without crediting skipped streams."""

from __future__ import annotations

import argparse
from collections.abc import Mapping, Sequence


STREAMS: tuple[str, ...] = ("edge", "product", "chaos")


def evaluate_gate(
    *,
    preflight: str,
    artifacts: str,
    selected: Mapping[str, bool],
    results: Mapping[str, str],
) -> tuple[bool, tuple[str, ...]]:
    """Return whether the gate passes and the reasons for any rejection.

    An unselected stream is intentionally not evaluated: its GitHub Actions
    ``skipped`` result is neutral and cannot be represented as a pass. Shared
    preflight and artifact preparation are always required whenever this gate
    runs.
    """

    failures: list[str] = []
    if preflight != "success":
        failures.append(f"shared preflight result was {preflight}")
    if artifacts != "success":
        failures.append(f"E2E artifact preparation result was {artifacts}")
    for stream in STREAMS:
        if selected.get(stream, False) and results.get(stream) != "success":
            failures.append(
                f"selected {stream} E2E result was {results.get(stream, 'missing')}"
            )
    return not failures, tuple(failures)


def _parser() -> argparse.ArgumentParser:
    """Build the command-line parser used by the workflow gate."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--preflight", required=True)
    parser.add_argument("--artifacts", required=True)
    for stream in STREAMS:
        parser.add_argument(f"--{stream}-result", required=True)
        parser.add_argument(f"--{stream}-selected", action="store_true")
    return parser


def main(arguments: Sequence[str] | None = None) -> int:
    """Evaluate workflow result arguments and return a shell exit status."""

    args = _parser().parse_args(arguments)
    selected = {stream: bool(getattr(args, f"{stream}_selected")) for stream in STREAMS}
    results = {stream: str(getattr(args, f"{stream}_result")) for stream in STREAMS}
    passed, failures = evaluate_gate(
        preflight=args.preflight,
        artifacts=args.artifacts,
        selected=selected,
        results=results,
    )
    for stream in STREAMS:
        state = "selected" if selected[stream] else "not evaluated"
        print(f"e2e-{stream}: {results[stream]} ({state})")
    if failures:
        for failure in failures:
            print(f"E2E gate failure: {failure}")
        return 1
    print("All selected E2E suites passed; intentionally unselected suites were not evaluated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
