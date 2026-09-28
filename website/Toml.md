# TOML configuration guide for Tool-Tree

**Source:** [`website/Toml.html` @ Kakathic/Tool-Tree](https://raw.githubusercontent.com/Kakathic/Tool-Tree/refs/heads/main/website/Toml.html)  
**Format:** Converted from HTML to Markdown on 2026-09-28 · 26 sections · 54 subsections · 55 tables · 32 code examples

---

## Table of contents

**Basics**

- [1. Introduction](#toml-configuration-guide-for-tool-tree)
- [2. Syntax rules](#2-syntax-rules)
  - [2.1. Always use double brackets `[[name]]` for every entry](#21-always-use-double-brackets-name-for-every-entry)
  - [2.2. Node type = TOML table name](#22-node-type--toml-table-name)
  - [2.3. Group children = nearest preceding `[[group]]` (dot-notation removed)](#23-group-children--nearest-preceding-group-dot-notation-removed)
  - [2.4. Display order = position in file](#24-display-order--position-in-file)
  - [2.5. Boolean values - 3 accepted forms](#25-boolean-values---3-accepted-forms)
  - [2.6. String resource references](#26-string-resource-references)
  - [2.7. `load-key` - inline key/value substitution (i18n)](#27-load-key---inline-keyvalue-substitution-i18n)
  - [2.8. Path placeholders: `{HOME}`, `{ICON}`](#28-path-placeholders-home-icon)
  - [2.9. Built-in variables: `{ROT}`, `{LOT}`](#29-built-in-variables-rot-lot)
- [3. Node type overview](#3-node-type-overview)
- [4. Shared fields (NodeInfoBase + ClickableNode + RunnableNode)](#4-shared-fields-nodeinfobase--clickablenode--runnablenode)
  - [4.1. NodeInfoBase (present on every node)](#41-nodeinfobase-present-on-every-node)
  - [4.2. ClickableNode (for page, action, switch, picker, editor, download)](#42-clickablenode-for-page-action-switch-picker-editor-download)
  - [4.3. RunnableNode (for action, switch, picker, download)](#43-runnablenode-for-action-switch-picker-download)

**Node types**

- [5. `[[group]]` - Container for child nodes](#5-group---container-for-child-nodes)
  - [5.1. Group fields](#51-group-fields)
  - [5.2. Demo](#52-demo)
  - [5.3. Grouping rules (no nesting)](#53-grouping-rules-no-nesting)
- [6. `[[page]]` - Sub-page](#6-page---sub-page)
  - [6.1. Page fields](#61-page-fields)
  - [6.2. Demo](#62-demo)
- [7. `[[action]]` - Action (runs a shell script)](#7-action---action-runs-a-shell-script)
  - [7.1. Action fields](#71-action-fields)
  - [7.2. Demo](#72-demo)
- [8. `[[action.params]]` - Input parameters](#8-actionparams---input-parameters)
  - [8.1. Basic param fields](#81-basic-param-fields)
  - [8.2. Common `type` values](#82-common-type-values)
  - [8.3. Demo](#83-demo)
  - [8.4. Dependencies (depend-*)](#84-dependencies-depend-)
- [9. `[[switch]]` - On/off toggle](#9-switch---onoff-toggle)
  - [9.1. Switch fields](#91-switch-fields)
  - [9.2. Demo](#92-demo)
- [10. `[[picker]]` - Value selector](#10-picker---value-selector)
  - [10.1. Picker fields](#101-picker-fields)
  - [10.2. Demo](#102-demo)
- [11. `[[text]]` - Rich text block](#11-text---rich-text-block)
  - [11.1. Text fields](#111-text-fields)
  - [11.2. Demo](#112-demo)
- [12. `[[text.rows]]` - Rich text row](#12-textrows---rich-text-row)
  - [12.1. Row fields](#121-row-fields)
  - [12.2. Demo](#122-demo)
  - [12.3. Inline HTML block (`html-file` / `html-url`)](#123-inline-html-block-html-file--html-url)
  - [12.4. Inline Markdown syntax reference (`markdown = true`)](#124-inline-markdown-syntax-reference-markdown--true)
  - [12.5. Pinning rows to the left / center / right of the same line](#125-pinning-rows-to-the-left--center--right-of-the-same-line)
  - [12.6. Reset a single row on demand (`reset`)](#126-reset-a-single-row-on-demand-reset)
  - [12.7. Inline progress bar (`progress`) and flash highlight (`flash`)](#127-inline-progress-bar-progress-and-flash-highlight-flash)
- [13. `[[editor]]` - Open file in the text editor](#13-editor---open-file-in-the-text-editor)
  - [13.1. Editor fields](#131-editor-fields)
  - [13.2. Demo](#132-demo)
- [14. `[[download]]` - Download a file via HTTP](#14-download---download-a-file-via-http)
  - [14.1. Download fields](#141-download-fields)
  - [14.2. Demo](#142-demo)
- [15. `[[resource]]` - Extract assets](#15-resource---extract-assets)
  - [15.1. Fields](#151-fields)
- [16. `[[menu]]` / `[[fab]]` - Overflow menu and FAB](#16-menu--fab---overflow-menu-and-fab)
  - [16.1. Structure](#161-structure)
  - [16.2. Item fields (inside menu/fab)](#162-item-fields-inside-menufab)
  - [16.3. Demo](#163-demo)

**Advanced**

- [17. Dependencies (depend-*)](#17-dependencies-depend-)
  - [17.1. depend-* fields (only for `[[action.params]]`)](#171-depend--fields-only-for-actionparams)
  - [17.2. Demo - simple dependency](#172-demo---simple-dependency)
  - [17.3. depend-logic reference](#173-depend-logic-reference)
  - [17.4. depend-default and depend-initial](#174-depend-default-and-depend-initial)
- [18. Shell-script fields - overview](#18-shell-script-fields---overview)
- [19. resolveBoolOrShell](#19-resolveboolorshell)
- [20. Pending states](#20-pending-states)
- [21. process = true](#21-process--true)
- [22. Page lifecycle](#22-page-lifecycle)
- [23. Script output control sequences](#23-script-output-control-sequences)
  - [23.1. exit:[kill] / exit:[restart]](#231-exitkill--exitrestart)
  - [23.2. choose:[value1|Label1,value2|Label2,...]](#232-choosevalue1label1value2label2)
  - [23.3. pick:[values] / pickv:[values] / pickh:[values]](#233-pickvalues--pickvvalues--pickhvalues)
  - [23.4. input:[prompt]](#234-inputprompt)
  - [23.5. progress:[current/total]](#235-progresscurrenttotal)
  - [23.6. am:[...] (send an Android Intent)](#236-am-send-an-android-intent)

**Practice**

- [24. Full example](#24-full-example)
- [25. Tips & pitfalls](#25-tips--pitfalls)
- [26. Changelog - recently added fields](#26-changelog---recently-added-fields)

---

> [!NOTE]
> This guide is for **Tool-Tree**, the component that powers the function menu in the `com.tool.tree` Android app. All page configuration files must use **TOML** (XML is no longer supported). The parser lives in `com.omarea.krscript.config.PageConfigReader` and uses the `org.tomlj:tomlj` library.

This reference is intended for anyone authoring or maintaining a Tool-Tree `.toml` page: it documents every node type, every field (with its aliases, type, and default), and the runtime behavior around them - dependency evaluation, shell-script integration, page lifecycle, and load ordering. Sections 2-4 cover the shared rules and fields every node relies on; sections 5-16 document each node type in turn; sections 17-23 cover cross-cutting mechanics; sections 24-26 provide a worked example, a checklist of common mistakes, and a changelog of recently added fields.

A TOML configuration file describes **the node tree of a page**: each page is a list of *nodes* (group, action, switch, picker, text, page, editor, download, resource, menu, fab) displayed in the order they appear in the file. All nodes are declared **flat** at the top level of the document (dot-notation nesting like `[[group.action]]` has been removed) - a node that follows a `[[group]]` (and precedes the next one) automatically becomes a child of that group.

---

## 2. Syntax rules

### 2.1. Always use double brackets `[[name]]` for every entry

TOML does not allow mixing `[name]` (single brackets, single table) and `[[name]]` (double brackets, array of tables) for the same key at the same position - this will cause a parse error. To be safe, **always use `[[name]]`** for any entry, even if there is currently only one entry of that type. This prevents breaking the file later when you add a second entry of the same type but forget to switch the brackets.

> [!CAUTION]
> **Wrong:** declaring `[group]` and then later declaring another `[[group]]` in the same file -> parse error.

> [!TIP]
> **Right:** always use `[[group]]`, `[[action]]`, etc. for all entries.

### 2.2. Node type = TOML table name

There is no separate `type` field. A node's type is determined by its TOML table name:

| Table name | Node type | Description |
| --- | --- | --- |
| `group` | Container | Holds child nodes - the flat entries declared after it become its children (no nesting) |
| `page` | Sub-page | Opens another page when clicked |
| `action` | Action | Runs a shell script |
| `switch` | Toggle | On/off switch with get/set shell |
| `picker` | Picker | Single/multi value selector |
| `text` | Rich text block | Multi-row text (bold/italic/link/photo...) |
| `editor` | File editor | Opens a file in the built-in editor |
| `download` | Download | Downloads a file, then runs a script |
| `resource` | Asset extraction | Extracts assets from APK (invisible) |
| `menu` | Overflow menu | Toolbar 3-dot menu container |
| `fab` | Floating button | FAB container on the page |

### 2.3. Group children = nearest preceding `[[group]]` (dot-notation removed)

All node types are declared as **flat `[[type]]` entries at the top level** of the document - the dotted `[[group.action]]` / `[[subgroup.type]]` style is **no longer supported**. A child node simply belongs to the most recently declared `[[group]]` above it in the file; the parser walks the document once, in line order, and assigns each entry to the current group.

```toml
[[group]]
title = "CPU"

[[switch]]        # child of group "CPU"
...

[[action]]        # also a child of group "CPU"
...

[[group]]
title = "Battery"

[[switch]]        # child of group "Battery"
...
```

> [!CAUTION]
> **Deprecated:** entries nested via dotted paths such as `[[group.action]]` are **silently ignored** - the parser only reads node tables at the top level of the document (`tomlChildren()` is called exactly once, on the root table, and is no longer recursive per group).

> [!TIP]
> **Reserved name:** `[[toml]]` is not a node type. It is only a marker used to recognize inline TOML output from `config-sh` (see [section 6](#6-page---sub-page)). If it appears in a file, the parser skips it harmlessly.

### 2.4. Display order = position in file

Display order is always **top-to-bottom** by the position of the `[[name]]` entry in the file, regardless of node type (group, action, page, text, switch ...) - even when types are **interleaved** (e.g. an action, then a page, then another action). The position is read directly from the `tomlj` API, so there is no need for an `order` field.

> [!TIP]
> The parser uses `TomlArray.inputPositionOf(index).line()` to obtain the line number of each array element. If the API returns null (due to error or unsupported feature), the parser falls back to read order (seq) - it never crashes.

### 2.5. Boolean values - 3 accepted forms

Boolean fields such as `confirm`, `readonly`, `auto-off` accept:

| Value | Result | Notes |
| --- | --- | --- |
| `true` / `1` | true | Statically true |
| `false` / `0` | false | Statically false |
| (any other string) | Run as shell, `"1"` => true, otherwise => false | **(shell)** via `resolveBoolOrShell()` |

Example: `readonly = "test -f /sdcard/lock && echo 1"` - the parser runs that command, and if it returns `"1"` then readonly = true.

> [!WARNING]
> **Note:** where the shell result only decides *layout* (`support`/`visible` of nodes and params, `show` of actions), the script runs immediately during parsing. Deferred fields - switch/picker/toggle `get`, row `support`, and all `*-sh` dynamic strings (title-sh, desc-sh, summary-sh, warn-sh) - are queued into *pending states* and batched at the end (see [section 20](#20-pending-states)). An expensive shell here (e.g. `support-sh`) still delays the whole page - mark that one entry `load-after = true` to build it AFTER the page has already loaded (see section 4.1).

### 2.6. String resource references

Any text field (`title`, `desc`, `summary` ...) may include a reference to the app's string resources via `@string/name` or `@string:name`. The parser resolves these automatically. If the resource is not found, the original string is kept.

```toml
[[group]]
title = "@string/group_battery_title"

[[switch]]
title = "@string/switch_fast_charge_title"
desc = "@string:switch_fast_charge_desc"
get = "getprop sys.fastcharge"
set = "setprop sys.fastcharge $state"
```

> [!TIP]
> Both `@string/name` and `@string:name` point to the same resource lookup - use whichever separator you prefer, they are interchangeable.

### 2.7. `load-key` - inline key/value substitution (i18n)

A page may declare **one** directive line anywhere in the document (typically at the very top):

```toml
load-key = "strings_en.txt"

[[switch]]
title = "@wifi_title"
get = "settings get global wifi_on"
set = "settings put global wifi_on $state"
```

Before the TOML is parsed, the parser reads the key file and substitutes every `@name` token in the **whole document** with the matching value. This is a plain text pre-processing step (it runs on the raw file, before `Toml.parse()`), so `@name` works inside any string field - titles, descs, scripts, options, `lock` messages...

| Directive | Description |
| --- | --- |
| `load-key = "path"` | Path of the key file - resolved like every other path (relative to the page config dir, app-private dir, APK assets, or absolute), and accepts the `{HOME}`/`{ICON}` path placeholders described in [§2.8](#2-syntax-rules) |
| `load-key-sh = "script"` | **(shell)** Script whose output is the key file path instead of a static path |

The key file itself is a simple list of lines `name = "value"` (name starts with a letter/underscore; value is quoted; `\` escape sequences are supported):

```toml
# strings_en.txt
wifi_title = "Wi-Fi booster"
wifi_desc = "Keep Wi-Fi on during sleep"
```

> [!TIP]
> Substitution details: unknown `@name` references are left untouched; values are re-escaped (`\` → `\\`, `"` → `\"`) so they are safe inside TOML strings; the directive line itself is removed from the document after processing. Only the **first** directive line is applied - later ones are ignored.

> [!WARNING]
> The token prefix used to change from `$name` to `@name` - a shell variable like `$PATH`/`$HOME`/`$1` inside a `script`/`run` field would otherwise collide with a load-key of the same name. For the same reason, avoid naming a load-key `string`, `color`, or `android` - those are reserved by the `@string/...`, `@color/...` and `@android:color/...` resource references from §2.6 and §8.2, and a load-key with one of these names would shadow them.

### 2.8. Path placeholders: `{HOME}`, `{ICON}`

These are resolved by the path lookup itself (`PathAnalysis`), not by the whole-document text substitution above - so they only take effect inside fields that are actually treated as a **path**: `icon`/`icon-sh` output, `photo`, `bg`, `logo`, row `icon`/`photo`, `html-file`, the editor's `file`, `resource-file`/`resource-dir`, and `load-key`/`load-key-sh` above.

| Placeholder | Description |
| --- | --- |
| `{HOME}` | The app's toolkit "home" directory. Equivalent to writing the absolute path by hand, but portable across app installs |
| `{ICON}` | The app's icon directory (`home/etc/icon`) - a shortcut for icon assets shipped alongside the config, e.g. `icon = "{ICON}/wifi.png"` |

### 2.9. Built-in variables: `{ROT}`, `{LOT}`

Unlike `{HOME}`/`{ICON}`, these are substituted directly on the raw document text - same mechanism as `load-key`'s `@name`, but always available with **no `load-key` directive required**. They report the device's root state:

| Placeholder | Value |
| --- | --- |
| `{ROT}` | `"1"` if the device is rooted, `"0"` otherwise |
| `{LOT}` | Opposite of `{ROT}`: `"1"` if **not** rooted, `"0"` if rooted |

```toml
[[action]]
title = "Flash module"
lock = "{LOT}|Requires root access"
script = "..."
```

> [!TIP]
> The example above locks the action (with message "Requires root access") only on non-rooted devices, since `{LOT}` resolves to `"1"` there. Because the substitution is on raw text, `{ROT}`/`{LOT}` work inside `lock`, `script`, `title` - any string field, same as `@name`.

---

## 3. Node type overview

Quick reference for all node types. Click a name to jump to its detailed section.

| Node type | Description |
| --- | --- |
| [`[[group]]`](#5-group---container-for-child-nodes) | Container - the flat entries following it become its children |
| [`[[page]]`](#6-page---sub-page) | Sub-page - opens a new page (file/config-sh) |
| [`[[action]]`](#7-action---action-runs-a-shell-script) | Action - runs a shell script, may have params/rows |
| [`[[action.params]]`](#8-actionparams---input-parameters) | Input parameters for an action (text/file/seekbar...) |
| [`[[switch]]`](#9-switch---onoff-toggle) | On/off toggle, reads/writes state via shell |
| [`[[picker]]`](#10-picker---value-selector) | Single/multi value selector from a list |
| [`[[text]]`](#11-text---rich-text-block) | Rich text block (bold/italic/link/photo...) |
| [`[[text.rows]]`](#12-textrows---rich-text-row) | Rich text row inside text/action/page/download |
| [`[[editor]]`](#13-editor---open-file-in-the-text-editor) | Opens a file in the built-in text editor |
| [`[[download]]`](#14-download---download-a-file-via-http) | Downloads a file via HTTP then runs a script |
| [`[[resource]]`](#15-resource---extract-assets) | Extracts assets from APK to storage (invisible) |
| [`[[menu]] / [[fab]]`](#16-menu--fab---overflow-menu-and-fab) | Overflow menu and FAB of the page itself |

---

## 4. Shared fields (NodeInfoBase + ClickableNode + RunnableNode)

These are the fields that **every node has** (inherited from `NodeInfoBase`). All text fields accept `@string/...` references.

### 4.1. NodeInfoBase (present on every node)

| Field | Alias | Type | Default | Description |
| --- | --- | --- | --- | --- |
| title | - | String | "" | Display title. Accepts `@string/...` |
| title-sh | - | String | "" | **(shell)** Script that produces title dynamically. Runs during parse; overrides `title` |
| desc | - | String | "" | Short description shown below title |
| desc-sh | - | String | "" | **(shell)** Script that produces desc dynamically |
| summary | - | String | "" | Extra info shown in small grey text |
| summary-sh | - | String | "" | **(shell)** Script that produces summary dynamically |
| key | index, id | String | auto UUID | Unique ID. Required for Desktop shortcuts. If key starts with `@`, `allowShortcut` defaults to false |
| support | visible | Bool\|Shell | true | Hide/show this node. Accepts `true`/`false` or a **(shell)** returning `"1"` |
| load-after | - | Bool | false | true: this entry is NOT built with the rest of the page - it is built and inserted (at its ORIGINAL position in the file, inside its group if any) AFTER the page has already finished loading/showing. Use for a slow-to-resolve entry (e.g. expensive `support-sh`) that would otherwise delay the whole page. Bool only (no shell) |

### 4.2. ClickableNode (for page, action, switch, picker, editor, download)

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| icon | icon-path | String | Icon path shown on the left of the item |
| icon-sh | - | String | **(shell)** Script that produces the icon path dynamically. Runs during parse (batched with title-sh/desc-sh/summary-sh); overrides `icon` |
| logo | logo-path | String | Large icon used when creating a shortcut (different from small `icon`) |
| photo | photo-path | String | Large image shown in the detail dialog |
| photo-sh | - | String | **(shell)** Script that produces the photo path dynamically. Runs during parse (batched with title-sh/icon-sh/...); overrides `photo` |
| photo-real-size | photo-original-size | Bool | true: show image at its real size, no stretching |
| photo-gif-num | gif-num, gif_num | Int | >0: image is an animated GIF (photo_1.png, photo_2.png...) |
| photo-gif-time | gif-time, gif_time | Int | Time per frame (ms; default 300) |
| photo-gif-autoplay | gif-autoplay, gif_autoplay | Bool | true (default): auto-play the GIF |
| photo-gif-loop | photo-gif-loop-count, gif-loop, gif-loop-count, gif_loop_count | Int | Loop count (0 = infinite) |
| photo-real-gif | real-gif, photo-gif-real | Bool | true: `photo` points to a REAL `.gif` file (decoded via `AnimatedImageDrawable`) instead of the simulated frame-sequence GIF above. **Requires Android 9 / API 28+** - on older devices this flag is ignored and falls back to a static first frame. When enabled, `photo-gif-num`/multi-path frame lists are not needed for this field |
| icon-gif-num | icon-gif_num | Int | Same as photo-gif-num but for the small icon |
| icon-gif-time | icon-gif_time | Int | Time per frame for the icon |
| icon-gif-autoplay | icon-gif_autoplay | Bool | Auto-play icon GIF |
| icon-gif-loop | icon-gif-loop-count, icon-gif_loop_count | Int | Loop count for the icon GIF |
| icon-real-gif | icon-gif-real | Bool | Same as `photo-real-gif` but for the small icon - REAL `.gif` file, Android 9/API 28+ only |
| bg | bg-path | String | Background image for the item/dialog |
| bg-sh | - | String | **(shell)** Script that produces the background image path dynamically. Runs during parse (batched); overrides `bg` |
| lock | lock-state | String | Lock the item (no interaction allowed). `true`/`1` = locked; or the format `"state\|message"`, e.g. `lock = "1\|Requires root"` - the message is shown when the user clicks the locked item. The whole field is plain text, so the `message` accepts `@string/...` resource references (§2.6) as well as `{ROT}`/`{LOT}` and `@key` load-key substitution (§2.7-2.9), e.g. `lock = "{LOT}\|@root_required_msg"` |
| lock-sh | - | String | **(shell)** Script that checks the lock state dynamically (returns `"1"` = locked) |
| min-sdk | sdk-min | Int | Minimum Android SDK version |
| max-sdk | sdk-max | Int | Maximum Android SDK version (default 100) |
| target-sdk | sdk-target | Int | Target Android SDK version |
| allow-shortcut | - | Bool? | Allow creating a shortcut. Default null (auto). Forced false when key starts with `@` |

### 4.3. RunnableNode (for action, switch, picker, download)

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| confirm | - | Bool | Ask for confirmation before running |
| warn | warning | String | Warning text shown in the confirmation dialog |
| warn-sh | warning-sh | String | **(shell)** Script that produces warning dynamically |
| auto-off | auto-close | Bool | Auto-close the log dialog after running |
| auto-finish | - | Bool | Auto-close the page after running |
| auto-kill | - | Bool | Auto-kill related processes |
| auto-restart | - | Bool | Auto-restart the service |
| interruptible | interruptable | Bool | Allow interrupting mid-run |
| need-input | needs-input, require-input | Bool | Script uses `read` to receive keyboard input |
| reload-page | - | Bool | Reload the whole page after running |
| reload | - | Bool\|String | `true` = reload page; or a comma-separated list of block IDs to refresh only those blocks |
| shell | - | String | Interaction mode, NOT the script content: `default` (log dialog) / `bg-task` (background, no dialog) / `hidden`. Use `script` to declare the script itself |
| bg-task | background-task, async-task | Bool | Run in background (no log dialog) - equivalent to `shell = "bg-task"` |
| script | set, setstate | String | Main script for `action`/`download`; set-state script for `switch`/`picker` (receives `$state`) |

> [!TIP]
> **Refreshing the page:** use `reload-page = true` or `reload = true` to make the app reload the whole page after a script finishes (e.g. after toggling a switch, to refresh the summary). To refresh only specific blocks (e.g. only the affected switches), use `reload = "id1,id2"`.

---

## 5. `[[group]]` - Container for child nodes

`[[group]]` is a container that groups related nodes. It has no icon and is not clickable - it's just a title with a list of child nodes below it.

### 5.1. Group fields

| Field | Alias | Type | Default | Description |
| --- | --- | --- | --- | --- |
| title | - | String | "" | Group title (displayed uppercase, grey) |
| title-sh | - | String | "" | **(shell)** Script that produces title |
| key | index, id | String | auto | Unique ID |
| support | visible | Bool\|Shell | true | Hide/show the group - a hidden group ALSO hides all of its children (every entry until the next `[[group]]` is skipped, their shells are not run). Accepts shell too! |

### 5.2. Demo

> **Demo (Android UI)**
>
> - **CPU**
>     - **Optimize CPU** — Set governor to performance
>     - **Governor** — *performance*
> - **Memory**
>     - **Clear cache**

```toml
[[group]]
title = "CPU"

[[action]]
title = "Optimize CPU"
desc = "Set governor to performance"
script = "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

[[picker]]
title = "Governor"
get = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
set = "echo $state > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

[[group]]
title = "Memory"

[[action]]
title = "Clear cache"
script = "sync; echo 3 > /proc/sys/vm/drop_caches"
```

### 5.3. Grouping rules (no nesting)

Since dot-notation was removed, groups can no longer be nested inside each other. Every `[[group]]` is a top-level sibling: declaring a new `[[group]]` closes the previous one, and all following entries belong to the new group. Entries declared before the first `[[group]]` are rendered directly on the page without a group header.

> [!WARNING]
> Groups also don't support inline `resource-file`/`resource-dir` fields (see [section 15](#15-resource---extract-assets)) and have no `desc`/`summary` - a group is just a title + its children.

---

## 6. `[[page]]` - Sub-page

`[[page]]` is not an action - it's a **link to another page**. Clicking it opens a new page (another .toml file, an HTML page, or another Activity).

### 6.1. Page fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| config | - | String | Path to the sub-page .toml file (relative or absolute) |
| config-sh | - | String | **(shell)** Script returning **inline TOML content** or a **.toml file path**. Auto-detected by the `[[group]]` header |
| html | - | String | Online HTML URL - opens in a WebView |
| link | href | String | URL opened by the system browser |
| activity | a, intent | String | Activity intent to launch (e.g. `com.example.MyActivity`) |
| before-load | before-read | String | **(shell)** Script run BEFORE reading the sub-page config |
| after-load | after-read | String | **(shell)** Script run AFTER reading completes |
| load-ok | load-success | String | **(shell)** Script run if load succeeds |
| load-fail | load-error | String | **(shell)** Script run if load fails |
| process | - | Bool | true: show items one by one as they build (with progress bar) instead of waiting (see [section 21](#21-process--true)) |
| placeholder-count | placeholder_count | Int | Only used when `process = true`. Number of skeleton placeholders shown up-front while waiting for the first real item. Default: `1` (see [section 21](#21-process--true)) |
| lock | lock-state | String | Static lock: `true`/`1` or the `"1\|message"` format (message accepts `@string/...`, `@key`, `{ROT}`/`{LOT}` - see §4.2) |
| lock-sh | - | String | **(shell)** Script to check the lock state (returns `"1"` = locked) |
| rows | - | Array | Rich-text rows shown below the page (see [section 12](#12-textrows---rich-text-row)) |

### 6.2. Demo

> **Demo (Android UI)**
>
> - **App list** — Manage installed apps
> - **Advanced settings** — Open external config file

```toml
[[page]]
title = "App list"
desc = "Manage installed apps"
config = "pages/app_list.toml"

[[page]]
title = "Advanced settings"
desc = "Open external config file"
config = "/sdcard/Tool-Tree/advanced.toml"
```

> [!TIP]
> `config-sh` returns **one of two things**: either a .toml file path (output ending with `.toml`), or inline TOML content. Inline TOML is recognized when the 1st or 2nd non-empty line of the output starts with `[[group]]` or the reserved marker `[[toml]]` - see `PageConfigSh.looksLikeInlineToml()`. Anything else shows an error toast.

---

## 7. `[[action]]` - Action (runs a shell script)

`[[action]]` is the most common node type: clicking it shows a dialog (if it has `confirm`/`params`/`warning`), then runs the script and shows the output in a log dialog.

### 7.1. Action fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| script | set, setstate | String | **(required)** Main shell script. Receives `$param_name` env vars from params |
| lock | lock-state | String | Static lock: `true`/`1` or the `"1\|message"` format (message accepts `@string/...`, `@key`, `{ROT}`/`{LOT}` - see §4.2). When locked, the action is not clickable |
| lock-sh | - | String | **(shell)** Script to check lock dynamically (returns `"1"` = locked) |
| menu | - | Bool | true: action does NOT appear in the list; instead appears as its own icon on the toolbar |
| show | - | Bool\|Shell | true (or shell): auto-open this action's dialog when entering the page (only once) |
| params | - | Array | List of input parameters - see [section 8](#8-actionparams---input-parameters) |
| rows | - | Array | Rich-text rows shown below the item - see [section 12](#12-textrows---rich-text-row) |
| params-rows | - | Array | Rows for the params dialog only (separate from `rows` which appear both in list and dialog) |
| + all fields of RunnableNode, ClickableNode, NodeInfoBase (see [section 4](#4-shared-fields-nodeinfobase--clickablenode--runnablenode)) |  |  |  |

### 7.2. Demo

> **Demo (Android UI)**
>
> - **Optimize CPU** — Set governor to performance
> - **Clear cache** — Clear system cache

```toml
[[action]]
title = "Optimize CPU"
desc = "Set governor to performance"
menu = true
key = "optimize_cpu"
script = "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

[[action]]
title = "Clear cache"
desc = "Clear system cache"
confirm = true
warn = "This will clear all app caches"
script = "sync; echo 3 > /proc/sys/vm/drop_caches"
auto-off = true
```

> [!TIP]
> `menu = true` and `show = true` are **independent**: an action can be both on the toolbar (menu=true) and auto-open its dialog (show=true). When an action moves to the toolbar (menu=true), an unset key falls back to `title` to use as the Menu itemId.

---

## 8. `[[action.params]]` - Input parameters

Each `[[action.params]]` defines one input field in the dialog shown when the user clicks an action. The parameter's value is passed to the action's script via the `$param_name` environment variable.

### 8.1. Basic param fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| name | - | String | **(required)** Variable name (unique within an action). Becomes `$name` in the script |
| title | - | String | Display label |
| title-sh | - | String | **(shell)** Script that produces title dynamically |
| label | - | String | Secondary label |
| label-sh | - | String | **(shell)** Script that produces label |
| desc | - | String | Short description |
| desc-sh | - | String | **(shell)** Script that produces desc |
| desc-on | on-desc, desc-checked | String | Note shown SPECIFICALLY when the checkbox/switch is ON (only for type=bool/checkbox/switch) |
| desc-on-sh | on-desc-sh, desc-checked-sh | String | **(shell)** Script that produces desc-on |
| type | - | String | Input type: `text`/`int`/`number`/`bool`/`checkbox`/`switch`/`seekbar`/`file`/`folder`/`app`/`packages`/`spinner`/`single-select`/`multi-select`/`color` |
| value | - | String\|Array | Default value. Also accepts a TOML array of strings (e.g. `value = ["a", "b"]`) for `multi-select` - each entry is one selected value, joined internally using `separator` |
| value-sh | - | String | **(shell)** Script that fetches value dynamically (runs when dialog opens) |
| placeholder | - | String | Hint when empty |
| placeholder-sh | - | String | **(shell)** Script that produces placeholder |
| required | - | Bool | Required input |
| readonly | - | Bool\|Shell | Read-only (no editing). `true`/`1`/`readonly` = read-only, `false`/`0`/`empty` = editable; any other string is stored as a shell (`readonlySh`) evaluated later |
| maxlength | - | Int | Max character count (type=text) |
| min | - | Int | Minimum value (seekbar) |
| max | - | Int | Maximum value (seekbar). Default `Int.MAX_VALUE` |
| options | - | Array | List of choices (spinner/single-select/multi-select) |
| items | - | Array of String | Shorthand choice list - each entry is `"value\|title"` or just `"value"`. Takes priority over `options` tables when both are declared |
| options-sh | option-sh | String | **(shell)** Script that produces options dynamically |
| multiple | - | Bool | Allow multiple files/options |
| separator | - | String | Value separator (multi). Default `\n` |
| suffix | - | String | File extension filter (e.g. `"zip,apk"`). Only type=file |
| mime | - | String | MIME type filter. Only type=file |
| path-home | home-path, pathhome | String | Initial directory when opening file picker |
| editable | - | Bool | Allow manual path entry |
| support | visible | Bool\|Shell | Hide/show the param |
| sort | - | Bool | Move readonly params to the bottom (only effective when `readonly` is declared) |
| allow-no-selection | no-select | Bool | Allow spinner to be empty (e.g. when you need to distinguish "not selected" from "first item") |
| remember | remember-value | Bool | Persist the user's last chosen/entered value (keyed by page config path + action `key` + param `name`) and auto-restore it as the default the next time the dialog opens - overrides static `value`. If the param also has `value-sh`, the shell result still wins (it reflects the real current system state); the remembered value is only used when `value-sh` is absent. **Default: `true`** - set `remember = "false"` to opt out and always fall back to static `value` |

### 8.2. Common `type` values

| type | UI | Related fields |
| --- | --- | --- |
| `text` | Text input | `value`, `placeholder`, `maxlength`, `required` |
| `int` / `number` | Numeric text input - value is validated against `min`/`max` when the dialog is confirmed (out-of-range blocks OK, empty = no validation) | `value`, `min`, `max`, `required` |
| `bool` / `checkbox` / `switch` | Toggle ON/OFF | `value` = "1"/"0", `desc-on` for ON-state note |
| `seekbar` | Slider | `min`, `max`, `value` |
| `file` | File picker | `suffix`, `mime`, `path-home`, `multiple`, `editable` |
| `folder` | Directory picker | `path-home`, `multiple`, `editable` |
| `app` | App picker | `multiple` |
| `spinner` / `single-select` | Dropdown single choice | `options`/`options-sh`, `allow-no-selection` |
| `multi-select` | Checkbox list multi choice | `options`, `multiple=true`, `separator` |
| `color` | Color picker widget - accepts `#RRGGBB`/`#AARRGGBB` hex or a resource reference (`@color/name`, `@android:color/name`); an invalid value blocks OK | `value` (default color) |
| `packages` | Alias of `app` - same app-chooser UI and behavior | `multiple` |

### 8.3. Demo

> **Demo - params dialog**
>
> - **Frequency (kHz)** — Enter max frequency
> - **2400000**
> - **Governor** · performance
> - **Apply to all CPUs** — switch: **ON**
> - **Config file**

```toml
[[action]]
title = "Set CPU max freq"
script = """
for cpu in /sys/devices/system/cpu/cpu*/cpufreq; do
  [ "$all_cpus" = "0" ] && [ "$cpu" != "/sys/devices/system/cpu/cpu0/cpufreq" ] && continue
  echo $freq > "$cpu/scaling_max_freq"
  echo $gov > "$cpu/scaling_governor"
done
"""

[[action.params]]
name = "freq"
title = "Frequency (kHz)"
desc = "Enter max frequency"
type = "text"
value = "2400000"

[[action.params]]
name = "gov"
title = "Governor"
type = "spinner"
value = "performance"

[[action.params.options]]
title = "Performance"
value = "performance"

[[action.params.options]]
title = "Powersave"
value = "powersave"

[[action.params]]
name = "all_cpus"
title = "Apply to all CPUs"
type = "switch"
value = "1"

[[action.params]]
name = "cfg"
title = "Config file"
type = "file"
suffix = "conf"
```

### 8.4. Dependencies (depend-*)

A param can be hidden/shown (or switched to readonly) based on the value of another param. This is a large feature - see [section 17](#17-dependencies-depend-) for details.

---

## 9. `[[switch]]` - On/off toggle

`[[switch]]` displays an ON/OFF toggle. When the user toggles it, the `set` script is called with the env var `$state` set to `"1"` or `"0"`. When the page loads, the `get` script is called to read the current state.

### 9.1. Switch fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| get | getstate | String | **(required)** Script to read state. Returning `"1"`/`"true"` = ON. If omitted, the switch always shows OFF |
| set | setstate | String | **(required)** Script to set state. Receives `$state` = "1"/"0" |
| lock | lock-state | String | Static lock: `true`/`1` or the `"1\|message"` format (message accepts `@string/...`, `@key`, `{ROT}`/`{LOT}` - see §4.2) |
| lock-sh | - | String | **(shell)** Script to check lock dynamically (returns `"1"` = locked) |
| rows | - | Array | Rich-text rows shown below the item - see [section 12](#12-textrows---rich-text-row) |
| + all fields of RunnableNode, ClickableNode, NodeInfoBase |  |  |  |

### 9.2. Demo

> **Demo (Android UI)**
>
> - **Display**
>     - **Dark mode** — switch: **ON** — Enable dark UI
>     - **Auto brightness** — switch: **OFF**

```toml
[[group]]
title = "Display"

[[switch]]
title = "Dark mode"
desc = "Enable dark UI"
get = "cmd uimode night get | grep -q yes && echo 1"
set = "cmd uimode night $([ \"$state\" = \"1\" ] && echo yes || echo no)"

[[switch]]
title = "Auto brightness"
get = "settings get system screen_brightness_mode"
set = "settings put system screen_brightness_mode $state"
```

> [!TIP]
> The parser does NOT run `get` immediately when parsing each switch. Instead, all `get` scripts of switches/pickers are **queued into pendingSwitchStates** and run **exactly once** at the end of `readConfigToml()` via `resolvePendingStates()` - reducing N shell round-trips to 1 (see [section 20](#20-pending-states)).

---

## 10. `[[picker]]` - Value selector

`[[picker]]` displays a current value; clicking it opens a popup to choose one (or more) values from a list. When the user confirms, the `set` script is called with `$state` = the chosen value.

### 10.1. Picker fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| get | getstate | String | **(required)** Script to read the current value |
| set | setstate | String | **(required)** Script to set the value. Receives `$state` |
| options | - | Array | Static options list - `[[picker.options]]` with `title`/`value` |
| items | - | Array of String | Shorthand static choice list - each entry is `"value\|title"` or just `"value"`. Takes priority over `[[picker.options]]` when both are declared (the two are alternative ways to write the same static list, not additive) |
| option-sh | options-sh | String | **(shell)** Script that produces options dynamically. Each line `value\|title` or just `value` |
| multiple | - | Bool | Allow selecting multiple values |
| separator | - | String | Separator for multi-select values (default `\n`) |
| lock | lock-state | String | Static lock: `true`/`1` or the `"1\|message"` format (message accepts `@string/...`, `@key`, `{ROT}`/`{LOT}` - see §4.2) |
| lock-sh | - | String | **(shell)** Script to check lock dynamically (returns `"1"` = locked) |
| rows | - | Array | Rich-text rows shown below the item - see [section 12](#12-textrows---rich-text-row) |
| + all fields of RunnableNode, ClickableNode, NodeInfoBase |  |  |  |

### 10.2. Demo

> **Demo (Android UI)**
>
> - **Governor** — *performance*
> - **Max freq** — *2400000 kHz*

```toml
[[picker]]
title = "Governor"
get = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
set = "echo $state > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

[[picker.options]]
title = "Performance"
value = "performance"

[[picker.options]]
title = "Powersave"
value = "powersave"

[[picker]]
title = "Max freq"
get = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
set = "echo $state > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
option-sh = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies | tr ' ' '\\n'"
```

> [!WARNING]
> When a picker has both a static list (`items` or `[[picker.options]]`) and dynamic `option-sh`, the final list is the **union of both** (static options first, dynamic options appended after running the shell). `items` and `[[picker.options]]` are mutually exclusive with each other - only one is used to build the static part.

---

## 11. `[[text]]` - Rich text block

`[[text]]` is a non-clickable display block containing multiple rich-text rows. Use it for notices, instructions, or inline image/gif blocks.

### 11.1. Text fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| rows | - | Array | **(required)** Rich-text rows - see [section 12](#12-textrows---rich-text-row) |
| + NodeInfoBase fields (title, title-sh, desc, desc-sh, summary, support...) |  |  |  |

### 11.2. Demo

> **Demo (Android UI)**
>
> - **CAUTION**
> - The actions below may affect the system.
> - *— divider —*
> - `$ run backup --full`
> - *See more at: example.com/docs*

```toml
[[text]]
title = "Notice"

[[text.rows]]
text = "CAUTION"
bold = true

[[text.rows]]
text = "The actions below may affect the system."

[[text.rows]]
line = true

[[text.rows]]
text = "  $ run backup --full"
monospace = true

[[text.rows]]
text = "See more at: example.com/docs"
italic = true
```

---

## 12. `[[text.rows]]` - Rich text row

This is the **most common** display component: used in `[[text]]`, `[[action]]`, `[[page]]`, `[[download]]`, `[[action.params-rows]]` and `[[switch]]`, `[[picker]]`, `[[editor]]`. Each row is a single line of text that can have style, icon, toggle, photo, etc.

### 12.1. Row fields

| Field | Alias | Type | Default | Description |
| --- | --- | --- | --- | --- |
| text | - | String | "" | Text content |
| sh | text-sh | String | "" | **(shell)** Script that produces text dynamically |
| markdown | md | Bool | false | true: interpret `text` (or the `sh` result) as inline Markdown - bold/italic/strikethrough/color/link/code - before displaying, see [section 12.4](#12-textrows---rich-text-row) for the full syntax |
| bold | b | Bool | false | Bold |
| italic | i | Bool | false | Italic |
| underline | u | Bool | false | Underline |
| strikethrough | line-through, delete-line, del | Bool | false | Strikethrough |
| monospace | mono, code | Bool | false | Monospace font (for code/log) |
| letter-spacing | letterspacing, spacing | Float | 0 | Letter spacing (em units) |
| line-height | lineheight, row-height | Float | 0 | Line height multiplier (1.5 = +50%) |
| margin-top | spacing-top, top-margin | Int | 0 | Top margin (dp) |
| margin-bottom | spacing-bottom, bottom-margin | Int | 0 | Bottom margin (dp) |
| alpha | opacity | Float | -1 | Opacity 0.0-1.0 or 0-255 |
| foreground | color | String | -1 | Text color (e.g. `#FF0000`) |
| bg | background, bgcolor | String | -1 | Background color |
| size | - | Int | -1 | Font size (sp) |
| break | - | Bool | false | Line break after this row |
| line | divider, separator | Bool | false | Draw a horizontal divider before this row |
| align | - | String | normal | `normal`/`center`/`right`. Rows sharing the same line are grouped by `align` into up to 3 zones (left/center/right, **max 4 rows each**) and pinned accordingly - see [section 12.5](#12-textrows---rich-text-row) |
| link | href | String | "" | URL to open on click |
| activity | a, intent | String | "" | Intent to open on click |
| copy | - | Bool | false | Allow the user to long-press and select/copy text. Applies to the WHOLE `[[text.rows]]` block at once (all rows share one text view) - setting it on **any single row** turns on selection for the entire block |
| reset | - | Int\|String | -1 | Row index (0-based, within the same `[[text.rows]]` list) to refresh when THIS row is clicked - re-runs that row's `sh`/`icon-sh`/`progress-sh` only, without rebinding the whole block. Combine with `script` to run a script first, then refresh the target row. Also accepts the special values `"self"`/`"this"` (= refresh THIS row itself) and `"all"` (= rebind the whole rows list from scratch). See [section 12.6](#12-textrows---rich-text-row) |
| photo | photo-path | String | "" | Large image (own line, separate from text) |
| photo-sh | - | String | "" | **(shell)** Script that produces the row's photo path dynamically. Re-runs every time the rows are re-rendered (batched with text-sh/icon-sh, NOT cached - unlike node-level photo-sh); overrides `photo` |
| photo-real-size | photo-original-size | Bool | false | Show image at real size |
| photo-gif-num | gif-num, gif_num | Int | 0 | >0: image is animated (photo_1.png...) |
| photo-gif-time | gif-time, gif_time | Int | 300 | Time per frame (ms) |
| photo-gif-autoplay | gif-autoplay, gif_autoplay | Bool | true | Auto-play the GIF |
| photo-gif-loop | photo-gif-loop-count, gif-loop, gif-loop-count, gif_loop_count | Int | 0 | Loop count (0=infinite) |
| photo-real-gif | real-gif, photo-gif-real | Bool | false | true: row's `photo` is a REAL `.gif` file (via `AnimatedImageDrawable`), not the simulated frame-sequence GIF above. **Android 9/API 28+ only** - older devices fall back to a static first frame |
| html-file | html-path | String | "" | Local `.html` file rendered inline in its own WebView block below the row's text (resolved like `photo`: relative to the page config dir, app-private dir, assets, or an absolute path). Ignored if `html-url` is also set |
| html-url | html-link | String | "" | http/https page loaded directly into the WebView block. Takes priority over `html-file` if both are set |
| html-height | - | Int | 260 | Height of the WebView block (dp) |
| icon | icon-path | String | "" | INLINE small icon (same line, different from photo) |
| icon-sh | - | String | "" | **(shell)** Script that produces the row's inline icon path dynamically. Re-runs every time the rows are re-rendered (batched with text-sh/photo-sh, NOT cached); overrides `icon` |
| icon-position | icon-pos | String | before | `before` / `after` the text |
| icon-size | - | Int | 0 | Icon size (dp) |
| icon-gif-num | icon-gif_num | Int | 0 | >0: inline icon is animated (icon_1.png, icon_2.png...) |
| icon-gif-time | icon-gif_time | Int | 300 | Time per frame (ms) |
| icon-gif-autoplay | icon-gif_autoplay | Bool | true | Auto-play the icon animation. false: only the first frame is shown (no tap-to-play, unlike node/photo icons - inline icon has no dedicated hit area) |
| icon-gif-loop | icon-gif-loop-count, icon-gif_loop_count | Int | 0 | Loop count (0=infinite) |
| icon-real-gif | icon-gif-real | Bool | false | Same as `photo-real-gif` but for the row's inline icon - REAL `.gif` file, Android 9/API 28+ only |
| script | run | String | "" | **(shell)** Script to run when the row is clicked. Runs on a background thread (no longer blocks the UI) while a thin progress bar shows just below the toolbar; the bar disappears once the script finishes |
| toast | toast-result | Bool | false | true: show the (non-empty) result of `script`/`run` *and* a toggle's `set` as a short Toast instead of the default log dialog. Applies to both `script`'s result and a toggle's `set` result |
| toggle | toggle-type | String | "" | `checkbox`/`switch` = show a small toggle next to the row |
| get | getstate | Bool\|Shell | false | Toggle initial state. A shell returning exactly `"1"` = checked (queued into pending states - see [section 20](#20-pending-states)) |
| set | setstate | String | "" | **(shell)** Script run when the user toggles (receives `$state` = "1"/"0") |
| confirm | confirm-text, confirm-message | String | "" | Confirmation message shown before running `script` or before applying a toggle change (`set`) - "Cancel" runs nothing. "" (default) = execute immediately, no confirmation |
| refresh-interval | refresh, interval | Int | 0 | Seconds interval to automatically re-run this row's `sh`/`icon-sh`/`photo-sh` and redraw it, without waiting for the whole item to rebind (e.g. list scroll). 0 (default) = no auto refresh; when several rows declare it, the shortest interval wins |
| progress | - | Float | -1 | -1 (default) = no progress bar; any value >= 0 draws a small inline progress bar **right after the row's text/icon**, filled to `progress / progress-max` |
| progress-sh | - | String | "" | **(shell)** Script that produces the progress value dynamically (output must be a number). Re-runs on every rows render (batched with `text-sh`/`icon-sh`/`photo-sh`) and on `reset` refreshes; overrides `progress` |
| progress-max | progressmax | Float | 100 | Value that corresponds to a 100% full bar. Default `100` (so `progress` acts as a percentage) |
| progress-color | - | String | default green | Fill color of the bar (e.g. `#4CAF50`). Default: material green |
| progress-track-color | progress-bg | String | default grey | Track (background) color of the bar. Default: translucent grey |
| progress-width | - | Int | 120 | Width of the progress bar (dp) |
| progress-height | - | Int | 8 | Height of the progress bar (dp) |
| flash | - | Bool | true | When this row is refreshed via another row's `reset` and its text **actually changed**, briefly flash a background highlight that fades out - so the user notices the new value. `false` = disable the effect for this row |
| flash-color | - | String | amber | Highlight color used by the flash effect (e.g. `#FFC107`). Default: amber |
| support | visible | Bool\|Shell | true | Hide/show the row |

### 12.2. Demo

> **Demo (Android UI)**
>
> - **Device info**
> - *— divider —*
> - Model: **Pixel 7**
> - Android: **14**
> - `ro.build.fingerprint=google/raven/...`
> - *— divider —*
> - *Updated 2024-08-26* *(centered)*
> - *— divider —*

```toml
[[text]]
title = "Device info"

[[text.rows]]
text = "Device info"
bold = true

[[text.rows]]
line = true

[[text.rows]]
text = "Model: Pixel 7"

[[text.rows]]
text = "Android: 14"

[[text.rows]]
text = "ro.build.fingerprint=google/raven/..."
monospace = true

[[text.rows]]
line = true

[[text.rows]]
text = "Updated 2024-08-26"
align = "center"
italic = true

[[text.rows]]
line = true

[[text.rows]]
text = "Show notifications"
toggle = "switch"
get = "settings get global heads_up_notifications_enabled"
set = "settings put global heads_up_notifications_enabled $state"
```

> [!WARNING]
> `sh` (alias `text-sh`) of a row is NOT queued into the pending-state system - it is left to the UI layer and evaluated when the row is rendered. `get` with a shell value IS queued into `pendingRowCheckedStates` and batched at the end - like switch/picker.

### 12.3. Inline HTML block (`html-file` / `html-url`)

A row can also render a full HTML page inline, in its own `WebView` block below the text - useful for content richer than Markdown can express (custom layout, images, JS-driven widgets).

```toml
[[text.rows]]
text = "Changelog"
bold = true

[[text.rows]]
html-file = "changelog.html"
html-height = 300

[[text.rows]]
text = "Project homepage"

[[text.rows]]
html-url = "https://example.com"
```

> [!WARNING]
> Same limit as `photo`: each `[[text]]`/`[[action]]` item has only **one** HTML block - if several rows declare `html-file`/`html-url`, the **last** one wins. The WebView follows the app's light/dark theme (`force-dark`) and has a transparent background so it blends with the item - transparency only takes effect for HTML content that does not set its own CSS background color.

### 12.4. Inline Markdown syntax reference (`markdown = true`)

When a row has `markdown` (alias `md`) set to `true`, its `text`/`sh` result is parsed as simple inline Markdown before display. Marks can be escaped with `\` (e.g. `\*` shows a literal `*`).

| Syntax | Result |
| --- | --- |
| `**text**` / `__text__` | Bold |
| `*text*` / `_text_` | Italic |
| `~~text~~` | Strikethrough |
| `{text}(color)` | Custom text color - `color` is a color name (e.g. `red`) or hex (`#RRGGBB`/`#AARRGGBB`) |
| `[text](url)` | Clickable link, opens `url` |
| `` `text` `` | Rounded background box around `text` (like an inline "code" chip). The content is parsed recursively, so it can nest the other marks above - e.g. `` `{text}(red)` `` = box + red text, `` `[text](url)` `` = box + clickable link |
| `` `text\|R` `` | Same rounded box, with `R` (a plain number, dp) overriding the box's corner radius for this span only. No `\|R` suffix = default radius. Also works combined with nesting, e.g. `` `{text}(red)\|16` `` |
| `` ```text``` `` | Same rounded box as `` `text` ``, but the content is shown **literally** - Markdown marks inside are NOT interpreted (use this to display raw symbols, e.g. `` ```**not bold**``` `` shows the asterisks as-is). Also accepts the `\|R` corner-radius suffix |

```toml
[[text.rows]]
text = "Status: `{OK}(green)` - link: `[details](https://example.com)` - `big|20`"
markdown = true
```

> [!WARNING]
> Nesting only works inside single backticks (`` `...` ``) - triple backticks (`` ```...``` ``) always keep their content literal, by design, so they can be used to show raw Markdown symbols without any of them being interpreted.

### 12.5. Pinning rows to the left / center / right of the same line

Rows that would join the same line by default (no `break`/`line`/`margin-top` on a later row, no `margin-bottom` on an earlier one) are grouped, then bucketed by `align` into up to 3 zones: `normal` = left, `center` = center, `right` = right. If **each zone has at most 4 rows** and at least one row uses `center`/`right`, the group renders as a true multi-column line: left content stays flush left, center is centered across the full line width, right is flush against the right edge - regardless of how many rows end up in each zone (1 to 4). Unlike the earlier version of this feature, a pinned row can now have anything a normal row can: `icon`/`icon-sh`/`toggle`/`link`/`activity`/`script`/`markdown`/`underline`/`strikethrough` all work in any zone.

```toml
[[text.rows]]
text = "kkkkk"

[[text.rows]]
text = "ffffffg"
align = "right"
```

Up to 4 rows per zone, mixed content, three zones at once:

```toml
[[text.rows]]
text = "CPU"

[[text.rows]]
text = "68°C"

[[text.rows]]
text = "Governor"
align = "center"

[[text.rows]]
text = "Details"
align = "right"
link = "https://example.com/cpu"
```

> [!WARNING]
> If any single zone ends up with **more than 4 rows** (e.g. 5 rows all left-aligned in the same group), the whole group silently falls back to the old shared-line behavior instead: every row is simply joined left-to-right in declaration order, and `align` on the first row of the group decides the alignment of the group as a whole (no more per-zone pinning for that group). A row following a pinned group always starts on a new line, even without `break`. This layout assumes each zone's content is short enough to fit on one line - if a zone's text is long enough to wrap, the wrapped portion will not stay aligned to that zone.

### 12.6. Reset a single row on demand (`reset`)

A row with `reset = N` acts as a refresh button for row index `N` (0-based, within the same `[[text.rows]]` list): tapping it re-runs only that row's `sh`/`icon-sh`/`progress-sh` and updates just that row's text, without rebinding the whole block (icons/GIFs/other rows are left untouched). Combine with `script` to run a script first and then refresh the row that displays its result.

```toml
[[text.rows]]
sh = "cat /sys/class/thermal/thermal_zone0/temp"
icon = "ic_temperature"

[[text.rows]]
text = "🔄 Refresh"
align = "right"
reset = 0
```

Besides a numeric index, `reset` also accepts two special string values:

| Value | Behavior |
| --- | --- |
| `reset = "self"` (or `"this"`) | Refreshing THIS row itself - re-runs its own `sh`/`icon-sh`/`progress-sh` in place. A row can therefore be *both* the data source and its own refresh button (with `script` running first) |
| `reset = "all"` | Rebinds the ENTIRE `[[text.rows]]` list from scratch - equivalent to a full block re-render (all rows' dynamic scripts re-run) |

> [!WARNING]
> If the target row happens to be part of a group using the multi-column pin layout from [section 12.5](#12-textrows---rich-text-row), `reset` automatically rebinds that whole block instead of just the one row, to avoid breaking the column spacing - this is expected, not a bug.

### 12.7. Inline progress bar (`progress`) and flash highlight (`flash`)

A row can embed a small horizontal progress bar **inline, right after its text/icon** - useful for battery/thermal/storage gauges without leaving the text flow. The bar is driven either statically or by a script:

```toml
[[text]]
title = "Storage"

[[text.rows]]
sh = "df -h /data | tail -1 | awk '{print $5}'"
text = "/data usage"
progress-sh = "df /data | tail -1 | awk '{gsub(\"%\",\"\"); print $5}'"
progress-max = 100
progress-color = "#FF9800"
progress-track-color = "#33888888"
progress-width = 120
progress-height = 8

[[text.rows]]
text = "🔄 Refresh"
align = "right"
reset = "all"
```

> [!TIP]
> Like `text-sh`/`icon-sh`/`photo-sh` of a row, `progress-sh` is re-evaluated on every rows render (batched into one shell round-trip) and by `refresh-interval` timers - the bar updates live together with the row's text. The output must be a plain number; when combined with `refresh-interval` + `reset` you get a self-updating gauge.

The companion `flash` effect makes a reset-driven value change visible: whenever a row is updated through `reset` and its rendered text actually differs from before, a highlight rectangle is drawn behind the new text and fades out over a short animation. It is **on by default** - set `flash = false` on a row to opt out, or `flash-color` to use a custom color instead of the default amber.

---

## 13. `[[editor]]` - Open file in the text editor

`[[editor]]` lets the user open a file in the built-in text editor (`TextEditorActivity`) to view or edit it. If the file does not exist, the editor creates it when the user saves.

### 13.1. Editor fields

| Field | Alias | Type | Default | Description |
| --- | --- | --- | --- | --- |
| file | path | String | "" | **(required)** Path of the file to open |
| wrap | - | Bool | true | Enable line wrapping |
| placeholder | - | String | null | Hint when file is empty |
| readonly | - | Bool\|Shell | false | Read-only. Accepts shell too! |
| need-input | - | Bool | false | Whether the run script uses `read` |
| value | - | String | "" | Initial content (only when file does not exist) |
| value-sh | - | String | "" | **(shell)** Script that produces initial content (higher priority than `value`) |
| rows | - | Array | [] | Rich-text rows shown below the item - see [section 12](#12-textrows---rich-text-row) |
| + ClickableNode fields (icon, lock, min-sdk...) and NodeInfoBase (title, desc...) |  |  |  |  |

### 13.2. Demo

> **Demo (Android UI)**
>
> - **Edit build.prop** — /system/build.prop
> - **Create new script** — /sdcard/myscript.sh

```toml
[[editor]]
title = "Edit build.prop"
desc = "/system/build.prop"
file = "/system/build.prop"
readonly = "test -w /system/build.prop || echo 1"

[[editor]]
title = "Create new script"
desc = "/sdcard/myscript.sh"
file = "/sdcard/myscript.sh"
value = "#!/system/bin/sh\necho hello"
```

> [!WARNING]
> `value` is only written when the file **does not exist**. If the file already exists, `value`/`value-sh` are ignored - the existing file content is preserved.

---

## 14. `[[download]]` - Download a file via HTTP

`[[download]]` is a new node type that displays download progress **directly in the item** (no separate dialog). When download completes, it runs the `script` with the env var `$state` = path of the downloaded file (cached, random name). The URL can be static (`url`) and/or dynamic (`url-sh`).

### 14.1. Download fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| url | - | String | Static URL (http/https) to download. Required if `url-sh` is not set |
| url-sh | - | String | **(shell)** Script that returns the URL to download. Runs **once**, the first time the item is tapped - the result is cached into `url` for the rest of that page visit (pause/resume/retry reuse the cached value, the script is not re-run). Required if `url` is not set; if both are set, the result of `url-sh` overwrites `url` on that first tap |
| script | set, setstate | String | Script run after download. Receives `$state` = path of downloaded file |
| lock | lock-state | String | Static lock: `true`/`1` or the `"1\|message"` format (message accepts `@string/...`, `@key`, `{ROT}`/`{LOT}` - see §4.2) |
| lock-sh | - | String | **(shell)** Script to check lock dynamically (returns `"1"` = locked) |
| rows | - | Array | Rich-text rows shown below the item - see [section 12](#12-textrows---rich-text-row) |
| + all RunnableNode fields: confirm, warn, reload, auto-finish... |  |  |  |

### 14.2. Demo

> **Demo (Android UI)**
>
> - **Download update** — update.zip - 24 MB / 53 MB · 45%

```toml
[[download]]
title = "Download update"
url = "https://example.com/update.zip"
script = "unzip -o $state -d /sdcard/update && echo Update installed"
```

---

## 15. `[[resource]]` - Extract assets

`[[resource]]` is an **invisible** node (it returns `null` after initialization). It only extracts assets from the APK to storage (typically `/data/data/<pkg>/files/...` or cache). This is how to ship script files, images, etc. from the APK to outside so shell can call them.

You can also put `resource-file`/`resource-dir` fields directly inside `[[page]]`/`[[action]]`/`[[switch]]`/`[[picker]]`/`[[text]]` - no need to declare a separate `[[resource]]` entry. (Not available on `[[group]]`.)

### 15.1. Fields

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| resource-file | - | String | Name of a single asset file to extract |
| resource-dir | - | String | Name of an asset directory to extract entirely |
| resources | - | Array | List of more complex resources - each entry is a table with `file` and/or `dir` |

```toml
[[resource]]
resource-file = "busybox"

[[resource]]
resource-dir = "scripts"

# Or inline on the node that needs it - no separate [[resource]] entry required:
[[action]]
title = "Run bundled script"
resource-file = "run.sh"
script = "sh /data/data/com.tool.tree/files/run.sh"
```

> [!TIP]
> Extraction is de-duplicated in memory (per app process): each asset name is written only once, repeated declarations reuse the first extracted path. Note that for a node carrying `resource-*` fields, `support = false` skips the whole node - its resources are not extracted either.

---

## 16. `[[menu]]` / `[[fab]]` - Overflow menu and FAB

This feature **replaces** the old `[[page.options]]` mechanism (declared in the parent page, built eagerly even when the sub-page was never opened). Now the menu and FAB are declared **directly inside the page's own TOML file**, just like `[[action]]`/`[[text]]` - they are only read (and only their inner shell runs) when the page is actually opened.

### 16.1. Structure

A `[[menu]]` or `[[fab]]` block is a **container**, NOT a single entry:

- `handler` (or `handler-sh`): the DEFAULT script that runs when clicking a child item, if that item does not declare its own `script`
- `icon` (or `icon-path`): custom icon for the container itself (NOT an item) - see below
- `[[...items]]`: the list of items shown in the menu/fab - each item uses the same fields as the old `[[page.options]]` (type/style/suffix/mime/path-home/multiple/get/silent/link/activity/html/config/config-sh/script/title/icon...)

> [!TIP]
> `icon`/`icon-path` on the container itself: On `[[menu]]`: replaces the default 3-dot toolbar icon. Always **force-tinted** to the toolbar icon color (same tint applied to every custom icon in the popup list) - the image itself can be any color/shape, it will be recolored to match the theme. On `[[fab]]`: default icon for the floating button, used when the (single) item does not set its own `icon`. If `[[fab]]` has 2+ items, this is the icon shown before the item picker opens (each item's own icon is only used when there is exactly 1 item). NOT tinted - drawn as-is like a normal FAB icon. If `[[menu]]`/`[[fab]]` is declared more than once on the same page, only the **first** non-empty `icon` found is used.

### 16.2. Item fields (inside menu/fab)

| Field | Alias | Type | Description |
| --- | --- | --- | --- |
| title | text | String | Title |
| key | index, id | String | Item ID. Passed to the item's script as `$state` and `$menu_id` when clicked (spinner items get `$state` = the chosen value instead, file items also get `$file`/`$folder` = the picked path). If omitted, an auto key is derived from the title (or `"menu"`/`"fab"` when the title is empty); auto keys that would collide get a `#menu2`/`#fab2`... suffix so two items never share one. Explicitly declared keys are kept as-is even when duplicated |
| type | - | String | `refresh`/`reload` (rerun the page), `restart` (restart the app), `finish`/`exit`/`close` (close the page), `killapp` (kill the app process), `file`/`folder`, `spinner`, `checkbox` |
| style | - | String | Set to `fab` to render this item as a floating action button (useful inside `[[menu]]`) |
| script | set, setstate | String | Script to run on click. If empty, uses the group's `handler` |
| get | getstate | String | **(shell)** (type=checkbox) script that decides the tick; (type=spinner) script that reads the current selection for highlighting. Re-run every time the menu opens |
| silent | hidden | Bool | true: run silently in background (no log) |
| link | href | String | URL |
| activity | a, intent | String | Intent |
| html | - | String | WebView URL |
| config | - | String | Open sub-page (.toml) |
| config-sh | - | String | Script returning inline toml |
| suffix | - | String | File extension filter (type=file) |
| mime | - | String | MIME filter |
| path-home | home-path, pathhome | String | Initial directory (file picker) |
| multiple | - | Bool | Allow multiple file selection |
| options | - | Array | (type=spinner) Choice list |
| items | - | Array of String | (type=spinner) Shorthand choice list - each entry is `"value\|title"` or just `"value"`. Takes priority over `options` when both are declared |
| option-sh | options-sh | String | **(shell)** Script that produces options. Each line `value\|title` or just `value` |
| + RunnableNode, ClickableNode, NodeInfoBase fields |  |  |  |

### 16.3. Demo

> **Demo (Android UI)**
>
> - **Performance mode** — switch: **ON** — Enable performance governor
> - **Clear cache** — Free up temporary files
> - Refresh
> - ✓ Show module
> - Select governor ▾
> - View log
> - Open in browser
> - **FAB (floating button):** +

> [!TIP]
> `[[menu]]`/`[[fab]]` do NOT appear in the page's content list - they only show on the toolbar or as a floating button. The parser merges all items from `[[menu]]`/`[[fab]]` blocks (declared anywhere - top-level or inside a group) into **one shared list** for the whole page.

```toml
# Regular page content
[[switch]]
title = "Performance mode"
desc = "Enable performance governor"
get = "getprop sys.perf.mode"
set = "setprop sys.perf.mode $state"

[[action]]
title = "Clear cache"
script = "sync; echo 3 > /proc/sys/vm/drop_caches"

# Toolbar overflow menu (3-dot icon) - not part of the page content list above
[[menu]]
handler = "echo Menu: $key"
icon = "menu_icon.png"

[[menu.items]]
title = "Refresh"
type = "refresh"

[[menu.items]]
title = "Show module"
type = "checkbox"
get = "test -f /data/adb/modules/my_module/disable || echo 1"
script = "touch /data/adb/modules/my_module/disable"

[[menu.items]]
title = "Select governor"
type = "spinner"
options = ["performance|Performance", "powersave|Power saving", "schedutil|Balanced"]
script = "echo $state > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

[[menu.items]]
title = "View log"
config = "logcat.toml"

[[menu.items]]
title = "Open in browser"
link = "https://example.com"

# Floating action button - also not part of the page content list
[[fab]]
icon = "fab_icon.png"
[[fab.items]]
title = "Add rule"
type = "file"
suffix = "toml"
script = "sh $state"
```

---

## 17. Dependencies (depend-*)

This is the **largest feature** of `[[action.params]]`: a param can be hidden/shown (or switched to *readonly*) based on the value of one (or more) other params in the same action. This is how to build complex dialogs that are still easy to manage - the UI auto-hides irrelevant fields when the user picks a different mode.

### 17.1. depend-* fields (only for `[[action.params]]`)

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| depend-on | String | null | Name(s) of parent param(s), separated by `\|`. E.g. `"mode\|cam"`. Alias: `depend` |
| depend-value | String | null | Required matching value for each parent. Parents separated by `\|`; within one parent: accepted values OR-separated by comma `,`. E.g. `"a\|b,c"` |
| depend-mode | String | "show" | `"show"`: show when matched (default). `"hide"`: hide when matched. Can be declared per-parent, separated by `\|`: `"show\|hide"` |
| depend-logic | String | "and" | How to combine multiple parents: `and` / `priority` (= or LTR) / `priority-rtl` (= or RTL) / `xor` / `nand`. Alias: `depend-priority` |
| depend-default | String | "show" | Default value when NO condition matches: `"show"` or `"hide"` |
| depend-initial | String | "auto" | Initial state before any evaluation: `"auto"` / `"show"` / `"hide"`. Alias: `depend-initial-state` |
| depend-negate | Bool | false | Invert ALL conditions (NOT logic) |
| depend-threshold | Int | -1 | Only for `and`: % of conditions that must match (0-100). E.g. `67` = at least 2/3 |
| depend-include-hidden | Bool | true | true: hidden param still included in result. false: skip hidden param |
| depend-cascade | Bool | true | true: parent hidden => child hidden too. false: only visible parents are used |
| depend-onchange | String | null | Shell callback name run when this param's hide/show state changes. Aliases: `depend-on-change`, `depend-callback` |
| depend-readonly | Bool | false | true: don't hide - just *dim* and lock interaction |
| depend-sort | Bool | false | true: move locked params to the bottom (only effective when depend-readonly=true) |

### 17.2. Demo - simple dependency

> **Demo - hidden param**
>
> - **Backup mode** · Single file
> - **Source file** — /sdcard/file.zip
> - **Destination folder** — (hidden because mode != "folder")

```toml
[[action]]
title = "Backup"
script = """
if [ "$mode" = "file" ]; then
  cp "$src_file" /sdcard/backup/
else
  cp -r "$dst_folder" /sdcard/backup/
fi
"""

[[action.params]]
name = "mode"
title = "Backup mode"
type = "spinner"
value = "file"

[[action.params.options]]
title = "Single file"
value = "file"

[[action.params.options]]
title = "Whole folder"
value = "folder"

[[action.params]]
name = "src_file"
title = "Source file"
type = "file"
depend-on = "mode"
depend-value = "file"

[[action.params]]
name = "dst_folder"
title = "Destination folder"
type = "folder"
depend-on = "mode"
depend-value = "folder"
```

> [!WARNING]
> `depend-sort = true` only works when `depend-readonly = true`. If you declare `depend-sort = true` without `depend-readonly`, the parser forces it to **false** - because sort only makes sense for items "locked in place" (still visible, just dimmed), not for fully hidden items (View.GONE has no slot to "move down").

### 17.3. depend-logic reference

| Logic | Meaning | Example |
| --- | --- | --- |
| `and` (default) | ALL conditions must match | `depend-on="a\|b"` => both a and b must match depend-value |
| `priority` | left-to-right, first matching condition wins | If a matches => result follows a's mode; if a doesn't match, check b... |
| `priority-rtl` | right-to-left, opposite of priority | If b matches before a |
| `xor` | EXACTLY ONE condition must match | a matches OR b matches, not both |
| `nand` | negation of and | NOT all matching => true |

### 17.4. depend-default and depend-initial

By default, when no condition matches, the param is `show`n. To change the default to hidden:

> [!TIP]
> `depend-initial = "auto"` (default) auto-determines based on `depend-default`. Use `"show"`/`"hide"` when you want to avoid "flicker" when the dialog first opens - the UI will pin to that state until evaluation completes.

---

## 18. Shell-script fields - overview

In Tool-Tree, many fields accept either a **static value** (string/bool) or a **dynamic shell script**. The parser runs the script and uses its output as the value. Here is a summary of all shell-style fields:

| Field | Applies to | Description |
| --- | --- | --- |
| title-sh | Node, param, option | Produce title dynamically |
| desc-sh | Node, param | Produce desc dynamically |
| summary-sh | Node | Produce summary dynamically |
| warn-sh / warning-sh | RunnableNode | Produce warning dynamically |
| label-sh | ActionParamInfo | Produce label dynamically |
| placeholder-sh | ActionParamInfo | Produce placeholder dynamically |
| desc-on-sh | ActionParamInfo (type=bool) | Produce desc-on dynamically |
| value-sh | ActionParamInfo, EditorNode | Fetch current value |
| options-sh / option-sh | PickerNode, ActionParamInfo, PageMenuOption | Produce options list dynamically |
| get / getstate | SwitchNode, PickerNode, PageMenuOption (checkbox/spinner), TextRow (toggle) | Read current state (batched via pending states at parse time) |
| set / setstate / script | ActionNode, SwitchNode, PickerNode, DownloadNode, PageMenuOption, TextRow (toggle) | Set new state on user interaction |
| lock / lock-state | ClickableNode, PageNode, PageMenuOption | Check lock state (static, or "state\|message" format) |
| lock-sh | ClickableNode (page, action, switch, picker, download, editor) | Check lock state dynamically (returns "1" = locked) |
| icon-sh | ClickableNode (page, action, switch, picker, download, editor) | Produce icon path dynamically - runs during parse, batched with title-sh/desc-sh/summary-sh |
| photo-sh | ClickableNode (page, action, switch, picker, download, editor) | Produce photo path dynamically - runs during parse, batched with the other node-level -sh fields |
| bg-sh | ClickableNode (page, action, switch, picker, download, editor) | Produce background image path dynamically - runs during parse, batched |
| icon-sh (row) | TextRow | Produce row's inline icon path dynamically - re-runs every render (batched with text-sh/photo-sh of the row, not cached) |
| photo-sh (row) | TextRow | Produce row's photo path dynamically - re-runs every render (batched, not cached) |
| progress-sh (row) | TextRow | Produce the row's inline progress-bar value dynamically (a number) - re-runs every render (batched with text-sh/icon-sh/photo-sh) |
| url-sh | DownloadNode | Produce download URL dynamically - runs once on first tap, cached into `url` for the rest of that page visit |
| support / visible | All nodes, row, param | Hide/show (accepts shell too) |

---

## 19. resolveBoolOrShell

The `resolveBoolOrShell()` function parses fields that can take either a static boolean or a shell command. If the string evaluates to `"true"` or `"1"`, it resolves to `true`. For non-boolean strings, it executes the script immediately via the root shell during parsing; if the command output equals `"1"`, it returns `true`, otherwise `false`.

---

## 20. Pending states

To maximize performance when parsing large configuration files, the parser does not run every dynamic shell evaluation synchronously. Six queues collect the deferred work while parsing:

| Queue | Collected from | Result applied to |
| --- | --- | --- |
| `pendingSwitchStates` | switch `get` | `checked` = true when output is "1"/"true" (and not "error") |
| `pendingPickerStates` | picker `get` | current `value` |
| `pendingRowCheckedStates` | row `get` (toggle) | row `checked` = (output trimmed == "1") |
| `pendingRowVisibleStates` | row `support` (non-boolean value) | row removed from its list when output != "1" |
| `pendingDynamicStrings` | `title-sh`/`desc-sh`/`summary-sh` of nodes, `warn-sh` of runnable nodes, `title-sh` of picker/param options | replaces the static string |
| `pendingCheckboxStates` | `get` of `[[menu]]`/`[[fab]]` checkbox items | item `checked` = true when output is "1"/"true" (and not "error") - so the tick is correct immediately at page load, before the async refresh pass runs |

After the whole document is parsed, `resolvePendingStates()` submits ALL collected scripts in a single batch via `ScriptEnvironmen.executeMultipleResultRoot()` - one shell round-trip instead of N - and then distributes the results to each node before the page is displayed.

---

## 21. process = true

Setting `process = true` on a `[[page]]` instructs the rendering engine to build and render child nodes progressively on the UI as they are parsed, accompanied by a linear progress indicator, rather than holding display until the entire tree has loaded. Note that a `[[group]]` is emitted to the UI only after ALL of its children have been collected (group + children appear together), while items outside any group are emitted one by one as soon as they are parsed.

While the first real item is still being built, the page shows `placeholder-count` shimmering skeleton cards up-front so it doesn't look empty (default: `1`). As soon as the FIRST real item finishes loading, the skeleton(s) are hidden immediately - they no longer wait for the whole page to finish loading.

```toml
[[page]]
title = "Kernel tweaks"
config = "kernel.toml"
process = true
placeholder-count = 3
```

---

## 22. Page lifecycle

Sub-pages trigger lifecycle callbacks during initialization and reading. `before-load` executes before parsing begins, followed by `after-load` upon parsing completion. Dependent on the parse status, `load-ok` or `load-fail` shell scripts execute accordingly.

---

## 23. Script output control sequences

While a `script` (from `[[action]]`, `[[switch]]`, `[[download]]` ...) is running, its log dialog is not just a plain text viewer. If a running script `echo`s a line matching one of the special tags below, the log dialog intercepts that line (it is **not** printed as normal output) and performs a UI action instead. This lets a shell script drive dialogs, prompts, progress bars, and even control the app process itself, without leaving the log screen.

> [!TIP]
> All tags follow the same pattern: `tagname:[content]`, printed on its own line via `echo`. They only work while the log dialog is open and reading the script's live output.

### 23.1. exit:[kill] / exit:[restart]

Terminates the app process from inside a running script. `exit:[kill]` simply kills the app process; `exit:[restart]` relaunches the app first and then kills the old process, giving the effect of a full app restart.

```toml
[[action]]
title = "Apply and restart app"
confirm = true
warn = "The app will restart to apply changes"
script = """
setprop persist.sys.my_tweak 1
echo "exit:[restart]"
"""
```

> [!WARNING]
> **Note:** everything printed by the script *after* the `exit:[...]` line is never processed - the process is torn down as soon as the tag is detected.

### 23.2. choose:[value1|Label1,value2|Label2,...]

Pauses the script and renders a row of buttons directly in the log dialog. Each option is `value|Label` (the `|Label` part is optional - if omitted, the value itself is used as the label). When the user taps one, its `value` is written back to the script's standard input, and the script resumes.

```toml
[[action]]
title = "Set governor"
script = """
echo "choose:[performance|Performance,powersave|Power saving,schedutil|Balanced]"
read governor
echo "userspace" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null
for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
  echo "$governor" > "$cpu"
done
echo "Governor set to: $governor"
"""
```

### 23.3. pick:[values] / pickv:[values] / pickh:[values]

Same idea as `choose:[...]` (comma-separated `value|Label` list, sent back via stdin on tap), but rendered as tappable links inline in the log text rather than as separate buttons. `pickv:[...]` (or plain `pick:[...]`) stacks the choices vertically; `pickh:[...]` lays them out horizontally.

```toml
[[action]]
title = "Choose log level"
script = """
echo "Select a verbosity level:"
echo "pickh:[1|Low,2|Medium,3|High]"
read level
echo "Verbosity set to level $level"
"""
```

### 23.4. input:[prompt]

Shows a text input field in the log dialog with `prompt` as its hint. Whatever the user types and submits is written back to the script's standard input, letting the script keep going with free-form text instead of a fixed list of choices.

```toml
[[action]]
title = "Rename backup"
script = """
echo "input:[Enter a name for this backup]"
read name
mv /sdcard/backup/last.zip "/sdcard/backup/${name}.zip"
echo "Saved as ${name}.zip"
"""
```

### 23.5. progress:[current/total]

Drives the progress bar shown at the top of the log dialog. Echo this repeatedly while a long-running task advances. `current = -1` switches the bar to indeterminate (spinning) mode; `current >= total` hides the bar (task finished).

```toml
[[action]]
title = "Copy large folder"
script = """
echo "progress:[-1/100]"
total=$(ls /sdcard/source | wc -l)
i=0
for f in /sdcard/source/*; do
  cp "$f" /sdcard/target/
  i=$((i+1))
  echo "progress:[$i/$total]"
done
echo "progress:[$total/$total]"
echo "Done: $i files copied"
"""
```

### 23.6. am:[...] (send an Android Intent)

Sends an Android Intent directly from a shell script, without needing a separate `am start` binary call wired up elsewhere. Supported sub-commands are `start`, `startservice`, `foregroundservice`, and `broadcast`, using the same `-a`/`-d`/`-n` flags as the standard Android `am` command line tool, plus typed extras.

| Extra flag | Type |
| --- | --- |
| `--es key value` | String |
| `--ei key value` | Int |
| `--ez key value` | Boolean |
| `--el key value` | Long |
| `--ef key value` | Float |
| `--ed key value` | Double |
| `--eu key value` | Uri |
| `--esa key v1 v2` | String[] |
| `--eia key v1 v2` | Int[] |

```toml
[[action]]
title = "Open developer options"
script = """
echo "am:[start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS]"
"""

[[action]]
title = "Broadcast a custom event"
script = """
echo "am:[broadcast -a com.example.MY_ACTION --es status ok --ei code 200]"
"""
```

> [!TIP]
> Echo `am:[help]` from any script to print the full command/extras syntax straight into the log dialog - handy for testing without leaving the app.

---

## 24. Full example

A representative TOML configuration showcasing groups, actions, parameters, switches, and rich text rows:

```toml
# Top-level item (declared before the first [[group]]) - shown without a group header
[[text]]
title = "NOTICE"

[[text.rows]]
text = "Every entry below belongs to the nearest [[group]] above it"
italic = true

[[group]]
title = "System Performance"

[[switch]]
title = "Performance Mode"
desc = "Enable performance governor"
get = "getprop sys.perf.mode"
set = "setprop sys.perf.mode $state"
lock-sh = "[ -f /data/adb/.perf-ready ] && echo 1"

[[action]]
title = "Set CPU Frequency"
script = "echo $freq > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
reload = "perf-mode"

[[action.params]]
name = "freq"
title = "Frequency (kHz)"
type = "spinner"

[[action.params.options]]
title = "Mode 1"
value = "1800000"

[[action.params.options]]
title = "Mode 2"
value = "2200000"

[[action.rows]]
text = "Applies to CPU0 only"
italic = true

[[group]]
title = "Maintenance"

[[action]]
title = "Clear cache"
confirm = true
warn = "Cache will be wiped!"
script = "sync; echo 3 > /proc/sys/vm/drop_caches"
auto-off = true

# Page menu (3-dot) and FAB - collected wherever they appear
[[menu]]
handler = "echo Menu: $key"

[[menu.items]]
title = "Refresh"
type = "refresh"

[[menu.items]]
title = "Performance Mode"
type = "checkbox"
get = "getprop sys.perf.mode"
script = "setprop sys.perf.mode $state"

[[fab]]
[[fab.items]]
title = "Flash zip"
type = "file"
suffix = "zip"
script = "sh $state"
```

---

## 25. Tips & pitfalls

- Always use double brackets `[[node]]` across all declarations to prevent TOML table array parser exceptions.
- Groups are flat - never use dotted `[[group.action]]` nesting (those children are silently ignored). Declare children flat, right after their `[[group]]`.
- `[[toml]]` is a reserved marker for inline-TOML detection (`config-sh`) - do not use it as a node table name.
- Use selective refresh via `reload = "id1,id2"` instead of reloading the entire page after action execution.

---

## 26. Changelog - recently added fields

Fields marked with the badge throughout this guide are summarized here, grouped by section, for quick reference when upgrading an existing config.

*(No entries yet - newly added fields will be listed here going forward.)*
