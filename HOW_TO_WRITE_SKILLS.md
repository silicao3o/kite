Here’s an **updated and comprehensive Markdown guide** for writing Claude Skills that incorporates the official Claude Code docs, the *anthropics/skills* repository’s stance on what Skills are, and the Claude.com *Introducing Agent Skills* blog (October 16 2025) about the architecture, purpose, cross-platform availability, and best practices. The result below is ready to re-use as a team guide or SKILL authoring standard.

---

```md
# How to Write Claude Skills (Markdown Guide)

This document explains how to build high-quality **Claude Skills** — reusable, discoverable, and contextually triggered modules that extend Claude’s capabilities across *Claude Code*, *Claude.ai*, and the *Claude API* environment. It incorporates:
- the **Claude Code Skills** authoring docs (SKILL.md format, discovery, tooling) :contentReference[oaicite:0]{index=0}
- the **anthropics/skills** repository description of Skills (folders of instructions & scripts) :contentReference[oaicite:1]{index=1}
- the **Claude.com Agent Skills blog** overview of cross-platform operation, goals, and architecture :contentReference[oaicite:2]{index=2}

> **Goal of this guide:** help you write Skills that trigger reliably, scale well, and follow best practices.

---

## What Is a Claude Skill

A **Skill** is a standardized folder that contains:
1. a required `SKILL.md` file with metadata and instructions,
2. optional supporting documentation (e.g., reference or examples),
3. optional executable scripts (Python, Bash, Node, etc.). :contentReference[oaicite:3]{index=3}

Claude:
- **discovers Skills** by pre-loading each Skill’s `name` and `description` at startup; :contentReference[oaicite:4]{index=4}
- **activates Skills** when a user request matches its description; :contentReference[oaicite:5]{index=5}
- **executes Skills** by following the instructions and optionally launching referenced scripts or linked files. :contentReference[oaicite:6]{index=6}

Skills work across Claude: *Claude Code*, *Claude.ai*, *API*, and *Agent SDK*. :contentReference[oaicite:7]{index=7}

---

## Folder Structure

Minimum structure:

```

my-skill/
└── SKILL.md

```

Recommended (expandable) structure:

```

my-skill/
├── SKILL.md
├── reference.md       # detailed docs (loaded only when needed)
├── examples.md        # usage examples
└── scripts/
└── something.py   # utility scripts, executed not loaded

**Why structure matters:** Claude only preloads minimal metadata; everything else is discovered or loaded *on demand* (progressive disclosure). :contentReference[oaicite:9]{index=9}

---

## `SKILL.md`: The Core File

A Skill’s behavior and trigger are defined by a single Markdown file with:

1. **YAML frontmatter** (`name`, `description`, and optional fields), and
2. **Markdown instructions** Claude follows when the Skill is active. :contentReference[oaicite:10]{index=10}

### Minimal Template

```yaml
---
name: your-skill-name
description: >
  What this Skill does + when to use it. Include natural language trigger phrases.
---
# Your Skill Name

## What this Skill Does
- Summarize the outcomes this Skill produces.

## When to Use
- Language users might say that should trigger this Skill.

## Rules / Workflow
1. Step 1
2. Step 2
3. Step 3

## Examples
### User
User: "..."
### Assistant
Assistant: "..."
```

---

## Frontmatter Reference

| Field                      | Required | Purpose                                                                              |                    |
| -------------------------- | -------- | ------------------------------------------------------------------------------------ | ------------------ |
| `name`                     | Yes      | Identifier; lowercase letters, numbers, hyphens only. Should match directory.        |                    |
| `description`              | Yes      | Natural language plus trigger keywords; used for automatic discovery and activation. |                    |
| `allowed-tools`            | No       | Limits what tools Claude can use while this Skill is active.                         |                    |
| `model`                    | No       | Force a specific model execution context.                                            |                    |
| `context`                  | No       | `fork` isolates execution in a sub-agent context.                                    |                    |
| `agent`                    | No       | If forked, which agent type to use.                                                  |                    |
| `hooks`                    | No       | Lifecycle hooks (`PreToolUse`, `PostToolUse`, etc.).                                 |                    |
| `user-invocable`           | No       | Toggle visibility in slash command menus.                                            |                    |
| `disable-model-invocation` | No       | Blocks programmatic invocation.                                                      | ([Claude Code][1]) |

---

## Writing an Effective Description (Trigger)

The `description` field *is not just metadata* — Claude reads it to decide **when** to activate your Skill. To increase matching reliability:

* use **verbs + outcomes** (“analyze financial data, generate charts”)
* include **natural search phrases users might say** (“when user mentions quarterly metrics or earnings”)
* avoid vague language (“help with data”). ([Claude][2])

Example:

```yaml
description: >
  Analyze CSV datasets and produce
  summary reports with charts and tables.
  Use when the user uploads a .csv or asks
  for summary metrics and visualization.
```

---

## Guidelines & Best Practices

### Naming

* consistent, descriptive, and follow the lowercase hyphenated convention;
* consider using **gerund form** (`analyzing-spreadsheets`, `formatting-decks`) to convey capability. ([Claude][2])

### Progressive Disclosure

Keep `SKILL.md` concise — put heavy docs in separate `.md` files and link them. Claude will load them *when needed*, reducing context usage. ([Claude][3])

### Scripts

If your Skill needs programmatic logic:

* include scripts in `scripts/`,
* reference them from instructions with execution commands (e.g., `python scripts/transform.py input.csv`). ([Claude Code][1])

⚠️ **Security Reminder:** Script execution gives Claude powerful capabilities; use trusted code only. ([Claude][3])

### Examples

Include real examples of input and expected output in markdown. This helps Claude and users understand behavior.

### Testing & Validation

* use the slash command to list available Skills,
* refine the description until Claude triggers your Skill reliably,
* test across use cases. ([Claude Code][1])

---

## Deployment & Distribution

Where you place Skills determines scope:

* **Personal Skills:** `~/.claude/skills/` — your personal environment.
* **Project Skills:** `.claude/skills/` in a repo — shared with collaborators.
* **Plugin Skills:** bundled in plugin packaging — shared via plugin ecosystem.
* **Managed Org Skills:** centrally deployed for enterprise use. ([Claude Code][1])

---

## Common Pitfalls

* **Skill never triggers:** description too broad or lacking trigger keywords.
* **Skill loads incorrectly:** invalid YAML frontmatter (must start with `---`).
* **Sluggish behavior:** too much content in `SKILL.md` (use progressive disclosure). ([Claude Code][1])

---

## Quick Skill Checklist

* [ ] Folder named appropriately and matches `name`.
* [ ] Clear, specific `description` with trigger phrases.
* [ ] Step-by-step instructions and examples.
* [ ] Scripts referenced clearly.
* [ ] Supporting docs in separate files.
* [ ] Tested for triggering reliability.

---

## Summary

Claude Skills let you **teach Claude repeatable workflows** and domain knowledge that apply whenever relevant — no manual slash commands required. Skills are **portable, composable, and efficient** by design, and should be authored with a focus on **clarity, triggers, and maintainability**. ([Claude][3])

```

[1]: https://code.claude.com/docs/en/skills "Agent Skills - Claude Code Docs"
[2]: https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices "Skill authoring best practices - Claude Docs"
[3]: https://claude.com/blog/skills "Introducing Agent Skills | Claude"

