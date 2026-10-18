# Project-local Codex design skills

Installed from [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill)
with the requested command:

```sh
npx skills add Leonxlnx/taste-skill -a codex
```

Upstream revision reviewed at installation:
`f624f37a8c650b9cc550253104925efe4c8de246`.
All 14 repository skills were installed under `skills/`; `skills-lock.json`
at the project root records each source path and content hash. The files are
committed so future Codex checkouts can discover the same project-local skills.

For Friends Only Android interface work, start with `minimalist-ui` and
`imagegen-frontend-mobile`, applying visual guidance to the existing native
Android constraints. `design-taste-frontend` v2 explicitly targets public web
pages and is not the default workflow for Android app screens. Install does
not start a redesign or add Android/runtime dependencies.

Verify the installation from the repository root:

```sh
npx skills list -a codex
```

Upstream content is retained as installed. See
[TASTE-SKILL-LICENSE](TASTE-SKILL-LICENSE) for its MIT license and copyright.
