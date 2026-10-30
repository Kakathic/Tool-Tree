#!/data/data/com.tool.tree/files/home/termux/bin/python
# -*- coding: utf-8 -*-
"""Gemini CLI
Direct Gemini Interactions REST API client.
Interactive mode keeps context in RAM for the current run.
One-shot mode stores full history and a compact summary in SQLite.
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import urllib.parse
import threading
import itertools
import time
import sqlite3
import tempfile
from contextlib import closing

# ============================================================
# CONFIGURATION
# ============================================================
DEFAULT_MODEL = "gemini-3.5-flash-lite"
DEFAULT_LANGUAGE = "English"
API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
THINKING_LEVELS = ("auto", "low", "medium", "high")
RECENT_CONTEXT_CHARS = 16000
SUMMARY_CHUNK_CHARS = 8000
MAX_SUMMARY_CHARS = 6000
UI_TEXT = {
  "vi": {
    "title": "Gemini CLI",
    "mode": "Bộ nhớ RAM",
    "thinking": "Suy nghĩ",
    "language": "Ngôn ngữ đầu ra",
    "help_hint": "Nhập /help để xem hướng dẫn.",
    "prompt": "Bạn",
    "goodbye": "Hẹn gặp lại lần sau.",
    "spinner": "Gemini đang tạo câu trả lời...",
    "help_title": "Các lệnh:",
    "help": "Hiển thị danh sách lệnh.",
    "thinking_help": "Thay đổi mức suy nghĩ.",
    "lang_help": "Thay đổi ngôn ngữ đầu ra (vi, vi-VN hoặc en).",
    "instruction_help": "Thay đổi system instruction.",
    "exit_help": "Thoát chương trình.",
    "invalid_command": "Lệnh không hợp lệ. Nhập /help để xem trợ giúp.",
    "language_usage": "Cách dùng: /lang vi|vi-VN|en",
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
  },
  "en": {
    "title": "Gemini CLI",
    "mode": "RAM Memory",
    "thinking": "Thinking",
    "language": "Output language",
    "help_hint": "Type /help to see available commands.",
    "prompt": "You",
    "goodbye": "See you next time.",
    "spinner": "Gemini is generating the answer...",
    "help_title": "Commands:",
    "help": "Show available commands.",
    "thinking_help": "Change thinking level.",
    "lang_help": "Change Gemini output language (vi, vi-VN or en).",
    "instruction_help": "Change system instruction.",
    "exit_help": "Exit the program.",
    "invalid_command": "Invalid command. Type /help to see available commands.",
    "language_usage": "Usage: /lang vi|vi-VN|en",
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
  },
}

def normalize_language(value):
  """Only vi and vi-VN select Vietnamese. Everything else selects English."""
  value = str(value or "").strip().lower().replace("_", "-")
  return "Vietnamese" if value in ("vi", "vi-vn") else DEFAULT_LANGUAGE

def get_cli_language(language):
  """Automatically match CLI interface language to Gemini output language."""
  return "vi" if language == "Vietnamese" else "en"

# ============================================================
# TERMINAL COLORS
# ============================================================
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

# ============================================================
# SPINNER
# ============================================================
class Spinner:
  FRAMES = ("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

  def __init__(self, message="Gemini is generating the answer..."):
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

# ============================================================
# ARGUMENTS
# ============================================================
def parse_args():
  parser = argparse.ArgumentParser(
    description=(
      "Gemini CLI - RAM context in chat mode, "
      "persistent context in one-shot mode"
    )
  )
  parser.add_argument("--model", default=DEFAULT_MODEL)
  parser.add_argument(
    "--thinking",
    choices=THINKING_LEVELS,
    default="auto",
  )
  parser.add_argument(
    "--lang",
    default="en",
    metavar="LANG",
    help=(
      "Output language: vi or vi-VN selects Vietnamese; "
      "everything else uses English."
    ),
  )
  parser.add_argument("--instruction")
  parser.add_argument("--instruction-file")
  parser.add_argument("--api-key")
  parser.add_argument("--timeout", type=int, default=120)
  parser.add_argument(
    "--memory-file",
    help="Path to the SQLite database used by one-shot mode.",
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
  return args

# ============================================================
# INSTRUCTIONS
# ============================================================
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

def build_system_instruction(instruction, language, memory_summary=None):
  language_rule = (
    f"Always write the complete response in {language}. "
    "The selected output language controls the response language, "
    "regardless of the language used in the user's input. "
    "Follow natural grammar, spelling, terminology, and writing "
    "conventions of the selected language."
  )
  plain_text_rule = (
    "For ordinary text responses, use plain text. Do not wrap the "
    "response or ordinary text in Markdown formatting, Markdown "
    "headings, bold/italic markers, or Markdown code fences. "
    "Use natural paragraphs and line breaks instead. "
    "When code is requested, output the code directly without "
    "surrounding Markdown code fences, and preserve all code syntax "
    "and indentation. Do not remove or alter characters that are "
    "part of the code."
  )
  parts = [instruction, language_rule, plain_text_rule]
  if memory_summary:
    parts.append(
      "Persistent conversation memory (use as background context; "
      "prefer newer messages when details conflict):\n"
      + memory_summary
    )
  return "\n\n".join(parts)

# ============================================================
# GEMINI REST API
# ============================================================
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
    except urllib.error.URLError as exc:
      raise RuntimeError(
        f"Network error: {exc.reason}"
      ) from exc
    except TimeoutError as exc:
      raise RuntimeError("Request timed out.") from exc
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
):
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
      )
    ),
  }
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
  try:
    with urllib.request.urlopen(
      request,
      timeout=timeout,
    ) as response:
      response_data = response.read().decode("utf-8")
    data = json.loads(response_data)
  except urllib.error.HTTPError as exc:
    error_body = exc.read().decode(
      "utf-8",
      errors="replace",
    )
    try:
      error_data = json.loads(error_body)
      error_message = error_data.get(
        "error", {}
      ).get("message", error_body)
    except json.JSONDecodeError:
      error_message = error_body
    raise RuntimeError(
      f"HTTP {exc.code}: {error_message}"
    ) from exc
  except urllib.error.URLError as exc:
    raise RuntimeError(
      f"Network error: {exc.reason}"
    ) from exc
  except TimeoutError as exc:
    raise RuntimeError("Request timed out.") from exc
  except json.JSONDecodeError as exc:
    raise RuntimeError("Invalid JSON response.") from exc
  if data.get("status") in ("failed", "cancelled"):
    raise RuntimeError(
      f"Interaction status: {data.get('status')}"
    )
  answer = extract_text(data)
  if not answer:
    raise RuntimeError("Gemini returned no text.")
  generated_steps = data.get("steps", [])
  if not isinstance(generated_steps, list):
    raise RuntimeError("Invalid API steps response.")
  if not generated_steps:
    generated_steps = [{
      "type": "model_output",
      "content": [
        {"type": "text", "text": answer}
      ],
    }]
  return answer, generated_steps

# ============================================================
# ONE-SHOT PERSISTENT SQLITE MEMORY
# ============================================================
def get_memory_path(args):
  configured_path = (
    args.memory_file
    or os.environ.get("GEMINI_CLI_MEMORY_FILE")
  )
  if configured_path:
    return os.path.abspath(
      os.path.expanduser(configured_path)
    )
  return os.path.join(
    tempfile.gettempdir(),
    "gemini_cli_memory.sqlite3",
  )

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

# ============================================================
# COMMANDS
# ============================================================
def show_help(cli_language):
  ui = UI_TEXT[cli_language]
  commands = [
    ("/help", ui["help"]),
    ("/thinking auto|low|medium|high", ui["thinking_help"]),
    ("/lang vi|vi-VN|en", ui["lang_help"]),
    ("/instruction <text>", ui["instruction_help"]),
    ("/exit", ui["exit_help"]),
  ]
  print("\n" + COLORS.title(ui["help_title"]))
  for command, description in commands:
    print(
      f"  {COLORS.help(command)}\n"
      f"      {description}\n"
    )

# ============================================================
# MAIN
# ============================================================
def run():
  args = parse_args()
  question = (
    args.prompt
    if args.prompt is not None
    else args.question
  )
  one_shot = question is not None
  language = normalize_language(args.lang)
  cli_language = get_cli_language(language)
  ui = UI_TEXT[cli_language]
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
  api_key = args.api_key or os.environ.get("GEMINI_API_KEY")
  if not api_key:
    print(
      COLORS.error(ui["missing_api_key"]),
      file=sys.stderr,
    )
    return 2

  # --------------------------------------------------------
  # API KEY AND MODEL CHECK
  # --------------------------------------------------------
  if args.check_api_key:
    try:
      model_info = check_api_key_model(
        api_key=api_key,
        model=args.model,
        timeout=args.timeout,
      )
      display_name = model_info.get(
        "displayName",
        model_info["name"],
      )
      print(COLORS.value(ui["api_valid"]))
      print(COLORS.value(ui["model_accessible"]))
      print(f"{ui['model_name']}: {display_name}")
      return 0
    except RuntimeError as exc:
      print(
        COLORS.error(f"{ui['api_check_failed']}: {exc}"),
        file=sys.stderr,
      )
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

  # ========================================================
  # ONE-SHOT MODE
  # ========================================================
  if one_shot:
    question = question.strip()
    if not question:
      print(
        COLORS.error(ui["question_empty"]),
        file=sys.stderr,
      )
      return 2
    try:
      (
        memory_rows,
        memory_summary,
        last_summarized_id,
      ) = load_memory(memory_path)
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
      request_history = recent_history + [
        {
          "type": "user_input",
          "content": question,
        }
      ]
      answer, _ = call_gemini(
        api_key=api_key,
        model=model,
        history=request_history,
        instruction=instruction,
        language=language,
        thinking=thinking,
        timeout=args.timeout,
        memory_summary=memory_summary,
      )
      save_memory(
        memory_path,
        question,
        answer,
      )
    except (RuntimeError, ValueError) as exc:
      print(
        f"\n{ui['error']}: {exc}",
        file=sys.stderr,
      )
      return 1
    sys.stdout.write(answer)
    if not answer.endswith("\n"):
      sys.stdout.write("\n")
    sys.stdout.flush()
    # Run maintenance after displaying the answer.
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

  # ========================================================
  # INTERACTIVE MODE
  # ========================================================
  if sys.stdout.isatty():
    os.system("clear")
  print(COLORS.title(ui["title"]))
  print()
  status_rows = (
    (ui["mode_label"], ui["mode"]),
    (ui["model_label"], model),
    (ui["thinking"], thinking),
    (ui["language"], language),
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
  # Interactive history exists only in RAM.
  history = []
  while True:
    try:
      # Always derive CLI language from Gemini output language.
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
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
      # Automatically switch CLI interface language.
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
    if message.startswith("/"):
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      print(
        COLORS.error(ui["invalid_command"])
      )
      continue
    cli_language = get_cli_language(language)
    ui = UI_TEXT[cli_language]
    print()
    spinner = Spinner(ui["spinner"])
    spinner.start()
    try:
      request_history = history + [
        {
          "type": "user_input",
          "content": message,
        }
      ]
      answer, generated_steps = call_gemini(
        api_key=api_key,
        model=model,
        history=request_history,
        instruction=instruction,
        language=language,
        thinking=thinking,
        timeout=args.timeout,
      )
      history.append({
        "type": "user_input",
        "content": message,
      })
      history.extend(generated_steps)
    except (RuntimeError, ValueError) as exc:
      cli_language = get_cli_language(language)
      ui = UI_TEXT[cli_language]
      print(
        COLORS.error(f"\n{ui['error']}: {exc}"),
        file=sys.stderr,
      )
      print()
      continue
    finally:
      spinner.stop()
    cli_language = get_cli_language(language)
    if "\n" in answer:
      print(
        f"{COLORS.gemini('Gemini')} ❯ \n\n{answer}"
      )
      print()
    else:
      print(
        f"{COLORS.gemini('Gemini')} ❯ {answer}"
      )
      print()
  return 0

if __name__ == "__main__":
  raise SystemExit(run())