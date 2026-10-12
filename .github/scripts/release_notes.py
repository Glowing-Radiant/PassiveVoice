"""Writes release notes for a tag: its annotation (if any), then every commit since the previous tag."""

import subprocess
import sys


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def previous_tag(tag: str) -> str | None:
    try:
        return git("describe", "--tags", "--abbrev=0", f"{tag}^")
    except subprocess.CalledProcessError:
        return None  # First release.


def escape(text: str) -> str:
    """GitHub drops unknown tags, so "<author>" would vanish from the notes."""
    return text.replace("<", "&lt;").replace(">", "&gt;")


def body_block(block: str) -> list[str]:
    """One paragraph of a commit body, indented under its bullet. "- " lists stay lists; wrapped lines are rejoined."""
    items: list[str] = []
    for line in block.splitlines():
        if line.startswith("- ") or not items:
            items.append(line)
        else:
            items[-1] += " " + line
    return ["  " + escape(" ".join(item.split())) for item in items]


def main(tag: str, repo: str) -> None:
    lines: list[str] = []

    if git("cat-file", "-t", tag) == "tag":
        highlights = git("tag", "-l", "--format=%(contents:subject)%0a%0a%(contents:body)", tag).strip()
        if highlights:
            lines += [highlights, ""]

    previous = previous_tag(tag)
    log = git("log", "--no-merges", "--format=%s%x1f%b%x1e", f"{previous}..{tag}" if previous else tag)
    lines += ["## Changes", ""]
    for entry in filter(None, (e.strip() for e in log.split("\x1e"))):
        subject, _, body = entry.partition("\x1f")
        lines.append(f"- **{escape(subject.strip())}**")
        paragraph = [l.strip() for l in body.splitlines() if not l.startswith("Co-Authored-By:")]
        text = "\n".join(paragraph).strip()
        for block in filter(None, text.split("\n\n")):
            lines += [""] + body_block(block)
        lines.append("")

    lines += [
        "## Install",
        "",
        f"Download the APK below, open it on your phone and allow the install. Then turn on PassiveVoice in"
        f" Settings → Accessibility.",
    ]
    if previous:
        lines += ["", f"**Full changelog:** https://github.com/{repo}/compare/{previous}...{tag}"]
    print("\n".join(lines))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1], sys.argv[2])
