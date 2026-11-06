#!/usr/bin/env python3
"""Prepare an isolated production-mode Mac IDE. Never reads or modifies a real IDE profile."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", default="user")
    parser.add_argument("--fixture", choices=["legacy175", "nearcap", "large-reward", "default", "fresh"], default="legacy175")
    parser.add_argument("--measure-animation", action="store_true", help="Log completed collection paint timings in this isolated RC profile")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9-]+", args.name):
        parser.error("name must contain lowercase letters, digits and hyphens only")
    repo = Path(__file__).resolve().parent.parent
    root = repo / "build" / "release-candidate" / args.name
    config = root / "config"
    marker = root / ".codexp-release-candidate"
    expected = "codexp-release-candidate-v1"
    if not marker.exists():
        if root.exists() and any(root.iterdir()):
            raise SystemExit("Refusing to reuse an unmarked directory")
        root.mkdir(parents=True, exist_ok=True)
        marker.write_text(expected + "\n")
    if marker.read_text().strip() != expected:
        raise SystemExit("Unrecognized RC profile marker")

    vendor = next((p for p in [Path.home() / "Applications/IntelliJ IDEA.app", Path("/Applications/IntelliJ IDEA.app")] if (p / "Contents/MacOS/idea").is_file()), None)
    if vendor is None:
        raise SystemExit("Install the official IntelliJ IDEA Mac app first")
    package = repo / "build/distributions/intellij-codexp-2.3.0.zip"
    package_hash = hashlib.sha256(package.read_bytes()).hexdigest()
    app_name = "CodeXP 2.3.0 RC" + ("" if args.name == "user" else " - " + args.name)
    app = root / (app_name + ".app")
    launch_record = root / "launch.json"
    processes = subprocess.check_output(["ps", "-axo", "command="], text=True)
    running = any(line.startswith(str(app / "Contents/MacOS/idea")) for line in processes.splitlines())
    if running:
        old = json.loads(launch_record.read_text()) if launch_record.exists() else {}
        if old.get("packageSha256") != package_hash:
            raise SystemExit("Close only this RC IDE before applying a new build; other IDEs are unaffected")
        print(app)
        return

    options = config / "options"
    options.mkdir(parents=True, exist_ok=True)
    profile = options / "CodeXP.xml"
    if not profile.exists():
        fixture = repo / "src/test/resources/upgrade/v2.2.0" / f"codexp-2.2.0-{args.fixture}.xml"
        shutil.copy2(fixture, profile)
        (root / "initial-2.2.0.xml").write_bytes(fixture.read_bytes())
        (root / "fixture.txt").write_text(args.fixture + "\n")
    plugins = root / "plugins"
    plugins.mkdir(exist_ok=True)
    with zipfile.ZipFile(package) as archive:
        jar_name = "intellij-codexp/lib/intellij-codexp-2.3.0.jar"
        with zipfile.ZipFile(io.BytesIO(archive.read(jar_name))) as jar:
            xml = jar.read("META-INF/plugin.xml").decode()
            if "<version>2.3.0</version>" not in xml:
                raise SystemExit("Unexpected plugin version")
            if any(not name.endswith("/") and ("/preview/" in name or "CollectionProductFixtures" in name) for name in jar.namelist()):
                raise SystemExit("Development fixture classes must not ship")
        for entry in archive.namelist():
            destination = (plugins / entry).resolve()
            if not destination.is_relative_to(plugins.resolve()):
                raise SystemExit("Invalid plugin archive path")
        current_plugin = plugins / "intellij-codexp"
        if current_plugin.exists():
            shutil.rmtree(current_plugin)
        archive.extractall(plugins)

    for project_name in ["CodeXP-RC-First", "CodeXP-RC-Second"]:
        project = root / project_name
        idea = project / ".idea"
        idea.mkdir(parents=True, exist_ok=True)
        source = project / "RcCheck.kt"
        if not source.exists():
            source.write_text("class RcCheck {\n    fun completeAlpha() = 1\n    fun completeBeta() = 2\n    fun check() {\n        // Type complete here, then accept completion with Tab or Enter.\n    }\n}\n")
        module = idea / "rc.iml"
        if not module.exists():
            module.write_text('<?xml version="1.0"?><module type="JAVA_MODULE" version="4"><component name="NewModuleRootManager"><content url="file://$MODULE_DIR$"><sourceFolder url="file://$MODULE_DIR$" isTestSource="false"/></content><orderEntry type="sourceFolder" forTests="false"/></component></module>')
        modules = idea / "modules.xml"
        if not modules.exists():
            modules.write_text('<?xml version="1.0"?><project version="4"><component name="ProjectModuleManager"><modules><module fileurl="file://$PROJECT_DIR$/.idea/rc.iml" filepath="$PROJECT_DIR$/.idea/rc.iml"/></modules></component></project>')

    vmoptions = root / "rc.vmoptions"
    base = (vendor / "Contents/bin/idea.vmoptions").read_text()
    vmoptions.write_text(base + "\n" + "\n".join([
        f"-Didea.config.path={config}", f"-Didea.system.path={root / 'system'}",
        f"-Didea.log.path={root / 'log'}", f"-Didea.plugins.path={plugins}",
        "-Didea.required.plugins.id=com.github.ilovegamecoding.intellijcodexp",
        "-Didea.plugin.in.sandbox.mode=true", "",
        *(["-Dcodexp.collection.measure=true"] if args.measure_animation else []),
    ]))
    if app.exists():
        shutil.rmtree(app)
    contents = app / "Contents"
    (contents / "MacOS").mkdir(parents=True)
    shutil.copy2(vendor / "Contents/MacOS/idea", contents / "MacOS/idea")
    for child in (vendor / "Contents").iterdir():
        if child.name not in {"MacOS", "Info.plist", "_CodeSignature"}:
            (contents / child.name).symlink_to(child, target_is_directory=child.is_dir())
    info = plistlib.loads((vendor / "Contents/Info.plist").read_bytes())
    info.update(CFBundleIdentifier="dev.codexp.releasecandidate." + args.name, CFBundleName=app_name, CFBundleDisplayName=app_name)
    info["LSEnvironment"] = {"IDEA_VM_OPTIONS": str(vmoptions)}
    (contents / "Info.plist").write_bytes(plistlib.dumps(info))
    entitlements = root / "entitlements.plist"
    entitlements.write_bytes(plistlib.dumps({
        "com.apple.security.cs.allow-jit": True,
        "com.apple.security.cs.allow-unsigned-executable-memory": True,
        "com.apple.security.cs.disable-library-validation": True,
    }))
    subprocess.run(["codesign", "--force", "--sign", "-", "--entitlements", str(entitlements), str(app)], check=True, capture_output=True)
    launch_record.write_text(json.dumps({
        "app": str(app), "project": str(root / "CodeXP-RC-First"), "secondProject": str(root / "CodeXP-RC-Second"),
        "config": str(config), "plugins": str(plugins), "packageSha256": package_hash,
        "productionMode": True, "syntheticProfile": True, "fixture": (root / "fixture.txt").read_text().strip(),
        "measureAnimation": args.measure_animation,
    }, indent=2) + "\n")
    print(app)


if __name__ == "__main__":
    main()
