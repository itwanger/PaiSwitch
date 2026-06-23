#!/usr/bin/env python3
"""Generate an image with StepFun's image generation API."""

from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_BASE_URL = "https://api.stepfun.com/v1"
DEFAULT_MODEL = "step-image-edit-2"
DEFAULT_SIZE = "1024x1024"
SUPPORTED_STEP_IMAGE_EDIT_2_SIZES = {
    "1024x1024",
    "768x1360",
    "896x1184",
    "1360x768",
    "1184x896",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate an image through StepFun /v1/images/generations."
    )
    prompt_group = parser.add_mutually_exclusive_group(required=True)
    prompt_group.add_argument("--prompt", help="Image prompt, 512 characters or fewer.")
    prompt_group.add_argument(
        "--prompt-file", type=Path, help="UTF-8 file containing the image prompt."
    )
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"Default: {DEFAULT_MODEL}")
    parser.add_argument("--size", default=DEFAULT_SIZE, help=f"Default: {DEFAULT_SIZE}")
    parser.add_argument("--response-format", choices=("b64_json", "url"), default="b64_json")
    parser.add_argument("--output-dir", type=Path, default=Path("output/step-image"))
    parser.add_argument("--filename", help="Optional output filename without path.")
    parser.add_argument("--seed", type=int)
    parser.add_argument("--steps", type=int, default=8)
    parser.add_argument("--cfg-scale", type=float, default=1.0)
    parser.add_argument("--negative-prompt", default="")
    parser.add_argument("--text-mode", action="store_true")
    parser.add_argument("--base-url", default=os.environ.get("STEPFUN_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--api-key-env", default="STEP_API_KEY")
    parser.add_argument("--dry-run", action="store_true", help="Print request JSON without API call.")
    return parser.parse_args()


def read_prompt(args: argparse.Namespace) -> str:
    prompt = args.prompt
    if args.prompt_file:
        prompt = args.prompt_file.read_text(encoding="utf-8")
    prompt = (prompt or "").strip()
    if not prompt:
        raise SystemExit("Prompt is empty.")
    if len(prompt) > 512:
        raise SystemExit(f"Prompt is {len(prompt)} characters; StepFun image prompt max is 512.")
    return prompt


def build_payload(args: argparse.Namespace, prompt: str) -> dict[str, Any]:
    if args.model == DEFAULT_MODEL and args.size not in SUPPORTED_STEP_IMAGE_EDIT_2_SIZES:
        allowed = ", ".join(sorted(SUPPORTED_STEP_IMAGE_EDIT_2_SIZES))
        raise SystemExit(f"Unsupported size for {DEFAULT_MODEL}: {args.size}. Allowed: {allowed}")

    payload: dict[str, Any] = {
        "model": args.model,
        "prompt": prompt,
        "response_format": args.response_format,
        "size": args.size,
        "cfg_scale": args.cfg_scale,
        "steps": args.steps,
    }
    if args.seed is not None:
        payload["seed"] = args.seed
    if args.negative_prompt:
        payload["negative_prompt"] = args.negative_prompt
    if args.text_mode:
        payload["text_mode"] = True
    return payload


def post_json(url: str, api_key: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"StepFun API HTTP {exc.code}: {details}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"StepFun API request failed: {exc.reason}") from exc


def extension_from_bytes(data: bytes) -> str:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if data.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return ".webp"
    return ".png"


def safe_stem(prompt: str) -> str:
    asciiish = re.sub(r"[^A-Za-z0-9_-]+", "-", prompt).strip("-").lower()
    if asciiish:
        return asciiish[:50]
    return "step-image"


def make_output_path(output_dir: Path, filename: str | None, prompt: str, suffix: str) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    if filename:
        path = output_dir / filename
        if not path.suffix:
            path = path.with_suffix(suffix)
        return path
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    return output_dir / f"{timestamp}-{safe_stem(prompt)}{suffix}"


def download_url(url: str, output_dir: Path, filename: str | None, prompt: str) -> Path:
    try:
        with urllib.request.urlopen(url, timeout=180) as response:
            data = response.read()
            content_type = response.headers.get_content_type()
    except urllib.error.URLError as exc:
        raise SystemExit(f"Failed to download generated image: {exc.reason}") from exc

    suffix = mimetypes.guess_extension(content_type) or extension_from_bytes(data)
    path = make_output_path(output_dir, filename, prompt, suffix)
    path.write_bytes(data)
    return path


def save_b64_image(b64_json: str, output_dir: Path, filename: str | None, prompt: str) -> Path:
    try:
        data = base64.b64decode(b64_json)
    except ValueError as exc:
        raise SystemExit("StepFun returned invalid b64_json image data.") from exc
    path = make_output_path(output_dir, filename, prompt, extension_from_bytes(data))
    path.write_bytes(data)
    return path


def main() -> int:
    args = parse_args()
    prompt = read_prompt(args)
    payload = build_payload(args, prompt)
    endpoint = args.base_url.rstrip("/") + "/images/generations"

    if args.dry_run:
        print(json.dumps({"url": endpoint, "payload": payload}, ensure_ascii=False, indent=2))
        return 0

    api_key = os.environ.get(args.api_key_env)
    if not api_key:
        raise SystemExit(f"Missing API key. Set {args.api_key_env}=<your StepFun API key>.")

    result = post_json(endpoint, api_key, payload)
    data = result.get("data") or []
    if not data:
        raise SystemExit(f"StepFun response did not include data: {json.dumps(result, ensure_ascii=False)}")

    first = data[0]
    finish_reason = first.get("finish_reason")
    seed = first.get("seed")

    if first.get("b64_json"):
        path = save_b64_image(first["b64_json"], args.output_dir, args.filename, prompt)
        print(f"saved={path.resolve()}")
    elif first.get("url"):
        print(f"url={first['url']}")
        path = download_url(first["url"], args.output_dir, args.filename, prompt)
        print(f"saved={path.resolve()}")
    else:
        raise SystemExit(f"StepFun response did not include b64_json or url: {json.dumps(first, ensure_ascii=False)}")

    if seed is not None:
        print(f"seed={seed}")
    if finish_reason:
        print(f"finish_reason={finish_reason}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
