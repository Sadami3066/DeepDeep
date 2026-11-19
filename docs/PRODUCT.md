# Product principles

Super PI Agent is a personal agent that runs on Android. It combines a conversational assistant, a local Linux environment, device control, and an extensible plugin system in one app. It works without a server: model requests go to the provider you configure, and conversations, files, credentials, and plugin data stay on the device.

This document records the product constraints that all surfaces share. Architecture and implementation details live in [ARCHITECTURE.md](ARCHITECTURE.md).

## One entry, one job

- Every entry point has a single purpose. When two entries lead to the same outcome, keep one.
- Do not add a setting, explanation, or shortcut only to expose an implementation detail. Remove options that do not change what the user can do.
- Navigation follows the real task: where the user is, what happens next, and how to recover. Do not add links just because a screen exists.

## Default to less

- Start with the smallest interface that completes the task. Progressive disclosure is preferred over a wall of controls.
- Settings use user language, not internal terms. If a choice needs implementation context to make sense, it does not belong in the product.
- Development-facing text — debug hints, mechanism descriptions, migration notes, internal identifiers — never appears in the interface.

## Feedback is continuous

- State appears before the result: pressed, running, waiting, done, failed.
- An action in progress cannot be triggered twice.
- Failures are explained where they happen and can be retried. A failure that cannot be retried says what the user can do instead.
- Empty, loading, error, and permission states are designed states, not leftovers.

## Conversation and tools

- The conversation is the primary surface. Text, attachments, voice, images, and files all enter the same thread.
- The process panel shows what the agent did: tool name, input, state, and output. It stays readable while running and folds into a summary when finished.
- Rich cards belong in the answer area only. They do not replace or nest inside the tool process panel.
- Cards are produced by plugin tools. Plugins that expose command-line tools return plain text and let the model answer in prose; they do not manufacture cards.
- The same plugin behavior uses the same rendering pipeline in the host. Cross-plugin problems are fixed in the pipeline, not with per-plugin patches.

## Bots and context

- A Bot is a workspace with its own profile, instructions, capabilities, and sessions. Switching Bots does not change the state of another Bot.
- Capabilities such as skills, plugins, MCP connectors, screen tools, and miniapp tools are resolved for the active Bot when a tool runs, not captured when a session is created.
- Conversation history, pinned sessions, archives, and titles are projections of the session log. The log is the single source of truth.

## System access and boundaries

- System access has three levels: normal, wireless debugging, and root. The level only degrades; it never upgrades without the user's choice.
- The Linux environment is a user-space proot on normal and wireless-debugging levels, and a native root environment on the root level. It is not a security sandbox.
- Plugins run in the app process with the host's permissions. Installing a plugin is an act of trust; only install plugins from sources you trust.
- The app has no backend for conversations. Data leaves the device only through the model provider, MCP services, or plugins the user configures.

## Design

- Readability first: body contrast at least 4.5:1, tap targets around 44dp, and color is never the only way to convey information.
- Hierarchy comes from spacing, weight, and color, not from adding frames. One visual focus per screen; secondary information is deliberately quieter.
- Motion explains change. It runs 150–300 ms and animates opacity and scale; it does not move layout. When the system disables animations, skip it.
- Light and dark are two complete designs. Dividers, borders, placeholders, disabled states, and images must work in both.
- One icon language: linear icons, consistent sizes; no emoji as icons, no decorative gradients, shadows, or badges without purpose.
- Copy says only what the user needs: one action, one sentence. Supplementary detail goes behind an info control.

## Quality bar

- A screen is finished when spacing, alignment, hierarchy, and visual balance have been checked in both themes and with real content, not when it compiles.
- Changes that can delete real data, change signing, or alter a core product trade-off require explicit confirmation.
