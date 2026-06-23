---
name: step-image
description: Use StepFun image APIs to generate Chinese technical illustrations, article images, whiteboard infographics, WeChat public-account covers, Bilibili knowledge covers, and Itwanger-style teaching graphics when Codex built-in image_gen is unavailable or the current Codex model/provider is Step 3.7 Flash, StepFun, or another non-OpenAI provider. Trigger for requests like "用 Step 生图", "StepFun 生成图片", "切到 Step 3.7 Flash 后生图", "不要用 Codex image_gen", "二哥风格配图但走 Step 接口", and "step-image".
---

# Step Image

Generate images through StepFun's image endpoint instead of Codex's built-in `image_gen` tool. Use this skill when image generation must still work after Codex has been switched to Step 3.7 Flash or another provider where the hosted `image_gen` tool is unavailable.

## API Contract

Use the bundled script:

```bash
python3 .agents/skills/step-image/scripts/generate_step_image.py \
  --prompt "一张公众号封面图，主题是 AI Agent 编程，科技感，干净高级，不要过度赛博风"
```

The script calls:

- `POST https://api.stepfun.com/v1/images/generations`
- default model: `step-image-edit-2`
- default response format: `b64_json`, saved as a local image file
- API key env var: `STEP_API_KEY`

Do not call Codex `image_gen` or the `$imagegen` skill from this skill. The point of this skill is to bypass Codex hosted image generation.

## Workflow

1. Extract the user's topic, desired platform, ratio, required visible Chinese words, reference style, and negative constraints.
2. Rewrite the request into one concise StepFun prompt of 512 characters or fewer.
3. Prefer `step-image-edit-2` unless the user explicitly asks for another StepFun image model.
4. Pick a supported size:
   - default square: `1024x1024`
   - article landscape: `768x1360`
   - article portrait or cover-like: `1360x768`
   - medium portrait: `1184x896`
   - medium landscape: `896x1184`
5. Run the script and show the saved local image path. In the Codex app, include a Markdown image tag with the absolute path when useful.
6. For iterations, keep the same prompt structure and change only the requested parts. Reuse the seed when the user wants similar composition.

StepFun documents `step-image-edit-2` size values as `height x width`, not `width x height`. Preserve that convention when passing `--size`.

## Itwanger Style Defaults

When the user asks for "二哥风格", "itwanger", "手绘白板", "公众号文章配图", or gives no style:

- Use a clean Chinese technical illustration, suitable for `toBeBetterJavaer`, WeChat articles, Bilibili knowledge covers, or tutorial posts.
- Prefer white or warm-white paper texture for article body infographics.
- Use simple hand-drawn structure: flow, comparison, layers, cycle, center-radiation, or timeline.
- Use 4-5 core nodes, each with short Chinese labels.
- Include a cartoon teacher if useful: short hair, glasses, yellow shirt, dark tie, holding a pointer or laptop.
- Use red for risk/failure, green for success/done, blue for systems/process, orange for key points.
- Avoid a full outer border, rounded screenshot-card framing, dark neon style, dense tiny text, long English paragraphs, and overdone 3D.

For covers, do not force a whiteboard style. Use a cleaner editorial/knowledge-cover look unless the user explicitly asks for whiteboard.

## Prompt Template

Use this as a starting point, then compress to 512 characters:

```text
生成一张中文技术配图，主题：{topic}。标题：{title}。版式：{layout}。画面包含 4-5 个短中文节点：{nodes}。风格：二哥手绘白板/公众号知识图，白底或浅暖白，粗黑中文标题，红绿蓝橙重点词，简洁图标，留白充分。可加入短发眼镜黄衬衫深色领带的讲解员。避免暗黑霓虹、复杂3D、密集小字、长英文、整图外框。
```

For a cover:

```text
生成一张中文技术封面图，主题：{topic}。主标题：{title}。视觉中心突出 {main_object}，科技感但干净高级，中文清晰，适合公众号/B站知识区，不要过度赛博风、不要密集文字。
```

## Script Examples

Generate a square image:

```bash
python3 .agents/skills/step-image/scripts/generate_step_image.py \
  --prompt "生成一张中文技术配图，主题：Codex 切换 Step 模型后如何继续生图，白底手绘流程图，包含：文本模型、Skill、StepFun 图片接口、本地保存，简洁清晰" \
  --size 1024x1024
```

Generate with text rendering optimization:

```bash
python3 .agents/skills/step-image/scripts/generate_step_image.py \
  --prompt "生成一张公众号封面图，主标题：AI Agent 生图新链路，副标题：StepFun 图片接口，干净科技感，中文清晰" \
  --size 1360x768 \
  --text-mode
```

Preview the request without calling the API:

```bash
python3 .agents/skills/step-image/scripts/generate_step_image.py \
  --prompt "采菊东篱下，悠然见南山" \
  --dry-run
```

## Quality Gate

Before reporting completion:

1. Confirm the script saved an image or returned a StepFun URL.
2. Include the seed when available, so the user can iterate.
3. Mention if the API key is missing, the prompt exceeds 512 characters, or StepFun returns `content_filtered`.
4. Do not claim Codex built-in `image_gen` was used.
