#!/data/data/com.tool.tree/files/home/termux/bin/python
# -*- coding: utf-8 -*-
"""Chatbot CLI
Direct REST API client for Gemini and OpenAI-compatible providers.
Interactive mode keeps context in RAM for the current run.
One-shot mode stores full history and a compact summary in SQLite.
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import http.client
import urllib.parse
import threading
import itertools
import time
import sqlite3
import tempfile
import subprocess
import shutil
import platform
import difflib
import fnmatch
import re
import shlex
import signal
import math
import collections
import atexit
from contextlib import closing

DEFAULT_MODEL = "gemini-3.5-flash-lite"
OPENROUTER_DEFAULT_MODEL = "openrouter/free"
OPENROUTER_MODELS = {
  "auto": "openrouter/free",
  "nemotron-ultra": "nvidia/nemotron-3-ultra-550b-a55b:free",
  "nemotron-lightning": "nvidia/nemotron-3.5-lightning:free",
  "north-mini-code": "cohere/north-mini-code:free",
}
PROVIDERS = {
  "gemini": {"label": "Google Gemini", "env": "GEMINI_API_KEY", "base_url": "https://generativelanguage.googleapis.com/v1beta", "default_model": DEFAULT_MODEL, "key_url": "https://aistudio.google.com/app/apikey", "website": "https://ai.google.dev/gemini-api/docs"},
  "openai": {"label": "OpenAI", "env": "OPENAI_API_KEY", "base_url": "https://api.openai.com/v1", "default_model": "gpt-4.1-mini", "key_url": "https://platform.openai.com/api-keys", "website": "https://platform.openai.com/docs"},
  "openrouter": {"label": "OpenRouter", "env": "OPENROUTER_API_KEY", "base_url": "https://openrouter.ai/api/v1", "default_model": OPENROUTER_DEFAULT_MODEL, "key_url": "https://openrouter.ai/settings/keys", "website": "https://openrouter.ai/docs"},
  "groq": {"label": "Groq", "env": "GROQ_API_KEY", "base_url": "https://api.groq.com/openai/v1", "default_model": "openai/gpt-oss-120b", "key_url": "https://console.groq.com/keys", "website": "https://console.groq.com/docs"},
  "together": {"label": "Together AI", "env": "TOGETHER_API_KEY", "base_url": "https://api.together.ai/v1", "default_model": "meta-llama/Llama-3.3-70B-Instruct-Turbo", "key_url": "https://api.together.ai/settings/api-keys", "website": "https://docs.together.ai"},
  "custom": {"label": "Custom OpenAI-compatible", "env": "CUSTOM_API_KEY", "base_url": "", "default_model": "", "key_url": "", "website": "https://platform.openai.com/docs/api-reference"},
}
ACTIVE_PROVIDER = {"name": "gemini", "base_url": ""}
DEFAULT_LANGUAGE = "English"
API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
THINKING_LEVELS = ("auto", "low", "medium", "high")
THINKING_UNSUPPORTED = set()
RECENT_CONTEXT_CHARS = 16000
SUMMARY_CHUNK_CHARS = 8000
MAX_SUMMARY_CHARS = 6000
DEFAULT_SHELL_TIMEOUT = 60
DEFAULT_MAX_TOOL_STEPS = 12
MAX_COMMAND_OUTPUT_CHARS = 8000
RUN_COMMAND_TOOL = {
  "type": "function",
  "name": "run_command",
  "description": (
    "Run a shell command on the user's device and return its exit "
    "code, stdout and stderr. Use it to inspect or change files, "
    "check system state, or run programs. Prefer short, "
    "non-interactive commands."
  ),
  "parameters": {
    "type": "object",
    "properties": {
      "command": {
        "type": "string",
        "description": "The shell command to run.",
      }
    },
    "required": ["command"],
  },
}
DATA_DIR = os.path.join(os.path.expanduser("~"), ".gemini_cli")
DEFAULT_LOG_FILE = os.path.join(DATA_DIR, "actions.log")
INTERACTIVE_HISTORY_CHARS = 60000
POWER_PROGRAMS = {"reboot", "shutdown", "poweroff", "halt"}
DISK_PROGRAMS = {"wipefs", "fdisk", "sgdisk", "parted", "fastboot"}
ACTIVE_SPINNER = {"s": None}

# Progress lines for consumers that are not a terminal (e.g. the Tool-Tree chat reads stdout
# through a pipe, so the spinner cannot run). Enabled with --progress / GEMINI_CLI_PROGRESS=1.
# A status line ends with "\r": a reader that honors carriage returns replaces it with the next
# output, so it never becomes part of the answer.
PROGRESS = {"enabled": False, "pending": False, "col0": True}

def progress_enabled():
  return PROGRESS["enabled"] and not sys.stdout.isatty()

def _fit(text, limit=None):
  text = " ".join(str(text).split())
  if limit is None:
    if sys.stdout.isatty():
      limit = max(20, shutil.get_terminal_size((80, 24)).columns - 4)
    else:
      limit = 120
  return text if len(text) <= limit else text[:limit - 1] + "…"

def emit_progress(text):
  if not progress_enabled() or not text:
    return
  if not PROGRESS["col0"]:
    # Do not overwrite a half-written answer line: commit it first
    sys.stdout.write("\n")
    PROGRESS["col0"] = True
  sys.stdout.write(_fit(text) + "\r")
  sys.stdout.flush()
  PROGRESS["pending"] = True

def _clear_progress():
  """Remove a status line that nothing replaced (program ends, or an error is about to print).

  A "\\r" directly followed by "\\n" counts as a Windows line break and would keep the status
  text, so a space is written first: it replaces the status line, and the newline then ends
  the (blank) line.
  """
  if PROGRESS["pending"]:
    PROGRESS["pending"] = False
    PROGRESS["col0"] = True
    try:
      sys.stdout.write(" \n")
      sys.stdout.flush()
    except Exception:
      pass

atexit.register(_clear_progress)

def describe_tool_call(step):
  """Short human text for what a tool call is about to do (command, file path, pattern)."""
  name = step.get("name", "")
  arguments = step.get("arguments") or {}
  if isinstance(arguments, str):
    try:
      arguments = json.loads(arguments)
    except json.JSONDecodeError:
      arguments = {}
  if not isinstance(arguments, dict):
    return ""
  if name == "run_command":
    value = arguments.get("command", "")
  elif name == "search_files":
    value = arguments.get("pattern") or arguments.get("path") or ""
  else:
    value = arguments.get("path", "")
  return " ".join(str(value).split())
MAX_LOG_BYTES = 5000000
BLOCKED_REGEXES = [
  (r"\bmkfs(\.\w+)?\b", "formatting a filesystem"),
  (r"\bdd\b[^;&|\n]*\bof=/dev/", "raw write to a device"),
  (r">\s*/dev/(sd|mmcblk|block|nvme|loop)", "raw write to a device"),
  (r":\s*\(\s*\)\s*\{[^}]*:\s*\|\s*:", "fork bomb"),
]
COMMAND_WRAPPERS = {
  "sudo", "tsu", "env", "nohup", "command", "exec", "time",
  "busybox", "toybox", "nice", "setsid", "xargs",
}
SHELL_NAMES = {"sh", "bash", "dash", "zsh", "ash", "eval", "su"}
PROTECTED_DIRS = {
  "/", "/data", "/data/data", "/system", "/vendor", "/sdcard",
  "/storage", "/storage/emulated", "/storage/emulated/0", "/etc",
  "/usr", "/bin", "/sbin", "/home", "/root", "/proc", "/dev",
  "/mnt", "/opt", "/var", "/lib", "/boot",
}
MAX_READ_LINES = 400
MAX_SEARCH_RESULTS = 100
SKIP_DIRS = {".git", "node_modules", "__pycache__", ".svn"}
FILE_TOOLS = [
  {
    "type": "function",
    "name": "read_file",
    "description": (
      "Read a text file and return numbered lines. For long files "
      "read in parts with offset and limit."
    ),
    "parameters": {
      "type": "object",
      "properties": {
        "path": {"type": "string", "description": "File path."},
        "offset": {
          "type": "integer",
          "description": "First line to read, 1-based. Default 1.",
        },
        "limit": {
          "type": "integer",
          "description": "Maximum lines to return. Default 200.",
        },
      },
      "required": ["path"],
    },
  },
  {
    "type": "function",
    "name": "edit_file",
    "description": (
      "Replace exact text in an existing file. old_text must match "
      "the file exactly (including whitespace) and appear once, "
      "unless replace_all is true. Read the file first."
    ),
    "parameters": {
      "type": "object",
      "properties": {
        "path": {"type": "string", "description": "File path."},
        "old_text": {
          "type": "string",
          "description": "Exact text to find.",
        },
        "new_text": {
          "type": "string",
          "description": "Text to put in its place.",
        },
        "replace_all": {
          "type": "boolean",
          "description": "Replace every occurrence. Default false.",
        },
      },
      "required": ["path", "old_text", "new_text"],
    },
  },
  {
    "type": "function",
    "name": "write_file",
    "description": (
      "Create a new file or fully overwrite an existing one with "
      "the given content. Prefer edit_file for small changes."
    ),
    "parameters": {
      "type": "object",
      "properties": {
        "path": {"type": "string", "description": "File path."},
        "content": {
          "type": "string",
          "description": "Complete new file content.",
        },
      },
      "required": ["path", "content"],
    },
  },
  {
    "type": "function",
    "name": "search_files",
    "description": (
      "Search a directory tree. mode 'name' matches file names with "
      "a glob such as *.smali; mode 'content' finds lines matching "
      "a case-insensitive regular expression."
    ),
    "parameters": {
      "type": "object",
      "properties": {
        "pattern": {
          "type": "string",
          "description": "Glob (name mode) or regex (content mode).",
        },
        "path": {
          "type": "string",
          "description": "Directory to search. Default: current.",
        },
        "mode": {
          "type": "string",
          "enum": ["name", "content"],
          "description": "Default: content.",
        },
        "include": {
          "type": "string",
          "description": "Content mode only: file name glob filter.",
        },
        "max_results": {
          "type": "integer",
          "description": "Default 50, maximum 100.",
        },
      },
      "required": ["pattern"],
    },
  },
]
FILE_TOOL_NAMES = {tool["name"] for tool in FILE_TOOLS}
UI_TEXT = {
  "vi": {
    "title": "Chatbot CLI",
    "mode": "Bộ nhớ RAM",
    "thinking": "Suy nghĩ",
    "language": "Ngôn ngữ đầu ra",
    "help_hint": "Nhập /help để xem hướng dẫn.",
    "prompt": "Bạn",
    "goodbye": "Hẹn gặp lại lần sau.",
    "spinner": "AI đang tạo câu trả lời...",
    "tool_running": "AI đang thực hiện: {tool}",
    "help_title": "Các lệnh:",
    "help": "Hiển thị danh sách lệnh.",
    "thinking_help": "Thay đổi mức suy nghĩ.",
    "lang_help": "Thay đổi ngôn ngữ đầu ra (tên hoặc mã ngôn ngữ bất kỳ, ví dụ vi, ja, fr).",
    "instruction_help": "Thay đổi system instruction.",
    "exit_help": "Thoát chương trình.",
    "invalid_command": "Lệnh không hợp lệ. Nhập /help để xem trợ giúp.",
    "language_usage": "Cách dùng: /lang <ngôn ngữ> (ví dụ /lang vi, /lang ja)",
    "language_changed": "Đã đổi ngôn ngữ đầu ra: {language}",
    "instruction_changed": "Đã cập nhật hướng dẫn.",
    "thinking_prefix": "Mức suy nghĩ: {value}",
    "mode_label": "Chế độ",
    "model_label": "Mô hình",
    "thinking_usage": "Cách dùng: /thinking auto|low|medium|high",
    "instruction_usage": "Cách dùng: /instruction <text>",
    "timeout_error": "Timeout phải lớn hơn 0.",
    "missing_api_key": "Thiếu GEMINI_API_KEY.",
    "question_empty": "Câu hỏi không được để trống.",
    "instruction_error": "Lỗi hướng dẫn",
    "api_valid": "API key: HỢP LỆ",
    "model_accessible": "Model: CÓ THỂ TRUY CẬP",
    "model_name": "Tên model",
    "api_check_failed": "Kiểm tra API thất bại",
    "error": "Lỗi",
    "warning": "Cảnh báo: chưa thể cập nhật bộ nhớ tóm tắt",
    "memory_error": "Không thể xóa bộ nhớ",
    "shell_help": "Bật/tắt khả năng chạy lệnh trên thiết bị.",
    "shell_usage": "Cách dùng: /shell on|off|auto",
    "shell_state": "Chạy lệnh: {value}",
    "shell_label": "Chạy lệnh",
    "run_prompt": "Cho phép chạy lệnh này? [y = có / N = không / a = luôn cho phép]",
    "run_denied": "Đã từ chối chạy lệnh.",
    "run_non_tty": "Cần xác nhận nhưng không có terminal; dùng --yes để tự động cho phép.",
    "tool_limit": "Vượt quá số bước chạy lệnh tối đa.",
    "files_help": "Bật/tắt công cụ đọc, sửa, ghi, tìm file.",
    "files_usage": "Cách dùng: /files on|off",
    "files_state": "Công cụ file: {value}",
    "files_label": "Công cụ file",
    "apply_prompt": "Cho phép áp dụng thay đổi này? [y = có / N = không / a = luôn cho phép]",
    "all_help": "Bật hoặc tắt cùng lúc: chạy lệnh, công cụ file, tự động cho phép.",
    "all_usage": "Cách dùng: /all on|off",
    "all_state": "Tất cả: {value}",
    "stream_help": "Bật/tắt hiển thị câu trả lời dần dần (streaming).",
    "stream_usage": "Cách dùng: /stream on|off",
    "stream_state": "Streaming: {value}",
    "stream_label": "Streaming",
    "throttle_wait": "Đang chờ để không vượt giới hạn yêu cầu:",
    "net_down": "Mất kết nối mạng hoặc máy chủ đã ngắt kết nối. Hãy kiểm tra Internet rồi thử lại. ({detail})",
    "net_timeout": "Hết thời gian chờ phản hồi. Mạng có thể đang yếu hoặc bị mất, hãy kiểm tra Internet rồi thử lại.",
    "retry_wait": "Bị giới hạn tốc độ (HTTP {code}), thử lại {n}/{max} sau",
    "blocked": "Đã chặn lệnh nguy hiểm",
    "log_label": "Nhật ký",
    "cancelled": "Đã hủy câu hỏi.",
    "thinking_unsupported": "Model {model} không hỗ trợ mức suy nghĩ; đã bỏ qua --thinking.",
    "clear_help": "Xóa ngữ cảnh hội thoại hiện tại (RAM).",
    "clear_done": "Đã xóa ngữ cảnh hội thoại.",
  },
  "en": {
    "title": "Chatbot CLI",
    "mode": "RAM Memory",
    "thinking": "Thinking",
    "language": "Output language",
    "help_hint": "Type /help to see available commands.",
    "prompt": "You",
    "goodbye": "See you next time.",
    "spinner": "AI is generating the answer...",
    "tool_running": "AI is running: {tool}",
    "help_title": "Commands:",
    "help": "Show available commands.",
    "thinking_help": "Change thinking level.",
    "lang_help": "Change the chatbot output language (any language name or code, e.g. vi, ja, fr).",
    "instruction_help": "Change system instruction.",
    "exit_help": "Exit the program.",
    "invalid_command": "Invalid command. Type /help to see available commands.",
    "language_usage": "Usage: /lang <language> (for example /lang vi, /lang ja)",
    "language_changed": "Output language changed: {language}",
    "instruction_changed": "Instruction updated.",
    "thinking_prefix": "Thinking: {value}",
    "mode_label": "Mode",
    "model_label": "Model",
    "thinking_usage": "Usage: /thinking auto|low|medium|high",
    "instruction_usage": "Usage: /instruction <text>",
    "timeout_error": "Timeout must be greater than zero.",
    "missing_api_key": "Missing GEMINI_API_KEY.",
    "question_empty": "Question cannot be empty.",
    "instruction_error": "Instruction error",
    "api_valid": "API key: VALID",
    "model_accessible": "Model: ACCESSIBLE",
    "model_name": "Model name",
    "api_check_failed": "API check failed",
    "error": "Error",
    "warning": "Warning: memory summarization was deferred",
    "memory_error": "Cannot clear memory",
    "shell_help": "Turn device command execution on/off.",
    "shell_usage": "Usage: /shell on|off|auto",
    "shell_state": "Command execution: {value}",
    "shell_label": "Run commands",
    "run_prompt": "Allow this command? [y = yes / N = no / a = always]",
    "run_denied": "Command was denied.",
    "run_non_tty": "Confirmation needed but no terminal; use --yes to auto-approve.",
    "tool_limit": "Maximum number of command steps exceeded.",
    "files_help": "Turn file read/edit/write/search tools on/off.",
    "files_usage": "Usage: /files on|off",
    "files_state": "File tools: {value}",
    "files_label": "File tools",
    "apply_prompt": "Apply this change? [y = yes / N = no / a = always]",
    "all_help": "Turn on or off at once: commands, file tools, auto-approve.",
    "all_usage": "Usage: /all on|off",
    "all_state": "All: {value}",
    "stream_help": "Turn streamed (gradual) answers on/off.",
    "stream_usage": "Usage: /stream on|off",
    "stream_state": "Streaming: {value}",
    "stream_label": "Streaming",
    "throttle_wait": "Waiting to stay under the request limit:",
    "net_down": "Network connection lost or the server closed the connection. Check your Internet and try again. ({detail})",
    "net_timeout": "Timed out waiting for a response. The network may be weak or offline; check your Internet and try again.",
    "retry_wait": "Rate limited (HTTP {code}), retry {n}/{max} in",
    "blocked": "Blocked dangerous command",
    "log_label": "Log",
    "cancelled": "Question cancelled.",
    "thinking_unsupported": "Model {model} does not support a thinking level; --thinking was ignored.",
    "clear_help": "Clear the current conversation context (RAM).",
    "clear_done": "Conversation context cleared.",
  },
}

def normalize_language(value):
  """Keep the output language as typed (a name or a BCP-47 tag such as fr, ja, pt-BR); the
  model understands both, so no language table is needed. vi/vi-VN select Vietnamese (and the
  Vietnamese CLI); empty or en select English. Input is cleaned because it is placed inside
  the system instruction."""
  value = " ".join(str(value or "").split())
  value = re.sub(r"[^\w\s().,-]", "", value).replace("_", "-").strip()[:40]
  if not value:
    return DEFAULT_LANGUAGE
  key = value.lower()
  if key in ("vi", "vi-vn"):
    return "Vietnamese"
  if key == "en":
    return "English"
  return value

def get_cli_language(language):
  """Automatically match CLI interface language to the chatbot output language."""
  return "vi" if language == "Vietnamese" else "en"

class TerminalColors:
  RESET = "\033[0m"
  PALETTE = {
    "title": "#83BCE8",
    "label": "#91A0B0",
    "value": "#80BEA5",
    "ai": "#75BFA4",
    "gemini": "#91B4E4",
    "help": "#B49BD6",
    "spinner": "#D6B875",
    "error": "#D99494",
  }

  def __init__(self):
    self.enabled = (
      sys.stdout.isatty()
      and "NO_COLOR" not in os.environ
      and os.environ.get("TERM", "").lower() != "dumb"
    )
    self.truecolor = (
      "truecolor" in os.environ.get("COLORTERM", "").lower()
      or "24bit" in os.environ.get("COLORTERM", "").lower()
    )

  @staticmethod
  def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(
      int(value[index:index + 2], 16)
      for index in (0, 2, 4)
    )

  @staticmethod
  def rgb_to_256(r, g, b):
    def convert(value):
      return round(value / 255 * 5)
    red, green, blue = map(convert, (r, g, b))
    return 16 + 36 * red + 6 * green + blue

  def color_code(self, color):
    if not self.enabled:
      return ""
    r, g, b = self.hex_to_rgb(color)
    if self.truecolor:
      return f"\033[38;2;{r};{g};{b}m"
    return f"\033[38;5;{self.rgb_to_256(r, g, b)}m"

  def paint(self, text, name):
    if not self.enabled:
      return str(text)
    color = self.PALETTE.get(name)
    if not color:
      return str(text)
    return self.color_code(color) + str(text) + self.RESET

  def title(self, text):
    return self.paint(text, "title")

  def label(self, text):
    return self.paint(text, "label")

  def value(self, text):
    return self.paint(text, "value")

  def ai(self, text):
    return self.paint(text, "ai")

  def gemini(self, text):
    return self.paint(text, "gemini")

  def help(self, text):
    return self.paint(text, "help")

  def spinner(self, text):
    return self.paint(text, "spinner")

  def error(self, text):
    return self.paint(text, "error")

COLORS = TerminalColors()

ALLOW_MARKDOWN = False

ANSI_ESC = "\x1b"
_ANSI_TEXT_ESC = re.compile(
  r"(?:\\033|\\x1[bB]|\\u001[bB]|\\e|\^\[)\[([0-9;:]*)m"
)
_ANSI_BARE_ESC = re.compile(r"(?<![\[\x1b\\])\[((?:[0-9]{1,3})(?:;[0-9]{1,3}){0,6})m(?!\])")
_ANSI_PARTIAL = re.compile(
  r"(?:\\(?:0(?:3(?:3)?)?|x(?:1[bB]?)?|u(?:0(?:0(?:1[bB]?)?)?)?|e)?|\^\[?)?"
  r"(?:\[[0-9;:]*m?)?\Z"
)

# Text inside ``` fences is left untouched; only text outside them is converted.
FENCE = "```"

def _convert_ansi(text):
  text = _ANSI_TEXT_ESC.sub(lambda m: ANSI_ESC + "[" + m.group(1) + "m", text)
  return _ANSI_BARE_ESC.sub(lambda m: ANSI_ESC + "[" + m.group(1) + "m", text)

def normalize_ansi(text, close=False):
  parts = text.split(FENCE)
  for index in range(0, len(parts), 2):
    parts[index] = _convert_ansi(parts[index])
  text = FENCE.join(parts)
  if close and ANSI_ESC + "[" in text:
    body = text.rstrip("\n")
    text = body + COLORS.RESET + text[len(body):]
  return text

class AnsiStream:
  """Stateful normalize_ansi for text that arrives in arbitrary pieces."""

  def __init__(self):
    self.pending = ""
    self.last = " "
    self.used = False
    self.in_fence = False

  def feed(self, piece):
    text = self.pending + piece
    self.pending = ""
    hold = (len(text) - len(text.rstrip("`"))) % 3
    if hold:
      self.pending = text[-hold:]
      text = text[:-hold]
    parts = text.split(FENCE)
    ends_in_fence = self.in_fence != (len(parts) % 2 == 0)
    if not hold and not ends_in_fence:
      match = _ANSI_PARTIAL.search(parts[-1])
      if match and match.start() < len(parts[-1]) and len(parts[-1]) - match.start() <= 40:
        self.pending = parts[-1][match.start():]
        parts[-1] = parts[-1][:match.start()]
    out = []
    inside = self.in_fence
    prev = self.last
    for index, part in enumerate(parts):
      if index:
        out.append(FENCE)
        inside = not inside
        prev = "`"
      if not part:
        continue
      if inside:
        out.append(part)
      else:
        converted = _convert_ansi(prev + part)[len(prev):]
        if ANSI_ESC in converted:
          self.used = True
        out.append(converted)
      prev = part[-1]
    self.in_fence = inside
    self.last = prev
    return "".join(out)

  def finish(self):
    out = self.pending
    self.pending = ""
    if out and not self.in_fence:
      out = _convert_ansi(self.last + out)[len(self.last):]
      if ANSI_ESC in out:
        self.used = True
    if self.used:
      out += COLORS.RESET
      self.used = False
    self.in_fence = False
    self.last = " "
    return out

class Spinner:
  FRAMES = ("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

  def __init__(self, message="AI is generating the answer..."):
    self.message = message
    self.running = threading.Event()
    self.thread = None

  def _animate(self):
    frames = itertools.cycle(self.FRAMES)
    while self.running.is_set():
      frame = next(frames)
      sys.stdout.write(
        f"\r{COLORS.spinner(frame)} "
        f"{COLORS.spinner(self.message)}"
      )
      sys.stdout.flush()
      time.sleep(0.09)

  def start(self):
    if not sys.stdout.isatty():
      return
    self.running.set()
    self.thread = threading.Thread(
      target=self._animate,
      daemon=True,
    )
    self.thread.start()

  def stop(self):
    self.running.clear()
    if self.thread and self.thread.is_alive():
      self.thread.join(timeout=1)
    if self.thread is not None:
      sys.stdout.write("\r\033[2K")
      sys.stdout.flush()
      self.thread = None

def parse_args():
  parser = argparse.ArgumentParser(
    description=(
      "Chatbot CLI - RAM context in chat mode, "
      "persistent context in one-shot mode"
    )
  )
  parser.add_argument("--provider", choices=tuple(PROVIDERS), default="gemini", help="API provider.")
  parser.add_argument("--base-url", help="Base URL for a custom OpenAI-compatible API.")
  parser.add_argument("--model", help="Model ID; use --list-models to view available models.")
  parser.add_argument("--list-providers", action="store_true", help="List supported API providers.")
  parser.add_argument("--list-websites", action="store_true", help="List provider websites and API-key registration pages.")
  parser.add_argument("--list-models", action="store_true", help="Fetch available models from the selected provider.")
  parser.add_argument(
    "--thinking",
    choices=THINKING_LEVELS,
    default="auto",
    help=(
      "Thinking effort. Gemini: thinking_level. Other providers: sent "
      "as reasoning_effort and dropped automatically if the model "
      "does not accept it."
    ),
  )
  parser.add_argument(
    "--lang",
    default="en",
    metavar="LANG",
    help=(
      "Output language: any language name or code, e.g. vi, ja, zh-CN, pt-BR, French "
      "(default: en). The CLI messages are Vietnamese for vi and English otherwise."
    ),
  )
  parser.add_argument("--instruction")
  parser.add_argument("--instruction-file")
  parser.add_argument("--api-key")
  parser.add_argument("--timeout", type=int, default=120)
  parser.add_argument(
    "--shell",
    action="store_true",
    help="Let the chatbot run shell commands on this device (asks first).",
  )
  parser.add_argument(
    "--files",
    action="store_true",
    help=(
      "Let the chatbot read, edit, write and search files "
      "(writes ask first)."
    ),
  )
  parser.add_argument(
    "--all",
    action="store_true",
    help=(
      "Turn on everything at once: --shell, --files and --yes "
      "(no confirmation prompts). DANGEROUS."
    ),
  )
  parser.add_argument(
    "--stream",
    action="store_true",
    help="Show the answer gradually as the chatbot writes it.",
  )
  parser.add_argument(
    "--markdown",
    action="store_true",
    help=(
      "Allow Markdown in answers: removes the instruction that tells the AI to "
      "answer in plain text (also: GEMINI_CLI_MARKDOWN=1)."
    ),
  )
  parser.add_argument(
    "--progress",
    action="store_true",
    help=(
      "When stdout is not a terminal (e.g. read through a pipe), print what the AI is doing "
      "(waiting, running a command, reading a file) as one status line ending with a carriage "
      "return, which the next output replaces (also: GEMINI_CLI_PROGRESS=1)."
    ),
  )
  parser.add_argument(
    "--no-label",
    action="store_true",
    help="Do not print the provider label (for example 'Google Gemini ❯') before answers.",
  )
  parser.add_argument(
    "--yes",
    action="store_true",
    help=(
      "With --shell or --files: run commands and write files "
      "without asking. DANGEROUS."
    ),
  )
  parser.add_argument(
    "--allow-path",
    action="append",
    default=[],
    metavar="DIR",
    help=(
      "Extra directory the file tools may use (repeatable). "
      "By default they are limited to the start directory."
    ),
  )
  parser.add_argument(
    "--any-path",
    action="store_true",
    help="Let the file tools use any path. Not covered by --all.",
  )
  parser.add_argument(
    "--log-file",
    help=(
      "Where to record commands and file changes "
      f"(default {DEFAULT_LOG_FILE})."
    ),
  )
  parser.add_argument(
    "--no-log",
    action="store_true",
    help="Do not record commands and file changes.",
  )
  parser.add_argument(
    "--rpm",
    type=int,
    default=DEFAULT_RPM,
    help=(
      "Max requests per minute; the script waits to stay under it "
      f"(default {DEFAULT_RPM}, 0 = no limit)."
    ),
  )
  parser.add_argument(
    "--max-retries",
    type=int,
    default=DEFAULT_MAX_RETRIES,
    help=(
      "Retries after HTTP 429/503, waiting as the server asks "
      f"(default {DEFAULT_MAX_RETRIES}, 0 = never retry)."
    ),
  )
  parser.add_argument(
    "--shell-timeout",
    type=int,
    default=DEFAULT_SHELL_TIMEOUT,
    help="Seconds before a command is killed (default 60).",
  )
  parser.add_argument(
    "--max-tool-steps",
    type=int,
    default=DEFAULT_MAX_TOOL_STEPS,
    help="Maximum command rounds per question (default 12).",
  )
  parser.add_argument(
    "--memory-file",
    help="Path to the SQLite database used by one-shot mode.",
  )
  parser.add_argument(
    "--no-memory",
    action="store_true",
    help="Do not load or send saved memory/history in one-shot mode.",
  )
  parser.add_argument(
    "--clear-memory",
    action="store_true",
    help="Clear persistent one-shot memory and exit.",
  )
  parser.add_argument(
    "--check-api-key",
    action="store_true",
    help="Check API key validity and access to the selected model.",
  )
  parser.add_argument(
    "question",
    nargs="?",
    help="Ask one question and exit after receiving the answer.",
  )
  parser.add_argument(
    "-p",
    "--prompt",
    dest="prompt",
    help="Ask one question and exit after receiving the answer.",
  )
  args = parser.parse_args()
  if args.question is not None and args.prompt is not None:
    parser.error(
      "Use either a positional question or --prompt, not both."
    )
  if args.clear_memory and (
    args.question is not None or args.prompt is not None
  ):
    parser.error("--clear-memory cannot be combined with a question.")
  if args.check_api_key and (
    args.question is not None or args.prompt is not None
  ):
    parser.error("--check-api-key cannot be combined with a question.")
  if args.clear_memory and args.check_api_key:
    parser.error(
      "--clear-memory cannot be combined with --check-api-key."
    )
  if args.all:
    args.shell = True
    args.files = True
    args.yes = True
  if args.yes and not (args.shell or args.files):
    parser.error("--yes requires --shell or --files.")
  if args.rpm < 0 or args.max_retries < 0:
    parser.error("--rpm and --max-retries cannot be negative.")
  if args.shell_timeout <= 0 or args.max_tool_steps <= 0:
    parser.error(
      "--shell-timeout and --max-tool-steps must be greater than 0."
    )
  if args.provider == "custom" and not args.base_url and not (args.list_providers or args.list_websites):
    parser.error("--provider custom requires --base-url.")
  args.base_url = (args.base_url or PROVIDERS[args.provider]["base_url"]).rstrip("/")
  args.model = args.model or PROVIDERS[args.provider]["default_model"]
  if args.provider == "openrouter":
    args.model = OPENROUTER_MODELS.get(args.model, args.model)
  return args

def load_instruction(args):
  if args.instruction and args.instruction_file:
    raise ValueError(
      "Use --instruction or --instruction-file, not both."
    )
  if args.instruction_file:
    with open(args.instruction_file, "r", encoding="utf-8") as file:
      instruction = file.read().strip()
    if not instruction:
      raise ValueError("Instruction file is empty.")
    return instruction
  if args.instruction:
    instruction = args.instruction.strip()
    if not instruction:
      raise ValueError("Instruction is empty.")
    return instruction
  return (
    "You are a helpful, accurate and clear AI assistant. "
    "Admit uncertainty when information is insufficient."
  )

def build_system_instruction(
  instruction,
  language,
  memory_summary=None,
  shell_enabled=False,
  files_enabled=False,
):
  language_rule = (
    f"Always write the complete response in the application language: {language} "
    "(a language name or a BCP-47 language tag; use the language it denotes). "
    "The application language has priority over the language used in the user's input. "
    "Do not automatically switch response language. Keep source-code identifiers, "
    "function names, library names, API names, shell commands and technical terms "
    "unchanged when translating them would alter their meaning. Keep the language "
    "and conventions of existing file content unless the user requests otherwise; "
    "explicit output-language or file-language requirements apply to the requested artifact. "
    "Use natural grammar, spelling, terminology and writing conventions."
  )
  plain_text_rule = (
    "For ordinary text responses, use plain text. Do not wrap ordinary text in Markdown "
    "formatting, headings, bold/italic markers or code fences. Use natural paragraphs "
    "and line breaks. When code is requested, output code directly without surrounding "
    "Markdown fences; preserve syntax, indentation and all meaningful characters."
  )
  color_rule = (
    "The answer is shown in a terminal that renders ANSI SGR color and style sequences. "
    "When the user asks to print, show or demonstrate colors or styled text, write the "
    "ANSI sequences directly in the answer text, spelled as \\033[<codes>m (backslash, 0, "
    "3, 3, open bracket); they are converted to real escape characters automatically. "
    "Examples: \\033[31mred\\033[0m, \\033[1;32mbold green\\033[0m, "
    "\\033[38;5;208morange\\033[0m, \\033[38;2;255;105;180mpink\\033[0m. Close every "
    "colored part with \\033[0m. Do not say colors are unsupported, do not print the "
    "sequences inside code fences to produce color (text inside a code fence is shown literally and "
    "never converted; use a fence only to show the sequences themselves as code), and do not run "
    "a shell command only to display colors: "
    "command output goes to you, not directly to the user."
  )
  scope_rule = (
    "Stay within the scope of the user's request. Do not expand the task, redesign "
    "architecture, optimize unrelated components, add dependencies or change project "
    "configuration unless required. Do not ask for information already available in "
    "the conversation or environment. Ask one focused question only when ambiguity "
    "could cause a materially different or risky result. Distinguish confirmed facts "
    "from assumptions and admit uncertainty when evidence is insufficient."
  )
  parts = [instruction, language_rule]
  if not ALLOW_MARKDOWN:
    parts.append(plain_text_rule)
  parts.extend([color_rule, scope_rule])
  if shell_enabled:
    parts.append(
      "You can run shell commands on the user's device with the run_command tool. "
      "Current directory: " + os.getcwd() + ". System: " + platform.system() + ". "
      "Run only commands needed for the task and prefer read-only inspection first. "
      "Check and report each command's exit code; a command is not successful merely "
      "because execution ended, and a nonzero exit code must not be reported as success. "
      "Avoid unrelated side effects and destructive operations such as deleting data, "
      "formatting, bulk changes or overwriting unless clearly requested. Do not expose "
      "API keys, passwords, tokens or other secrets in output. If the user denies a "
      "command, do not retry it in disguised form."
    )
  if files_enabled:
    parts.append(
      "You can use read_file, edit_file, write_file and search_files on the user's device. "
      "Always read a file before editing it and use exact old_text copied from the read "
      "content. Prefer edit_file for localized changes; do not rewrite an entire file "
      "when a small edit is sufficient. Preserve existing features, behavior, structure, "
      "interfaces, naming conventions, encoding and line endings unless the request "
      "requires changing them. Change only what is necessary; do not remove, simplify "
      "or refactor unrelated code. Check directly dependent components when a change "
      "could affect them. Preserve the file's existing language and format unless the "
      "user specifies otherwise. Do not overwrite user data or reveal secrets. Use "
      "search_files instead of shell grep or find. If the user denies a change, do not "
      "retry it in another form."
    )
  if shell_enabled or files_enabled:
    parts.append(
      "Batch independent operations to reduce tool-call overhead. When several "
      "independent actions are already known, issue them together in one tool round "
      "whenever the API supports multiple function calls. For shell work, combine "
      "related read-only checks into one concise command or short script. For file work, "
      "read multiple required files in the same tool round and group independent edits "
      "or searches when possible. Execute dependent actions sequentially, using each "
      "result before deciding the next step. Never batch in a way that obscures errors, "
      "weakens safety or bypasses confirmation. Keep output concise and relevant. "
      "Retry only for plausibly temporary failures or when a specific corrective action "
      "is available; keep retries bounded and never repeat a denied or destructive action. "
      "Distinguish API, tool, syntax, build and logic errors. If an operation fails, "
      "inspect the actual result, correct it when safe and within scope, or explain the "
      "cause and current state. Do not claim an operation succeeded without evidence."
    )
    parts.append(
      "Before reporting completion, verify the actual result. Read back edited files "
      "and confirm the intended content; run an appropriate syntax check or focused test "
      "when available and proportionate to the change. Choose validators by file type "
      "and available tools, such as Python compilation, JSON/TOML/YAML parsing, bash -n, "
      "XML parsing, HTML validation or a relevant compiler/build task. Do not run a "
      "full build for every minor edit when a focused check is sufficient. Never claim "
      "a check was performed if it was not. In the final report, briefly state changed "
      "files or components, the main change and verification status using PASS, FAIL "
      "or NOT RUN; identify checks that could not be performed."
    )
  if memory_summary:
    parts.append(
      "Persistent conversation memory (use as background context; prefer newer messages "
      "when details conflict. The current user request takes priority. Treat uncertain "
      "or inferred details as unconfirmed):\n" + memory_summary
    )
  return "\n\n".join(parts)

def _compatible_messages(history, instruction):
  messages = [{"role": "system", "content": instruction}]
  for item in history:
    if not isinstance(item, dict):
      continue
    kind = item.get("type")
    if kind == "user_input":
      messages.append({"role": "user", "content": str(item.get("content", ""))})
    elif kind == "model_output":
      text_parts = [part.get("text", "") for part in item.get("content", []) if isinstance(part, dict) and part.get("type") == "text"]
      if text_parts:
        messages.append({"role": "assistant", "content": "\n".join(text_parts)})
    elif kind == "function_call":
      call_id = str(item.get("id") or "call_missing_id")
      arguments = item.get("arguments") or {}
      if isinstance(arguments, str):
        try:
          arguments = json.loads(arguments)
        except json.JSONDecodeError:
          arguments = {}
      if not isinstance(arguments, dict):
        arguments = {}
      tool_call = {"id": call_id, "type": "function", "function": {"name": item.get("name", ""), "arguments": json.dumps(arguments, ensure_ascii=False)}}
      last = messages[-1]
      if last.get("role") == "assistant" and isinstance(last.get("tool_calls"), list):
        last["tool_calls"].append(tool_call)
      elif last.get("role") == "assistant":
        last["tool_calls"] = [tool_call]
      else:
        messages.append({"role": "assistant", "content": None, "tool_calls": [tool_call]})
    elif kind == "function_result":
      result_parts = [part.get("text", "") for part in item.get("result", []) if isinstance(part, dict) and part.get("type") == "text"]
      messages.append({"role": "tool", "tool_call_id": str(item.get("call_id") or "call_missing_id"), "name": item.get("name", ""), "content": "\n".join(result_parts)})
  return messages

def _compatible_tools(shell_enabled, files_enabled):
  tools = ([RUN_COMMAND_TOOL] if shell_enabled else []) + (FILE_TOOLS if files_enabled else [])
  return [{"type": "function", "function": {"name": tool["name"], "description": tool["description"], "parameters": tool["parameters"]}} for tool in tools]

def _compatible_urlopen(request, timeout, status_callback=None):
  attempt = 0
  provider = PROVIDERS[ACTIVE_PROVIDER["name"]]["label"]
  while True:
    RATE_LIMITER.wait(status_callback)
    try:
      return urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as exc:
      body = exc.read().decode("utf-8", errors="replace")
      try:
        error = json.loads(body).get("error", {})
        message = error.get("message", body) if isinstance(error, dict) else str(error)
      except json.JSONDecodeError:
        message = body
      if exc.code in (429, 503) and attempt < RETRY["max"]:
        delay = _retry_delay(exc, message, body, attempt)
        if delay <= MAX_RETRY_WAIT:
          attempt += 1
          _wait(delay + 1, _ui_text("retry_wait", code=exc.code, n=attempt, max=RETRY["max"]), status_callback)
          continue
      raise RuntimeError(f"{provider} HTTP {exc.code}: {message}") from exc
    except NETWORK_ERRORS as exc:
      raise _net_error(exc, provider) from exc

def call_compatible_api(api_key, model, history, instruction, language, thinking, timeout, memory_summary=None, shell_enabled=False, files_enabled=False, stream_callback=None, status_callback=None):
  system_instruction = build_system_instruction(instruction, language, memory_summary, shell_enabled, files_enabled)
  payload = {"model": model, "messages": _compatible_messages(history, system_instruction), "stream": bool(stream_callback and not (shell_enabled or files_enabled))}
  thinking_key = (ACTIVE_PROVIDER["name"], model)
  if thinking != "auto" and thinking_key not in THINKING_UNSUPPORTED:
    payload["reasoning_effort"] = thinking
  tools = _compatible_tools(shell_enabled, files_enabled)
  if tools:
    payload["tools"] = tools
    payload["tool_choice"] = "auto"
  headers = {"Content-Type": "application/json", "Authorization": "Bearer " + api_key}
  if ACTIVE_PROVIDER["name"] == "openrouter":
    headers.update({"HTTP-Referer": "https://github.com/Zenlua/Tool-Tree", "X-Title": "Chatbot CLI"})

  def build_request():
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    return urllib.request.Request(ACTIVE_PROVIDER["base_url"] + "/chat/completions", data=body, headers=headers, method="POST")

  try:
    opened = _compatible_urlopen(build_request(), timeout, status_callback)
  except RuntimeError as exc:
    text = str(exc).lower()
    if "reasoning_effort" in payload and "http 400" in text and ("reason" in text or "thinking" in text):
      THINKING_UNSUPPORTED.add(thinking_key)
      del payload["reasoning_effort"]
      print(COLORS.error(_ui_text("thinking_unsupported", model=model)), file=sys.stderr)
      opened = _compatible_urlopen(build_request(), timeout, status_callback)
    else:
      raise
  try:
    return _compatible_read(opened, payload, stream_callback)
  except NETWORK_ERRORS as exc:
    raise _net_error(exc, PROVIDERS[ACTIVE_PROVIDER["name"]]["label"]) from exc

def _compatible_read(opened, payload, stream_callback):
  with opened as response:
    if payload["stream"]:
      pieces = []
      for raw in response:
        line = raw.decode("utf-8", errors="replace").strip()
        if not line.startswith("data:"):
          continue
        chunk = line[5:].strip()
        if not chunk or chunk == "[DONE]":
          continue
        try:
          event = json.loads(chunk)
        except json.JSONDecodeError:
          continue
        if event.get("error"):
          raise RuntimeError(ACTIVE_PROVIDER["name"].title() + " stream error: " + str(event["error"]))
        choices = event.get("choices") or []
        delta = choices[0].get("delta", {}) if choices else {}
        piece = delta.get("content")
        if isinstance(piece, str) and piece:
          pieces.append(piece)
          stream_callback(piece)
      answer = "".join(pieces).strip()
      if not answer:
        raise RuntimeError(ACTIVE_PROVIDER["name"].title() + " returned no text.")
      return answer, [{"type": "model_output", "content": [{"type": "text", "text": answer}]}]
    data = json.loads(response.read().decode("utf-8"))
  choices = data.get("choices") or []
  if not choices:
    raise RuntimeError(ACTIVE_PROVIDER["name"].title() + " returned no choices: " + json.dumps(data, ensure_ascii=False)[:1200])
  message = choices[0].get("message") or {}
  answer = (message.get("content") or "").strip() if isinstance(message.get("content"), str) else ""
  steps = []
  for call in message.get("tool_calls") or []:
    function = call.get("function") or {}
    raw_args = function.get("arguments") or "{}"
    try:
      arguments = json.loads(raw_args) if isinstance(raw_args, str) else raw_args
    except json.JSONDecodeError:
      arguments = {}
    steps.append({"type": "function_call", "id": call.get("id"), "name": function.get("name", ""), "arguments": arguments})
  if answer:
    steps.insert(0, {"type": "model_output", "content": [{"type": "text", "text": answer}]})
  if not steps:
    raise RuntimeError(ACTIVE_PROVIDER["name"].title() + " returned neither text nor tool calls.")
  return answer, steps

def list_providers(show_websites=False):
  print("Supported AI providers" if not show_websites else "Provider websites and API-key registration")
  for key, item in PROVIDERS.items():
    if show_websites:
      registration = item["key_url"] or "Configure your own API service"
      print(f"  {item['label']:<30} {registration}")
      if item.get("website"):
        print(f"    Documentation: {item['website']}")
    else:
      print(f"  {key:<12} {item['label']}")

def _fetch_json(url, api_key, timeout, gemini=False):
  headers = {"x-goog-api-key": api_key} if gemini else ({"Authorization": "Bearer " + api_key} if api_key else {})
  request = urllib.request.Request(url, headers=headers, method="GET")
  try:
    with urllib.request.urlopen(request, timeout=timeout) as response:
      return json.loads(response.read().decode("utf-8"))
  except urllib.error.HTTPError as exc:
    body = exc.read().decode("utf-8", errors="replace")
    try:
      data = json.loads(body)
      error = data.get("error", {})
      message = error.get("message", body) if isinstance(error, dict) else str(error)
    except json.JSONDecodeError:
      message = body
    raise RuntimeError(f"HTTP {exc.code}: {message}") from exc
  except NETWORK_ERRORS as exc:
    raise _net_error(exc) from exc
  except json.JSONDecodeError as exc:
    raise RuntimeError("Invalid JSON response.") from exc

def fetch_provider_models(provider, api_key, base_url, timeout):
  if provider == "gemini":
    models, token = [], None
    while True:
      url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
      if token:
        url += "&pageToken=" + urllib.parse.quote(token, safe="")
      data = _fetch_json(url, api_key, timeout, gemini=True)
      models.extend(data.get("models", []))
      token = data.get("nextPageToken")
      if not token:
        break
    return sorted(({"id": m.get("name", "").removeprefix("models/"), "name": m.get("displayName", "")} for m in models if m.get("name")), key=lambda x: x["id"].lower())
  data = _fetch_json(base_url.rstrip("/") + "/models", api_key, timeout)
  items = data.get("data", data.get("models", [])) if isinstance(data, dict) else data
  if not isinstance(items, list):
    raise RuntimeError("Provider returned an unsupported model-list format.")
  return sorted(({"id": item.get("id") or item.get("name", ""), "name": item.get("name", "")} for item in items if isinstance(item, dict) and (item.get("id") or item.get("name"))), key=lambda x: x["id"].lower())

def print_models(provider, models, selected=None):
  print(f"Available models — {PROVIDERS[provider]['label']} ({len(models)})")
  for item in models:
    model_id = item["id"]
    marker = " *" if model_id == selected else "  "
    label = f" — {item['name']}" if item.get("name") and item["name"] != model_id else ""
    print(f"{marker} {model_id}{label}")

def check_api_key_model(api_key, model, timeout):
  base_url = "https://generativelanguage.googleapis.com/v1beta/models"

  def request_model(url):
    request = urllib.request.Request(
      url,
      headers={"x-goog-api-key": api_key},
      method="GET",
    )
    try:
      with urllib.request.urlopen(
        request,
        timeout=timeout,
      ) as response:
        return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
      body = exc.read().decode("utf-8", errors="replace")
      try:
        error_data = json.loads(body).get("error", {})
        message = error_data.get("message", body)
        status = error_data.get("status", "")
      except json.JSONDecodeError:
        message, status = body, ""
      raise RuntimeError(
        f"HTTP {exc.code} ({status}): {message}"
      ) from exc
    except NETWORK_ERRORS as exc:
      raise _net_error(exc) from exc
    except json.JSONDecodeError as exc:
      raise RuntimeError("Invalid JSON response.") from exc

  request_model(base_url + "?pageSize=1")
  model_name = model.strip()
  if model_name.startswith("models/"):
    model_name = model_name[len("models/"):]
  if not model_name:
    raise RuntimeError("Model name cannot be empty.")
  model_url = (
    base_url + "/" + urllib.parse.quote(model_name, safe="-._~")
  )
  model_info = request_model(model_url)
  if not isinstance(model_info, dict):
    raise RuntimeError("Invalid model information response.")
  if not model_info.get("name"):
    raise RuntimeError("Model information is incomplete.")
  return model_info

def extract_text(data):
  output_text = data.get("output_text")
  if isinstance(output_text, str) and output_text.strip():
    return output_text.strip()
  results = []
  for step in data.get("steps", []):
    if step.get("type") != "model_output":
      continue
    for item in step.get("content", []):
      if item.get("type") == "text":
        value = item.get("text", "")
        if value:
          results.append(value)
  return "\n".join(results).strip()

DEFAULT_RPM = 15
DEFAULT_MAX_RETRIES = 3
MAX_RETRY_WAIT = 120
RETRY = {"max": DEFAULT_MAX_RETRIES}
ACTIVE_UI = {"ui": None}

def _ui_text(key, **values):
  ui = ACTIVE_UI["ui"] or UI_TEXT["en"]
  return ui[key].format(**values)

NETWORK_ERRORS = (OSError, http.client.HTTPException)

def _net_error(exc, provider=""):
  """Build a readable RuntimeError for network failures
  (offline, connection reset, DNS, SSL, timeout, truncated reply)."""
  prefix = f"{provider}: " if provider else ""
  reason = getattr(exc, "reason", exc)
  if isinstance(exc, TimeoutError) or isinstance(reason, TimeoutError):
    return RuntimeError(prefix + _ui_text("net_timeout"))
  detail = str(reason).strip() or type(reason).__name__
  return RuntimeError(prefix + _ui_text("net_down", detail=detail))

def _wait(seconds, label, status=None):
  """Sleep with a visible notice. status(text) updates the spinner."""
  seconds = max(0, int(math.ceil(seconds)))
  if status is None:
    if seconds >= 1:
      print(
        COLORS.spinner(f"{label} {seconds}s..."),
        file=sys.stderr,
      )
    time.sleep(seconds)
    return
  end = time.time() + seconds
  while True:
    left = end - time.time()
    if left <= 0:
      break
    status(f"{label} {int(left) + 1}s")
    time.sleep(min(1.0, left))
  status(None)

class RateLimiter:
  """Sliding window: never more than rpm requests in any 60 seconds."""

  def __init__(self, rpm=0):
    self.rpm = rpm
    self.times = collections.deque()

  def wait(self, status=None):
    if self.rpm <= 0:
      return
    while True:
      now = time.time()
      while self.times and now - self.times[0] >= 60:
        self.times.popleft()
      if len(self.times) < self.rpm:
        break
      delay = 60 - (now - self.times[0]) + 0.5
      _wait(delay, _ui_text("throttle_wait"), status)
    self.times.append(time.time())

RATE_LIMITER = RateLimiter(DEFAULT_RPM)

def _retry_delay(exc, message, body, attempt):
  """Seconds to wait before retrying: server hint, else backoff."""
  header = exc.headers.get("Retry-After") if exc.headers else None
  if header:
    try:
      return float(header)
    except ValueError:
      pass
  for text in (message, body):
    text = text or ""
    match = (
      re.search(r"retry in (\d+(?:\.\d+)?)\s*s", text, re.IGNORECASE)
      or re.search(r'"retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s"', text)
    )
    if match:
      return float(match.group(1))
  return 5.0 * (2 ** attempt)

def _send_request(request, timeout, stream_callback, status):
  """POST with rate limiting; retry on HTTP 429 and 503."""
  attempt = 0
  while True:
    RATE_LIMITER.wait(status)
    try:
      with urllib.request.urlopen(
        request,
        timeout=timeout,
      ) as response:
        if stream_callback is not None:
          return _read_stream(response, stream_callback)
        return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
      error_body = exc.read().decode("utf-8", errors="replace")
      try:
        error_data = json.loads(error_body)
        error_message = error_data.get("error", {}).get(
          "message", error_body
        )
      except (json.JSONDecodeError, AttributeError):
        error_message = error_body
      if exc.code in (429, 503) and attempt < RETRY["max"]:
        delay = _retry_delay(exc, error_message, error_body, attempt)
        if delay <= MAX_RETRY_WAIT:
          attempt += 1
          label = _ui_text(
            "retry_wait",
            code=exc.code,
            n=attempt,
            max=RETRY["max"],
          )
          _wait(delay + 1, label, status)
          continue
      raise RuntimeError(
        f"HTTP {exc.code}: {error_message}"
      ) from exc
    except NETWORK_ERRORS as exc:
      raise _net_error(exc) from exc
    except json.JSONDecodeError as exc:
      raise RuntimeError("Invalid JSON response.") from exc

def _read_stream(response, on_text):
  """Read a Server-Sent Events stream from the Interactions API.

  Text deltas are passed to on_text as they arrive. If the model starts
  a function call, the caller repeats the request without streaming so
  it gets the exact steps (thought signatures included).
  """
  parts = []
  step_types = {}
  saw_call = False
  status = ""
  event_name = ""
  for raw in response:
    line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
    if line.startswith("event:"):
      event_name = line[6:].strip()
      continue
    if not line.startswith("data:"):
      continue
    body = line[5:].strip()
    if not body or body == "[DONE]":
      continue
    try:
      event = json.loads(body)
    except json.JSONDecodeError:
      continue
    if not isinstance(event, dict):
      continue
    kind = event.get("event_type") or event_name
    if kind == "error":
      raise RuntimeError(
        "Stream error: "
        + json.dumps(event.get("error", event), ensure_ascii=False)
      )
    if kind == "step.start":
      step = event.get("step")
      step_type = (
        step.get("type") if isinstance(step, dict)
        else event.get("type")
      )
      step_types[event.get("index")] = step_type
      if step_type == "function_call" or (
        '"function_call"' in json.dumps(event)
      ):
        saw_call = True
    elif kind == "step.delta":
      delta = event.get("delta")
      if not isinstance(delta, dict):
        continue
      delta_type = delta.get("type")
      if delta_type == "arguments_delta":
        saw_call = True
      elif delta_type == "text":
        if step_types.get(event.get("index")) == "thought":
          continue
        piece = delta.get("text", "")
        if piece and not saw_call:
          parts.append(piece)
          on_text(piece)
    elif kind in ("interaction.completed", "interaction.status_update"):
      info = event.get("interaction")
      if not isinstance(info, dict):
        info = event
      status = info.get("status", status) or status
  if status == "requires_action":
    saw_call = True
  text = "".join(parts)
  steps = []
  if text:
    steps.append({
      "type": "model_output",
      "content": [{"type": "text", "text": text}],
    })
  return {"steps": steps, "status": status, "_saw_call": saw_call}

def call_gemini(
  api_key,
  model,
  history,
  instruction,
  language,
  thinking,
  timeout,
  memory_summary=None,
  system_instruction_override=None,
  shell_enabled=False,
  files_enabled=False,
  stream_callback=None,
  status_callback=None,
):
  if ACTIVE_PROVIDER["name"] != "gemini":
    effective_instruction = system_instruction_override if system_instruction_override is not None else instruction
    return call_compatible_api(api_key, model, history, effective_instruction, language, thinking, timeout, memory_summary, shell_enabled, files_enabled, stream_callback, status_callback)
  payload = {
    "model": model,
    "input": history,
    "store": False,
    "system_instruction": (
      system_instruction_override
      if system_instruction_override is not None
      else build_system_instruction(
        instruction,
        language,
        memory_summary,
        shell_enabled,
        files_enabled,
      )
    ),
  }
  tools = []
  if shell_enabled:
    tools.append(RUN_COMMAND_TOOL)
  if files_enabled:
    tools.extend(FILE_TOOLS)
  if tools:
    payload["tools"] = tools
  if stream_callback is not None:
    payload["stream"] = True
  if thinking != "auto":
    payload["generation_config"] = {
      "thinking_level": thinking
    }
  body = json.dumps(
    payload,
    ensure_ascii=False,
  ).encode("utf-8")
  request = urllib.request.Request(
    API_URL,
    data=body,
    headers={
      "Content-Type": "application/json",
      "x-goog-api-key": api_key,
    },
    method="POST",
  )
  data = _send_request(
    request,
    timeout,
    stream_callback,
    status_callback,
  )
  if data.get("_saw_call") or (
    stream_callback is not None and not extract_text(data)
  ):
    return call_gemini(
      api_key=api_key,
      model=model,
      history=history,
      instruction=instruction,
      language=language,
      thinking=thinking,
      timeout=timeout,
      memory_summary=memory_summary,
      system_instruction_override=system_instruction_override,
      shell_enabled=shell_enabled,
      files_enabled=files_enabled,
      stream_callback=None,
      status_callback=status_callback,
    )
  if data.get("status") in ("failed", "cancelled"):
    raise RuntimeError(
      f"Interaction status: {data.get('status')}"
    )
  answer = extract_text(data)
  generated_steps = data.get("steps", [])
  if not isinstance(generated_steps, list):
    raise RuntimeError("Invalid API steps response.")
  has_calls = any(
    isinstance(step, dict) and step.get("type") == "function_call"
    for step in generated_steps
  )
  if has_calls:
    return answer, generated_steps
  if not answer:
    raise RuntimeError("Gemini returned no text.")
  if not generated_steps:
    generated_steps = [{
      "type": "model_output",
      "content": [
        {"type": "text", "text": answer}
      ],
    }]
  return answer, generated_steps

class ShellPolicy:
  """Per-run settings for command execution."""

  def __init__(self, enabled=False, auto_approve=False,
               timeout=DEFAULT_SHELL_TIMEOUT,
               max_steps=DEFAULT_MAX_TOOL_STEPS,
               files_enabled=False,
               allowed_roots=None,
               log_path=None):
    self.allowed_roots = allowed_roots
    self.log_path = log_path
    self.last_decision = "n/a"
    self.files_enabled = files_enabled
    self.enabled = enabled
    self.auto_approve = auto_approve
    self.timeout = timeout
    self.max_steps = max_steps

def _find_shell():
  for name in ("bash", "sh"):
    found = shutil.which(name)
    if found:
      return found
  for fallback in ("/system/bin/sh", "/bin/sh"):
    if os.path.exists(fallback):
      return fallback
  return None

def _clip(text, limit=MAX_COMMAND_OUTPUT_CHARS):
  if len(text) <= limit:
    return text
  half = limit // 2
  return (
    text[:half]
    + f"\n...[{len(text) - limit} characters omitted]...\n"
    + text[-half:]
  )

def _kill_group(proc):
  """Kill the command's whole process group, children included."""
  try:
    if hasattr(os, "killpg"):
      os.killpg(proc.pid, signal.SIGKILL)
    else:
      proc.kill()
  except OSError:
    try:
      proc.kill()
    except OSError:
      pass

def _collect_after_kill(proc):
  """Read what is left; never wait long on pipes held by orphans."""
  try:
    out, err = proc.communicate(timeout=2)
  except subprocess.TimeoutExpired:
    out, err = b"", b""
  for pipe in (proc.stdout, proc.stderr):
    try:
      if pipe:
        pipe.close()
    except OSError:
      pass
  return (
    _clip((out or b"").decode("utf-8", errors="replace")),
    _clip((err or b"").decode("utf-8", errors="replace")),
  )

def execute_command(command, timeout):
  """Run a command in its own process group; return a JSON-able dict."""
  shell = _find_shell()
  if not shell:
    return {"error": "No shell found on this device."}
  try:
    proc = subprocess.Popen(
      [shell, "-c", command],
      stdin=subprocess.DEVNULL,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
      cwd=os.getcwd(),
      start_new_session=True,
    )
  except OSError as exc:
    return {"error": f"Cannot run command: {exc}"}
  try:
    out, err = proc.communicate(timeout=timeout)
  except subprocess.TimeoutExpired:
    _kill_group(proc)
    out, err = _collect_after_kill(proc)
    return {
      "error": (
        f"Command timed out after {timeout}s; it and its child "
        "processes were killed."
      ),
      "stdout": out,
      "stderr": err,
    }
  except KeyboardInterrupt:
    _kill_group(proc)
    out, err = _collect_after_kill(proc)
    return {
      "error": "Interrupted by the user (Ctrl+C); the command was stopped.",
      "stdout": out,
      "stderr": err,
    }
  return {
    "exit_code": proc.returncode,
    "stdout": _clip(out.decode("utf-8", errors="replace")),
    "stderr": _clip(err.decode("utf-8", errors="replace")),
  }

def _protected_target(arg, home, prefix):
  expanded = os.path.expandvars(os.path.expanduser(arg))
  if expanded in ("*", ".", "..", "./*", "../*", ".*", "~"):
    return True
  base = expanded[:-2] if expanded.endswith("/*") else expanded
  path = os.path.normpath(os.path.abspath(base or "/"))
  protected = set(PROTECTED_DIRS)
  protected.add(os.path.normpath(home))
  if prefix:
    protected.add(os.path.normpath(prefix))
  return path in protected

def _protected_path(arg, home, prefix):
  expanded = os.path.expandvars(os.path.expanduser(arg))
  base = expanded[:-2] if expanded.endswith("/*") else expanded
  path = os.path.normpath(os.path.abspath(base or "/"))
  protected = set(PROTECTED_DIRS)
  protected.add(os.path.normpath(home))
  if prefix:
    protected.add(os.path.normpath(prefix))
  return path in protected

def check_command_guard(command, depth=0):
  """Return a reason string if the command looks destructive."""
  for pattern, reason in BLOCKED_REGEXES:
    if re.search(pattern, command, re.IGNORECASE):
      return reason
  if depth > 3:
    return None
  home = os.path.expanduser("~")
  prefix = os.environ.get("PREFIX", "")
  for segment in re.split(r"[;&|\n`()]+|\$\(", command):
    try:
      tokens = shlex.split(segment)
    except ValueError:
      tokens = segment.split()
    while tokens and (
      tokens[0] in COMMAND_WRAPPERS
      or re.match(r"^\w+=", tokens[0])
    ):
      tokens = tokens[1:]
    if not tokens:
      continue
    program = os.path.basename(tokens[0])
    if program in POWER_PROGRAMS:
      return "power control"
    if program in DISK_PROGRAMS:
      return "partition or bootloader tool"
    if program in SHELL_NAMES:
      for token in tokens[1:]:
        if not token.startswith("-"):
          nested = check_command_guard(token, depth + 1)
          if nested:
            return nested
      continue
    if program == "find":
      fargs = tokens[1:]
      starts = []
      for arg in fargs:
        if arg.startswith(("-", "(", "!")):
          break
        starts.append(arg)
      deleting = "-delete" in fargs or "rm" in fargs
      if deleting:
        for start in starts:
          if _protected_path(start, home, prefix):
            return f"bulk delete under '{start}'"
      continue
    if program in ("chmod", "chown", "chgrp"):
      cargs = tokens[1:]
      if any(
        a == "--recursive"
        or (a.startswith("-") and not a.startswith("--") and "R" in a)
        for a in cargs
      ):
        for a in cargs:
          if not a.startswith("-") and _protected_path(a, home, prefix):
            return f"recursive permission change on '{a}'"
      continue
    if program != "rm":
      continue
    args = tokens[1:]
    recursive = any(
      arg == "--recursive"
      or (
        arg.startswith("-")
        and not arg.startswith("--")
        and ("r" in arg or "R" in arg)
      )
      for arg in args
    )
    if not recursive:
      continue
    targets = []
    after_dashes = False
    for arg in args:
      if arg == "--" and not after_dashes:
        after_dashes = True
        continue
      if after_dashes or not arg.startswith("-"):
        targets.append(arg)
    for target in targets:
      if _protected_target(target, home, prefix):
        return f"recursive delete of '{target}'"
  return None

def check_path_allowed(path, policy):
  """Return an error dict if the path is outside the allowed roots."""
  roots = policy.allowed_roots
  if roots is None:
    return None
  real = os.path.realpath(path)
  for root in roots:
    if real == root or real.startswith(root.rstrip(os.sep) + os.sep):
      return None
  return {
    "error": (
      f"Path is outside the allowed directories: {path}. "
      f"Allowed: {', '.join(roots)}"
    )
  }

def log_action(policy, record):
  """Append one JSON line describing an action. Never raises."""
  path = policy.log_path
  if not path:
    return
  record = {"time": time.strftime("%Y-%m-%dT%H:%M:%S%z"), **record}
  try:
    directory = os.path.dirname(path)
    if directory:
      os.makedirs(directory, exist_ok=True)
    if (
      os.path.exists(path)
      and os.path.getsize(path) > MAX_LOG_BYTES
    ):
      os.replace(path, path + ".1")
    fd = os.open(
      path, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600
    )
    with os.fdopen(fd, "a", encoding="utf-8") as file:
      file.write(json.dumps(record, ensure_ascii=False) + "\n")
  except OSError:
    pass

def _log_args(name, arguments):
  shown = {}
  for key, value in arguments.items():
    if name == "write_file" and key == "content":
      shown["content_chars"] = len(value) if isinstance(value, str) else 0
    elif isinstance(value, str):
      shown[key] = value[:1000]
    else:
      shown[key] = value
  return shown

def _log_result(result):
  summary = {}
  for key, value in result.items():
    if key in ("stdout", "stderr", "content"):
      summary[key + "_chars"] = (
        len(value) if isinstance(value, str) else 0
      )
    elif key == "matches":
      summary["match_count"] = len(value)
    elif isinstance(value, str):
      summary[key] = value[:300]
    else:
      summary[key] = value
  return summary

def _confirm_action(preview, policy, ui, prompt_key="run_prompt"):
  """Show a preview and ask the user. Returns True if allowed."""
  if policy.auto_approve:
    policy.last_decision = "auto"
    return True
  if not (sys.stdin.isatty() and sys.stderr.isatty()):
    print(COLORS.error(ui["run_non_tty"]), file=sys.stderr)
    policy.last_decision = "denied-no-tty"
    return False
  print(f"\n{COLORS.value(preview)}", file=sys.stderr)
  try:
    reply = input(
      f"{COLORS.spinner(ui[prompt_key])} "
    ).strip().lower()
  except (EOFError, KeyboardInterrupt):
    print(file=sys.stderr)
    policy.last_decision = "denied"
    return False
  if reply in ("a", "always"):
    policy.auto_approve = True
    policy.last_decision = "approved-always"
    return True
  approved = reply in ("y", "yes")
  policy.last_decision = "approved" if approved else "denied"
  return approved

def confirm_action(preview, policy, ui, prompt_key="run_prompt"):
  """Pause the spinner while the user is being asked."""
  spinner = ACTIVE_SPINNER["s"]
  paused = bool(
    spinner is not None
    and spinner.thread is not None
    and not policy.auto_approve
  )
  if paused:
    spinner.stop()
  try:
    return _confirm_action(preview, policy, ui, prompt_key)
  finally:
    if paused:
      spinner.start()

def confirm_command(command, policy, ui):
  return confirm_action("$ " + command, policy, ui, "run_prompt")

def _resolve(path):
  return os.path.abspath(os.path.expanduser(str(path or "")))

def _read_text(path):
  with open(path, "r", encoding="utf-8", newline="") as file:
    return file.read()

def _write_text(path, text):
  with open(path, "w", encoding="utf-8", newline="") as file:
    file.write(text)

def _backup(path):
  if os.path.exists(path):
    shutil.copy2(path, path + ".bak")

def _diff_preview(old, new, path, max_lines=60):
  diff = list(difflib.unified_diff(
    old.splitlines(),
    new.splitlines(),
    fromfile=path,
    tofile=path + " (new)",
    lineterm="",
    n=2,
  ))
  if len(diff) > max_lines:
    extra = len(diff) - max_lines
    diff = diff[:max_lines] + [f"... ({extra} more diff lines)"]
  return "\n".join(diff) or "(no textual change)"

def _syntax_check(path, text):
  """Quick parse check for a few file types; None if not applicable."""
  ext = os.path.splitext(path)[1].lower()
  try:
    if ext == ".py":
      compile(text, path, "exec")
    elif ext == ".json":
      json.loads(text)
    else:
      return None
  except (SyntaxError, ValueError) as exc:
    return f"{type(exc).__name__}: {exc}"
  return "ok"

def _verified_result(path, expected, result):
  """Read the file back; restore the backup if it differs."""
  detail = ""
  try:
    ok = _read_text(path) == expected
    if not ok:
      detail = "content differs"
  except (OSError, UnicodeDecodeError) as exc:
    ok = False
    detail = str(exc)
  if not ok:
    restored = False
    backup = path + ".bak"
    if result.get("backup") and os.path.exists(backup):
      try:
        shutil.copy2(backup, path)
        restored = True
      except OSError:
        pass
    return {
      "error": (
        f"Write verification failed ({detail}); "
        + (
          "the original was restored from the backup."
          if restored
          else "the file may be in a bad state."
        )
      )
    }
  result["verified"] = True
  check = _syntax_check(path, expected)
  if check:
    result["syntax_check"] = check
  return result

def tool_read_file(args, policy):
  path = _resolve(args.get("path"))
  denied = check_path_allowed(path, policy)
  if denied:
    return denied
  if not os.path.isfile(path):
    return {"error": f"Not a file: {path}"}
  try:
    offset = max(1, int(args.get("offset") or 1))
    limit = min(MAX_READ_LINES, max(1, int(args.get("limit") or 200)))
  except (TypeError, ValueError):
    return {"error": "offset and limit must be integers."}
  lines = []
  more = False
  try:
    with open(path, "r", encoding="utf-8", errors="replace") as file:
      for number, line in enumerate(file, 1):
        if number < offset:
          continue
        if len(lines) >= limit:
          more = True
          break
        text = line.rstrip("\n")
        if len(text) > 2000:
          text = text[:2000] + "...[line cut]"
        lines.append(f"{number}\t{text}")
  except OSError as exc:
    return {"error": f"Cannot read file: {exc}"}
  result = {
    "path": path,
    "first_line": offset,
    "content": _clip("\n".join(lines)),
  }
  if not lines:
    result["note"] = "No lines at this offset."
  elif more:
    result["note"] = (
      f"More lines follow; call again with offset={offset + limit}."
    )
  return result

def tool_edit_file(args, policy, ui):
  path = _resolve(args.get("path"))
  old = args.get("old_text")
  new = args.get("new_text")
  denied = check_path_allowed(path, policy)
  if denied:
    return denied
  if not isinstance(old, str) or not isinstance(new, str) or not old:
    return {"error": "old_text and new_text must be strings; old_text cannot be empty."}
  if not os.path.isfile(path):
    return {"error": f"Not a file: {path}"}
  try:
    text = _read_text(path)
  except (OSError, UnicodeDecodeError) as exc:
    return {"error": f"Cannot read file: {exc}"}
  count = text.count(old)
  if count == 0:
    return {
      "error": (
        "old_text was not found. Read the file again and copy "
        "the text exactly, including whitespace."
      )
    }
  replace_all = bool(args.get("replace_all"))
  if count > 1 and not replace_all:
    return {
      "error": (
        f"old_text appears {count} times. Add surrounding context "
        "to make it unique, or set replace_all to true."
      )
    }
  updated = (
    text.replace(old, new)
    if replace_all
    else text.replace(old, new, 1)
  )
  preview = f"Edit {path}\n" + _diff_preview(text, updated, path)
  if not confirm_action(preview, policy, ui, "apply_prompt"):
    print(COLORS.error(ui["run_denied"]), file=sys.stderr)
    return {"error": "The user denied this change."}
  try:
    _backup(path)
    _write_text(path, updated)
  except OSError as exc:
    return {"error": f"Cannot write file: {exc}"}
  return _verified_result(path, updated, {
    "path": path,
    "replacements": count if replace_all else 1,
    "backup": path + ".bak",
  })

def tool_write_file(args, policy, ui):
  path = _resolve(args.get("path"))
  content = args.get("content")
  if not args.get("path") or not isinstance(content, str):
    return {"error": "path and content (string) are required."}
  denied = check_path_allowed(path, policy)
  if denied:
    return denied
  if os.path.isdir(path):
    return {"error": f"Path is a directory: {path}"}
  existed = os.path.isfile(path)
  if existed:
    try:
      old = _read_text(path)
    except (OSError, UnicodeDecodeError):
      old = ""
    preview = f"Overwrite {path}\n" + _diff_preview(old, content, path)
  else:
    head = "\n".join(content.splitlines()[:15])
    preview = (
      f"Create {path} ({len(content)} characters)\n{head}"
    )
  if not confirm_action(preview, policy, ui, "apply_prompt"):
    print(COLORS.error(ui["run_denied"]), file=sys.stderr)
    return {"error": "The user denied this change."}
  try:
    directory = os.path.dirname(path)
    if directory:
      os.makedirs(directory, exist_ok=True)
    _backup(path)
    _write_text(path, content)
  except OSError as exc:
    return {"error": f"Cannot write file: {exc}"}
  result = {"path": path, "characters": len(content)}
  if existed:
    result["backup"] = path + ".bak"
  return _verified_result(path, content, result)

def tool_search_files(args, policy):
  pattern = str(args.get("pattern") or "")
  if not pattern:
    return {"error": "pattern is required."}
  root = _resolve(args.get("path") or ".")
  denied = check_path_allowed(root, policy)
  if denied:
    return denied
  if not os.path.isdir(root):
    return {"error": f"Not a directory: {root}"}
  mode = args.get("mode") or "content"
  if mode not in ("name", "content"):
    return {"error": "mode must be 'name' or 'content'."}
  include = args.get("include")
  skip_bak = ".bak" not in pattern.lower()
  try:
    max_results = min(
      MAX_SEARCH_RESULTS,
      max(1, int(args.get("max_results") or 50)),
    )
  except (TypeError, ValueError):
    return {"error": "max_results must be an integer."}
  regex = None
  if mode == "content":
    try:
      regex = re.compile(pattern, re.IGNORECASE)
    except re.error as exc:
      return {"error": f"Invalid regex: {exc}"}
  matches = []
  for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
    for name in sorted(filenames):
      full = os.path.join(dirpath, name)
      if name.lower().endswith(".bak") and (
        mode == "content" or skip_bak
      ):
        continue
      if mode == "name":
        if fnmatch.fnmatch(name.lower(), pattern.lower()):
          matches.append(full)
      else:
        if include and not fnmatch.fnmatch(name, include):
          continue
        try:
          if check_path_allowed(full, policy):
            continue
          if os.path.getsize(full) > 2000000:
            continue
          with open(full, "rb") as probe:
            if b"\0" in probe.read(1024):
              continue
          with open(
            full, "r", encoding="utf-8", errors="replace"
          ) as file:
            for number, line in enumerate(file, 1):
              if regex.search(line):
                matches.append(
                  f"{full}:{number}: {line.strip()[:160]}"
                )
                if len(matches) >= max_results:
                  break
        except OSError:
          continue
      if len(matches) >= max_results:
        break
    if len(matches) >= max_results:
      break
  return {
    "mode": mode,
    "root": root,
    "matches": matches,
    "truncated": len(matches) >= max_results,
  }

def run_tool_call(step, policy, ui):
  """Execute one function_call step; return a function_result step."""
  name = step.get("name", "")
  arguments = step.get("arguments") or {}
  if isinstance(arguments, str):
    try:
      arguments = json.loads(arguments)
    except json.JSONDecodeError:
      arguments = {}
  if not isinstance(arguments, dict):
    arguments = {}
  policy.last_decision = "n/a"
  if name == "run_command" and policy.enabled:
    command = str(arguments.get("command", "")).strip()
    reason = check_command_guard(command) if command else None
    if not command:
      result = {"error": "Empty command."}
    elif reason:
      policy.last_decision = "blocked"
      print(
        COLORS.error(f"{ui['blocked']}: {reason}"),
        file=sys.stderr,
      )
      result = {
        "error": (
          f"Blocked by the safety filter ({reason}). The command "
          "was not run; do not retry it in another form."
        )
      }
    elif confirm_command(command, policy, ui):
      result = execute_command(command, policy.timeout)
    else:
      result = {"error": "The user denied this command."}
      print(COLORS.error(ui["run_denied"]), file=sys.stderr)
  elif name in FILE_TOOL_NAMES and policy.files_enabled:
    if name == "read_file":
      result = tool_read_file(arguments, policy)
    elif name == "edit_file":
      result = tool_edit_file(arguments, policy, ui)
    elif name == "write_file":
      result = tool_write_file(arguments, policy, ui)
    else:
      result = tool_search_files(arguments, policy)
  else:
    result = {"error": f"Unknown or disabled tool: {name}"}
  log_action(policy, {
    "tool": name,
    "args": _log_args(name, arguments),
    "decision": policy.last_decision,
    "result": _log_result(result),
  })
  return {
    "type": "function_result",
    "name": name,
    "call_id": step.get("id"),
    "result": [
      {
        "type": "text",
        "text": json.dumps(result, ensure_ascii=False),
      }
    ],
  }

class StreamPrinter:
  """Prints streamed text as it arrives."""

  def __init__(self, header=""):
    self.header = header
    self.started = False
    self.ansi = AnsiStream()

  def __call__(self, piece):
    text = self.ansi.feed(piece)
    if not self.started:
      self.started = True
      if self.header:
        sys.stdout.write(self.header)
        PROGRESS["col0"] = False
        PROGRESS["pending"] = False
    if PROGRESS["pending"]:
      # Leading newlines would turn the status line into a permanent line
      text = text.lstrip("\r\n")
    sys.stdout.write(text)
    if text:
      PROGRESS["col0"] = text.endswith("\n")
      PROGRESS["pending"] = False
    sys.stdout.flush()

  def flush_end(self):
    """Write any held-back text and reset colors so they do not leak into later output."""
    if self.started:
      text = self.ansi.finish()
      sys.stdout.write(text)
      if text:
        PROGRESS["col0"] = text.endswith("\n")
      sys.stdout.flush()

  def finish(self, extra_newlines=1):
    if self.started:
      self.flush_end()
      sys.stdout.write("\n" * extra_newlines)
      sys.stdout.flush()

def chat_turn(
  api_key,
  model,
  history,
  instruction,
  language,
  thinking,
  timeout,
  memory_summary=None,
  policy=None,
  ui=None,
  spinner_text=None,
  stream_out=None,
):
  """One user turn, looping while the model asks to run commands.

  Returns (final_answer, all_generated_steps).
  """
  policy = policy or ShellPolicy()
  generated = []
  working = list(history)
  for _ in range(policy.max_steps + 1):
    spinner = Spinner(spinner_text) if spinner_text else None
    ACTIVE_SPINNER["s"] = spinner
    if spinner:
      spinner.start()
      if spinner.thread is None:
        emit_progress(spinner_text)

    def on_text(piece, spinner=spinner):
      if spinner:
        spinner.stop()
      stream_out(piece)

    def status(text, spinner=spinner, base=spinner_text):
      if spinner:
        spinner.message = text or base
        if spinner.thread is None:
          emit_progress(text or base)

    animating = bool(spinner and spinner.thread)
    try:
      answer, steps = call_gemini(
        api_key=api_key,
        model=model,
        history=working,
        instruction=instruction,
        language=language,
        thinking=thinking,
        timeout=timeout,
        memory_summary=memory_summary,
        shell_enabled=policy.enabled,
        files_enabled=policy.files_enabled,
        stream_callback=on_text if stream_out else None,
        status_callback=status if (animating or progress_enabled()) else None,
      )
      generated.extend(steps)
      calls = [step for step in steps if isinstance(step, dict) and step.get("type") == "function_call"]
      if not calls:
        return answer, generated
      results = []
      for step in calls:
        if spinner:
          tool_name = str(step.get("name") or "tool")
          label = ui["tool_running"].format(tool=tool_name) if ui else f"AI is running: {tool_name}"
          detail = describe_tool_call(step)
          if detail:
            label = f"{label} · {detail}"
          spinner.message = _fit(label)
          if spinner.thread is None:
            if sys.stdout.isatty():
              spinner.start()
            else:
              emit_progress(label)
        results.append(run_tool_call(step, policy, ui))
      generated.extend(results)
      working = working + steps + results
    finally:
      if spinner:
        spinner.stop()
      ACTIVE_SPINNER["s"] = None
  raise RuntimeError(ui["tool_limit"] if ui else "Too many tool steps.")

def get_memory_path(args):
  configured_path = (
    args.memory_file
    or os.environ.get("GEMINI_CLI_MEMORY_FILE")
  )
  if configured_path:
    return os.path.abspath(
      os.path.expanduser(configured_path)
    )
  new_path = os.path.join(DATA_DIR, "memory.sqlite3")
  old_path = os.path.join(
    tempfile.gettempdir(),
    "gemini_cli_memory.sqlite3",
  )
  if not os.path.exists(new_path) and os.path.exists(old_path):
    try:
      os.makedirs(DATA_DIR, mode=0o700, exist_ok=True)
      for suffix in ("", "-wal", "-shm"):
        if os.path.exists(old_path + suffix):
          shutil.move(old_path + suffix, new_path + suffix)
    except OSError:
      return old_path
  return new_path

def _connect_memory(path):
  directory = os.path.dirname(path)
  if directory:
    os.makedirs(directory, mode=0o700, exist_ok=True)
  connection = sqlite3.connect(path, timeout=30)
  connection.execute("PRAGMA busy_timeout = 30000")
  connection.execute(
    "CREATE TABLE IF NOT EXISTS conversations ("
    "id INTEGER PRIMARY KEY AUTOINCREMENT, "
    "user_message TEXT NOT NULL, "
    "assistant_response TEXT NOT NULL)"
  )
  connection.execute(
    "CREATE TABLE IF NOT EXISTS memory_state ("
    "key TEXT PRIMARY KEY, "
    "value TEXT NOT NULL)"
  )
  _migrate_legacy_history(connection)
  connection.commit()
  return connection

def _migrate_legacy_history(connection):
  """Convert the old verbose step table into compact user/answer pairs."""
  exists = connection.execute(
    "SELECT 1 FROM sqlite_master "
    "WHERE type='table' AND name='history'"
  ).fetchone()
  if not exists:
    return
  old_rows = connection.execute(
    "SELECT item_json FROM history ORDER BY id"
  ).fetchall()
  pending_question = None
  migrated = []
  for (raw,) in old_rows:
    try:
      item = json.loads(raw)
    except (TypeError, json.JSONDecodeError):
      continue
    if not isinstance(item, dict):
      continue
    if item.get("type") == "user_input":
      if pending_question is not None:
        migrated.append((pending_question, ""))
      pending_question = str(item.get("content", ""))
    elif (
      pending_question is not None
      and item.get("type") == "model_output"
    ):
      pieces = [
        str(part.get("text", ""))
        for part in item.get("content", [])
        if (
          isinstance(part, dict)
          and part.get("type") == "text"
        )
      ]
      if pieces:
        migrated.append(
          (pending_question, "\n".join(pieces))
        )
        pending_question = None
  if pending_question is not None:
    migrated.append((pending_question, ""))
  if migrated:
    connection.executemany(
      "INSERT INTO conversations "
      "(user_message, assistant_response) VALUES (?, ?)",
      migrated,
    )
  connection.execute("DROP TABLE history")
  connection.execute(
    "INSERT INTO memory_state (key, value) "
    "VALUES ('last_summarized_id', '0') "
    "ON CONFLICT(key) DO UPDATE SET value='0'"
  )

def load_memory(path):
  try:
    with closing(_connect_memory(path)) as connection:
      rows = connection.execute(
        "SELECT id, user_message, assistant_response "
        "FROM conversations ORDER BY id"
      ).fetchall()
      state = dict(
        connection.execute(
          "SELECT key, value FROM memory_state"
        ).fetchall()
      )
    try:
      os.chmod(path, 0o600)
    except OSError:
      pass
  except (
    OSError,
    sqlite3.Error,
    json.JSONDecodeError,
  ) as exc:
    raise RuntimeError(
      f"Cannot read SQLite memory database '{path}': {exc}"
    ) from exc
  return (
    rows,
    state.get("summary", ""),
    int(state.get("last_summarized_id", "0")),
  )

def save_memory(path, user_message, assistant_response):
  """Store only the exact user message and final assistant answer."""
  directory = os.path.dirname(path)
  try:
    if directory:
      os.makedirs(directory, exist_ok=True)
    with closing(_connect_memory(path)) as connection:
      with connection:
        connection.execute(
          "INSERT INTO conversations "
          "(user_message, assistant_response) VALUES (?, ?)",
          (user_message, assistant_response),
        )
    try:
      os.chmod(path, 0o600)
    except OSError:
      pass
  except (
    OSError,
    sqlite3.Error,
    TypeError,
    ValueError,
  ) as exc:
    raise RuntimeError(
      f"Cannot save SQLite memory database '{path}': {exc}"
    ) from exc

def save_memory_summary(path, summary, last_id):
  try:
    with closing(_connect_memory(path)) as connection:
      with connection:
        connection.executemany(
          "INSERT INTO memory_state (key, value) "
          "VALUES (?, ?) "
          "ON CONFLICT(key) "
          "DO UPDATE SET value=excluded.value",
          [
            ("summary", summary),
            ("last_summarized_id", str(last_id)),
          ],
        )
    try:
      os.chmod(path, 0o600)
    except OSError:
      pass
  except (OSError, sqlite3.Error) as exc:
    raise RuntimeError(
      f"Cannot update SQLite memory summary: {exc}"
    ) from exc

def _turn_size(row):
  return len(row[1]) + len(row[2])

def _turn_as_api_input(row):
  return [
    {
      "type": "user_input",
      "content": row[1],
    },
    {
      "type": "model_output",
      "content": [
        {"type": "text", "text": row[2]}
      ],
    },
  ]

def prepare_memory_context(
  path,
  rows,
  summary,
  last_summarized_id,
  api_key,
  model,
  language,
  timeout,
):
  """Summarize old turns incrementally; never delete source history."""
  pending = [
    row for row in rows
    if row[0] > last_summarized_id
  ]
  pending_size = sum(_turn_size(row) for row in pending)
  if pending_size <= RECENT_CONTEXT_CHARS:
    recent_rows = [
      row for row in rows
      if row[0] > last_summarized_id
    ]
    return (
      summary,
      [
        item
        for row in recent_rows
        for item in _turn_as_api_input(row)
      ],
    )
  target_old_size = max(
    0,
    pending_size - RECENT_CONTEXT_CHARS,
  )
  selected = []
  selected_size = 0
  for row in pending:
    if selected_size >= target_old_size:
      break
    selected.append(row)
    selected_size += _turn_size(row)
  chunk = []
  chunk_size = 0
  for row in selected:
    size = _turn_size(row)
    if chunk and chunk_size + size > SUMMARY_CHUNK_CHARS:
      summary = _summarize_memory_chunk(
        chunk,
        summary,
        api_key,
        model,
        language,
        timeout,
      )
      save_memory_summary(
        path,
        summary,
        chunk[-1][0],
      )
      chunk = []
      chunk_size = 0
    chunk.append(row)
    chunk_size += size
  if chunk:
    summary = _summarize_memory_chunk(
      chunk,
      summary,
      api_key,
      model,
      language,
      timeout,
    )
    save_memory_summary(
      path,
      summary,
      chunk[-1][0],
    )
  _, summary, last_summarized_id = load_memory(path)
  recent_rows = [
    row for row in rows
    if row[0] > last_summarized_id
  ]
  recent = [
    item
    for row in recent_rows
    for item in _turn_as_api_input(row)
  ]
  return summary, recent

def _summarize_memory_chunk(
  chunk,
  previous_summary,
  api_key,
  model,
  language,
  timeout,
):
  prompt = (
    f"Update the persistent conversation memory in {language}. "
    "Preserve important facts, user preferences, ongoing project "
    "decisions, constraints, names, paths, and unresolved tasks. "
    "Remove repetition, do not invent facts, and distinguish "
    "confirmed decisions from ideas. "
    f"Keep the complete summary at or below {MAX_SUMMARY_CHARS} "
    "characters. Prioritize durable facts and active projects; "
    "discard obsolete details. "
    "Return only the updated concise memory summary."
  )
  history = []
  if previous_summary:
    history.append({
      "type": "user_input",
      "content": "Previous memory summary:\n" + previous_summary,
    })
  for row in chunk:
    history.extend(_turn_as_api_input(row))
  answer, _ = call_gemini(
    api_key=api_key,
    model=model,
    history=history,
    instruction=prompt,
    language=language,
    thinking="low",
    timeout=timeout,
  )
  if len(answer) > MAX_SUMMARY_CHARS:
    compact_prompt = (
      f"Compress this persistent memory summary in {language} "
      f"to no more than {MAX_SUMMARY_CHARS} characters. "
      "Preserve confirmed facts, user preferences, active "
      "project decisions, important paths, constraints, "
      "and unresolved tasks. Remove repetition and obsolete "
      "details. Do not invent facts. "
      "Return only the compact summary.\n\n"
      + answer
    )
    answer, _ = call_gemini(
      api_key=api_key,
      model=model,
      history=[
        {
          "type": "user_input",
          "content": compact_prompt,
        }
      ],
      instruction=compact_prompt,
      language=language,
      thinking="low",
      timeout=timeout,
    )
  if len(answer) > MAX_SUMMARY_CHARS:
    raise RuntimeError(
      "Memory summary still exceeds the configured character limit."
    )
  return answer

def clear_memory(path):
  try:
    for suffix in ("", "-wal", "-shm"):
      target = path + suffix
      if os.path.exists(target):
        os.remove(target)
  except OSError as exc:
    raise RuntimeError(
      f"Cannot clear memory file '{path}': {exc}"
    ) from exc

def show_help(cli_language):
  ui = UI_TEXT[cli_language]
  commands = [
    ("/help", ui["help"]),
    ("/thinking auto|low|medium|high", ui["thinking_help"]),
    ("/lang <language>", ui["lang_help"]),
    ("/instruction <text>", ui["instruction_help"]),
    ("/shell on|off|auto", ui["shell_help"]),
    ("/files on|off", ui["files_help"]),
    ("/all on|off", ui["all_help"]),
    ("/stream on|off", ui["stream_help"]),
    ("/clear", ui["clear_help"]),
    ("/exit", ui["exit_help"]),
  ]
  print("\n" + COLORS.title(ui["help_title"]))
  for command, description in commands:
    print(
      f"  {COLORS.help(command)}\n"
      f"      {description}\n"
    )

def trim_history(history, limit=INTERACTIVE_HISTORY_CHARS):
  """Drop the oldest whole turns so the RAM history stays bounded."""
  turns, current = [], []
  for item in history:
    if (
      isinstance(item, dict)
      and item.get("type") == "user_input"
      and current
    ):
      turns.append(current)
      current = []
    current.append(item)
  if current:
    turns.append(current)
  sizes = [
    len(json.dumps(t, ensure_ascii=False, default=str))
    for t in turns
  ]
  while len(turns) > 1 and sum(sizes) > limit:
    turns.pop(0)
    sizes.pop(0)
  return [item for t in turns for item in t]

def run():
  global ALLOW_MARKDOWN
  args = parse_args()
  PROGRESS["enabled"] = bool(args.progress) or (
    os.environ.get("GEMINI_CLI_PROGRESS", "").strip().lower() in ("1", "true", "yes", "on")
  )
  ALLOW_MARKDOWN = bool(args.markdown) or (
    os.environ.get("GEMINI_CLI_MARKDOWN", "").strip().lower() in ("1", "true", "yes", "on")
  )
  question = (
    args.prompt
    if args.prompt is not None
    else args.question
  )
  one_shot = question is not None
  language = normalize_language(args.lang)
  cli_language = get_cli_language(language)
  ui = UI_TEXT[cli_language]
  ACTIVE_UI["ui"] = ui
  RATE_LIMITER.rpm = args.rpm
  RETRY["max"] = args.max_retries
  memory_path = get_memory_path(args)
  if args.clear_memory:
    try:
      clear_memory(memory_path)
    except RuntimeError as exc:
      print(
        COLORS.error(f"{ui['memory_error']}: {exc}"),
        file=sys.stderr,
      )
      return 1
    return 0
  if args.timeout <= 0:
    print(
      COLORS.error(ui["timeout_error"]),
      file=sys.stderr,
    )
    return 2
  if args.list_providers:
    list_providers()
    return 0
  if args.list_websites:
    list_providers(show_websites=True)
    return 0
  ACTIVE_PROVIDER["name"] = args.provider
  ACTIVE_PROVIDER["base_url"] = args.base_url
  provider_env = PROVIDERS[args.provider]["env"]
  api_key = args.api_key or os.environ.get(provider_env)
  if args.list_models and args.provider == "openrouter" and not api_key:
    api_key = ""
  elif not api_key:
    print(COLORS.error(f"Missing {provider_env}."), file=sys.stderr)
    return 2
  if args.list_models:
    try:
      models = fetch_provider_models(args.provider, api_key, args.base_url, args.timeout)
      print_models(args.provider, models, args.model)
      return 0
    except (RuntimeError, ValueError) as exc:
      print(COLORS.error(f"Model listing failed: {exc}"), file=sys.stderr)
      return 1

  if args.check_api_key:
    try:
      if args.provider == "gemini":
        model_info = check_api_key_model(api_key=api_key, model=args.model, timeout=args.timeout)
        display_name = model_info.get("displayName", model_info["name"])
      elif args.provider == "openrouter":
        check_request = urllib.request.Request(args.base_url + "/key", headers={"Authorization": "Bearer " + api_key}, method="GET")
        with urllib.request.urlopen(check_request, timeout=args.timeout) as response:
          json.loads(response.read().decode("utf-8"))
        display_name = args.model + " (model access checked on first request)"
      else:
        available = fetch_provider_models(args.provider, api_key, args.base_url, args.timeout)
        if not any(item["id"] == args.model for item in available):
          raise RuntimeError(f"Model '{args.model}' was not found in the provider model list.")
        display_name = args.model
      print(COLORS.value(ui["api_valid"]))
      print(COLORS.value(ui["model_accessible"]))
      print(f"{ui['model_name']}: {display_name}")
      return 0
    except (RuntimeError, urllib.error.HTTPError, urllib.error.URLError, ValueError) as exc:
      print(COLORS.error(f"{ui['api_check_failed']}: {exc}"), file=sys.stderr)
      return 1
  try:
    instruction = load_instruction(args)
  except (OSError, UnicodeError, ValueError) as exc:
    print(
      COLORS.error(f"{ui['instruction_error']}: {exc}"),
      file=sys.stderr,
    )
    return 2
  model = args.model
  thinking = args.thinking
  policy = ShellPolicy(
    enabled=args.shell,
    auto_approve=args.yes,
    timeout=args.shell_timeout,
    max_steps=args.max_tool_steps,
    files_enabled=args.files,
    allowed_roots=(
      None
      if args.any_path
      else [os.path.realpath(os.getcwd())]
      + [
        os.path.realpath(os.path.expanduser(item))
        for item in args.allow_path
      ]
    ),
    log_path=(
      None
      if args.no_log
      else os.path.abspath(
        os.path.expanduser(
          args.log_file
          or os.environ.get("GEMINI_CLI_LOG_FILE")
          or DEFAULT_LOG_FILE
        )
      )
    ),
  )

  def shell_status():
    if not policy.enabled:
      return "off"
    return "auto" if policy.auto_approve else "on (ask)"

  def files_status():
    return "on" if policy.files_enabled else "off"

  streaming = args.stream

  def stream_status():
    return "on" if streaming else "off"

  if one_shot:
    question = question.strip()
    if not question:
      print(
        COLORS.error(ui["question_empty"]),
        file=sys.stderr,
      )
      return 2
    printer = StreamPrinter() if streaming else None
    emit_progress(ui["spinner"])
    try:
      memory_summary = None
      if args.no_memory:
        request_history = [{"type": "user_input", "content": question}]
      else:
        memory_rows, memory_summary, last_summarized_id = load_memory(memory_path)
        memory_summary, recent_history = prepare_memory_context(
          path=memory_path,
          rows=memory_rows,
          summary=memory_summary,
          last_summarized_id=last_summarized_id,
          api_key=api_key,
          model=model,
          language=language,
          timeout=args.timeout,
        )
        request_history = recent_history + [{"type": "user_input", "content": question}]
      answer, _ = chat_turn(
        api_key=api_key,
        model=model,
        history=request_history,
        instruction=instruction,
        language=language,
        thinking=thinking,
        timeout=args.timeout,
        memory_summary=memory_summary,
        policy=policy,
        ui=ui,
        spinner_text=ui["spinner"],
        stream_out=printer,
      )
      save_memory(
        memory_path,
        question,
        answer,
      )
    except (RuntimeError, ValueError) as exc:
      _clear_progress()
      print(
        f"\n{ui['error']}: {exc}",
        file=sys.stderr,
      )
      return 1
    if printer is not None and printer.started:
      printer.flush_end()
      if not answer.endswith("\n"):
        printer.finish()
    else:
      answer_out = normalize_ansi(answer, close=True)
      if PROGRESS["pending"]:
        answer_out = answer_out.lstrip("\r\n")
      PROGRESS["pending"] = False
      sys.stdout.write(answer_out)
      if not answer_out.endswith("\n"):
        sys.stdout.write("\n")
    sys.stdout.flush()
    if not args.no_memory:
      try:
        rows, summary, checkpoint = load_memory(memory_path)
        prepare_memory_context(
          path=memory_path,
          rows=rows,
          summary=summary,
          last_summarized_id=checkpoint,
          api_key=api_key,
          model=model,
          language=language,
          timeout=args.timeout,
        )
      except (RuntimeError, ValueError) as exc:
        print(
          f"{ui['warning']}: {exc}",
          file=sys.stderr,
        )
    return 0

  if sys.stdout.isatty():
    os.system("clear")
  print(COLORS.title(ui["title"]))
  print()
  provider_label = PROVIDERS[args.provider]["label"]
  status_rows = (
    ("Provider", provider_label),
    (ui["mode_label"], ui["mode"]),
    (ui["model_label"], model),
    (ui["thinking"], thinking),
    (ui["language"], language),
    (ui["shell_label"], shell_status()),
    (ui["files_label"], files_status()),
    (ui["stream_label"], stream_status()),
    (
      ui["log_label"],
      policy.log_path
      if (policy.log_path and (policy.enabled or policy.files_enabled))
      else "off",
    ),
  )
  label_width = max(
    len(label)
    for label, _ in status_rows
  )
  for label, value in status_rows:
    print(
      f"{COLORS.label(label.ljust(label_width) + ' :')} "
      f"{COLORS.value(value)}"
    )
  print()
  print(ui["help_hint"])
  print()
  history = []
  while True:
    try:
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      ACTIVE_UI["ui"] = ui
      message = input(
        f"{COLORS.ai(ui['prompt'])} ❯ "
      ).strip()
    except (EOFError, KeyboardInterrupt):
      cli_language = get_cli_language(language)
      print("\n" + UI_TEXT[cli_language]["goodbye"])
      break
    if not message:
      continue
    command = message.lower()
    if command in ("/exit", "/quit", "/q"):
      cli_language = get_cli_language(language)
      print(UI_TEXT[cli_language]["goodbye"])
      break
    if command == "/help":
      show_help(get_cli_language(language))
      continue
    if command == "/clear":
      history = []
      print(COLORS.value(UI_TEXT[get_cli_language(language)]["clear_done"]))
      continue
    if command.startswith("/thinking"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split()
      if (
        len(parts) != 2
        or parts[1].lower() not in THINKING_LEVELS
      ):
        print(COLORS.error(ui["thinking_usage"]))
        continue
      thinking = parts[1].lower()
      print(
        COLORS.value(
          ui["thinking_prefix"].format(value=thinking)
        )
      )
      continue
    if command.startswith("/lang"):
      parts = message.split(maxsplit=1)
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      if len(parts) != 2 or not parts[1].strip():
        print(COLORS.error(ui["language_usage"]))
        continue
      language = normalize_language(parts[1])
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      print(
        COLORS.value(
          ui["language_changed"].format(
            language=language
          )
        )
      )
      continue
    if command.startswith("/all"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split()
      choice = parts[1].lower() if len(parts) == 2 else ""
      if choice not in ("on", "off"):
        print(COLORS.error(ui["all_usage"]))
        continue
      flag = choice == "on"
      policy.enabled = flag
      policy.files_enabled = flag
      policy.auto_approve = flag
      print(COLORS.value(ui["all_state"].format(value=choice)))
      continue
    if command.startswith("/stream"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split()
      choice = parts[1].lower() if len(parts) == 2 else ""
      if choice not in ("on", "off"):
        print(COLORS.error(ui["stream_usage"]))
        continue
      streaming = choice == "on"
      print(
        COLORS.value(
          ui["stream_state"].format(value=stream_status())
        )
      )
      continue
    if command.startswith("/files"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split()
      choice = parts[1].lower() if len(parts) == 2 else ""
      if choice not in ("on", "off"):
        print(COLORS.error(ui["files_usage"]))
        continue
      policy.files_enabled = choice == "on"
      print(
        COLORS.value(
          ui["files_state"].format(value=files_status())
        )
      )
      continue
    if command.startswith("/shell"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split()
      choice = parts[1].lower() if len(parts) == 2 else ""
      if choice == "on":
        policy.enabled = True
        policy.auto_approve = False
      elif choice == "auto":
        policy.enabled = True
        policy.auto_approve = True
      elif choice == "off":
        policy.enabled = False
        policy.auto_approve = False
      else:
        print(COLORS.error(ui["shell_usage"]))
        continue
      print(
        COLORS.value(
          ui["shell_state"].format(value=shell_status())
        )
      )
      continue
    if command.startswith("/instruction"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      parts = message.split(maxsplit=1)
      if len(parts) != 2 or not parts[1].strip():
        print(COLORS.error(ui["instruction_usage"]))
        continue
      instruction = parts[1].strip()
      print(
        COLORS.value(ui["instruction_changed"])
      )
      continue
    if re.match(r"^/[A-Za-z]*(\s|$)", message):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      print(
        COLORS.error(ui["invalid_command"])
      )
      continue
    cli_language = get_cli_language(language)
    ui = UI_TEXT[cli_language]
    print()
    reply_label = (
      "" if args.no_label else f"{COLORS.gemini(provider_label)} ❯ "
    )
    printer = (
      StreamPrinter(reply_label)
      if streaming
      else None
    )
    try:
      request_history = history + [
        {
          "type": "user_input",
          "content": message,
        }
      ]
      answer, generated_steps = chat_turn(
        api_key=api_key,
        model=model,
        history=request_history,
        instruction=instruction,
        language=language,
        thinking=thinking,
        timeout=args.timeout,
        policy=policy,
        ui=ui,
        spinner_text=ui["spinner"],
        stream_out=printer,
      )
      history.append({
        "type": "user_input",
        "content": message,
      })
      history.extend(generated_steps)
      history = trim_history(history)
    except KeyboardInterrupt:
      ui = UI_TEXT[get_cli_language(language)]
      print(COLORS.error(f"\n{ui['cancelled']}"))
      print()
      continue
    except (RuntimeError, ValueError) as exc:
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      print(
        COLORS.error(f"\n{ui['error']}: {exc}"),
        file=sys.stderr,
      )
      print()
      continue
    if printer is not None and printer.started:
      printer.finish(2)
      continue
    cli_language = get_cli_language(language)
    answer_out = normalize_ansi(answer, close=True)
    if PROGRESS["pending"]:
      answer_out = answer_out.lstrip("\r\n")
    PROGRESS["pending"] = False
    if args.no_label:
      print(answer_out)
      print()
    elif "\n" in answer_out:
      print(
        f"{COLORS.gemini(provider_label)} ❯ \n\n{answer_out}"
      )
      print()
    else:
      print(
        f"{COLORS.gemini(provider_label)} ❯ {answer_out}"
      )
      print()
  return 0

if __name__ == "__main__":
  raise SystemExit(run())
