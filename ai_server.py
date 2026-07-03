"""
Phiigrame AI Local Server
=========================
A local HTTP server that runs Qwen2.5-Coder-1.5B-Instruct entirely on this
machine, with no cloud calls and no Ollama required.

Default model source: HuggingFace - Qwen/Qwen2.5-Coder-1.5B-Instruct
                      (https://github.com/QwenLM/Qwen2.5-Coder)

The server exposes a small JSON API used by the Phiigrame IDE:

    GET  /health                  -> { "ok": true,  "model": "..." }
    POST /complete                -> { "text": "<code completion>" }
    POST /chat                    -> { "text": "<assistant reply>" }
    POST /shutdown                -> { "ok": true }

Requirements (installed automatically on first run if missing):
    - torch      (CPU build is fine)
    - transformers
    - flask

Usage:
    python ai_server.py [--port 11435] [--model Qwen/Qwen2.5-Coder-1.5B-Instruct]
                        [--device cpu|cuda|mps] [--no-auto-install]

If the model files are already cached on disk (default
~/.cache/huggingface/hub) they will be loaded from there.
"""
import argparse
import json
import os
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------------------------------------------------------------------
# Dependency bootstrap - try to import, otherwise install (unless disabled)
# ---------------------------------------------------------------------------
def _import_or_install():
    missing = []
    for mod in ("torch", "transformers", "flask"):
        try:
            __import__(mod)
        except ImportError:
            missing.append(mod)

    if not missing:
        return

    print(f"[phiigrame-ai] Missing dependencies: {missing}", flush=True)
    try:
        import subprocess
        cmd = [sys.executable, "-m", "pip", "install", "--quiet"] + missing
        print(f"[phiigrame-ai] Installing: {' '.join(cmd)}", flush=True)
        subprocess.check_call(cmd)
        print("[phiigrame-ai] Install complete.", flush=True)
    except Exception as e:
        print(f"[phiigrame-ai] Auto-install failed: {e}", flush=True)
        print("[phiigrame-ai] Please run:  pip install torch transformers flask", flush=True)
        sys.exit(1)


# ---------------------------------------------------------------------------
# Globals - the model is loaded once and shared by all worker threads
# ---------------------------------------------------------------------------
MODEL = None
TOKENIZER = None
DEVICE = "cpu"
MODEL_NAME = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
LOAD_LOCK = threading.Lock()
LOADED = False

SYSTEM_PROMPT = (
    "You are Phiigrame AI, a helpful coding assistant running locally "
    "inside the Phiigrame IDE. You help developers with code, debugging, "
    "and explanations. Be concise and accurate."
)


def load_model(model_name: str, device: str):
    """Load the Qwen2.5-Coder model + tokenizer once."""
    global MODEL, TOKENIZER, LOADED
    with LOAD_LOCK:
        if LOADED:
            return
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        print(f"[phiigrame-ai] Loading {model_name} on {device} ...", flush=True)
        t0 = time.time()
        try:
            tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
            # torch_dtype auto picks float16 on cuda/mps, float32 on cpu
            dtype = torch.float32
            if device in ("cuda", "mps"):
                dtype = torch.float16
            model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=dtype,
                trust_remote_code=True,
                low_cpu_mem_usage=True,
            )
            model = model.to(device)
            model.eval()
        except Exception as e:
            print(f"[phiigrame-ai] Failed to load {model_name}: {e}", flush=True)
            traceback.print_exc()
            raise

        MODEL = model
        TOKENIZER = tokenizer
        LOADED = True
        print(f"[phiigrame-ai] Model loaded in {time.time() - t0:.1f}s", flush=True)


def _generate(prompt: str, max_new_tokens: int, temperature: float) -> str:
    """Run a single text generation pass."""
    import torch
    if not LOADED:
        raise RuntimeError("Model not loaded")
    inputs = TOKENIZER(prompt, return_tensors="pt").to(DEVICE)
    with torch.no_grad():
        out = MODEL.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=temperature > 0.01,
            temperature=max(0.01, temperature),
            top_p=0.95,
            pad_token_id=TOKENIZER.eos_token_id,
            eos_token_id=TOKENIZER.eos_token_id,
        )
    # strip the prompt
    new_tokens = out[0][inputs["input_ids"].shape[1]:]
    text = TOKENIZER.decode(new_tokens, skip_special_tokens=True)
    return text


def build_chat_prompt(messages):
    """Build a Qwen2.5 chat-template prompt from a list of {role, content}."""
    parts = []
    for msg in messages:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        parts.append(f"<|im_start|>{role}\n{content}<|im_end|>\n")
    parts.append("<|im_start|>assistant\n")
    return "".join(parts)


# ---------------------------------------------------------------------------
# HTTP request handler
# ---------------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):  # quiet logging
        pass

    def _send_json(self, obj, code=200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if not length:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def do_GET(self):
        if self.path == "/health":
            self._send_json({
                "ok": LOADED,
                "model": MODEL_NAME,
                "device": DEVICE,
                "loaded": LOADED,
            })
        elif self.path == "/":
            self._send_json({"service": "phiigrame-ai", "model": MODEL_NAME})
        else:
            self._send_json({"error": "not found"}, code=404)

    def do_POST(self):
        try:
            if self.path == "/complete":
                data = self._read_json()
                prefix = data.get("prefix", "")
                suffix = data.get("suffix", "")
                language = data.get("language", "code")
                file_name = data.get("fileName", "")
                max_new = int(data.get("maxNewTokens", 256))
                temperature = float(data.get("temperature", 0.2))

                # Build a focused completion prompt
                user_msg = (
                    f"Complete the {language} code"
                    + (f" in file {file_name}" if file_name else "")
                    + f".\n\nFile before cursor:\n```\n{prefix[-2000:]}\n```\n\n"
                    + f"File after cursor:\n```\n{suffix[:500]}\n```"
                )
                messages = [
                    {"role": "system", "content":
                        "You are a code completion assistant. Output ONLY the code that "
                        "should be inserted at the cursor position, with no explanations "
                        "or markdown formatting."},
                    {"role": "user", "content": user_msg},
                ]
                prompt = build_chat_prompt(messages)
                text = _generate(prompt, max_new, temperature)
                self._send_json({"text": text})

            elif self.path == "/chat":
                data = self._read_json()
                message = data.get("message", "")
                history = data.get("history", []) or []
                max_new = int(data.get("maxNewTokens", 1024))
                temperature = float(data.get("temperature", 0.7))

                messages = [{"role": "system", "content": SYSTEM_PROMPT}]
                messages.extend(history)
                messages.append({"role": "user", "content": message})
                prompt = build_chat_prompt(messages)
                text = _generate(prompt, max_new, temperature)
                self._send_json({"text": text})

            elif self.path == "/shutdown":
                self._send_json({"ok": True})
                # Shutdown the server after a short delay
                threading.Timer(0.5, lambda: sys.exit(0)).start()

            else:
                self._send_json({"error": "not found"}, code=404)
        except Exception as e:
            traceback.print_exc()
            self._send_json({"error": str(e)}, code=500)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="Phiigrame AI Local Server")
    parser.add_argument("--port", type=int, default=11435)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--model", default=MODEL_NAME,
                        help="HF model id, e.g. Qwen/Qwen2.5-Coder-1.5B-Instruct")
    parser.add_argument("--device", default=None,
                        help="cpu | cuda | mps (auto-detected if omitted)")
    parser.add_argument("--no-auto-install", action="store_true",
                        help="Do not try to pip-install missing dependencies")
    args = parser.parse_args()

    if not args.no_auto_install:
        _import_or_install()

    # Determine the device
    global DEVICE
    if args.device:
        DEVICE = args.device
    else:
        try:
            import torch
            if torch.cuda.is_available():
                DEVICE = "cuda"
            elif getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
                DEVICE = "mps"
            else:
                DEVICE = "cpu"
        except Exception:
            DEVICE = "cpu"
    print(f"[phiigrame-ai] Using device: {DEVICE}", flush=True)

    global MODEL_NAME
    MODEL_NAME = args.model

    # Load the model in a background thread so the HTTP server can come up
    # and report a /health=not-loaded status in the meantime.
    t = threading.Thread(target=load_model, args=(args.model, DEVICE), daemon=True)
    t.start()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"[phiigrame-ai] HTTP server listening on http://{args.host}:{args.port}", flush=True)
    print(f"[phiigrame-ai] Press Ctrl+C to stop.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
