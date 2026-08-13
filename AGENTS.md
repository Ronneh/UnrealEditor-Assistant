## Editor Guide authoring mode

The tutorial editor is an internal development tool. Keep these rules intact:

- The editor implementation and its `Edit` button are compiled only by the explicit Maven profile `tutorial-editor`.
- Normal builds (`mvn package`) and GitHub Actions release artifacts must not contain the editor implementation or its service registration.
- Development builds use `mvn -Ptutorial-editor package`.
- Edited tutorials are committed as HTML overrides below
  `src/main/resources/tutorial-overrides/`; `help-content-pack` remains ignored.
- Do not enable the profile in `.github/workflows/build.yml` or make it active by default.
- Before a release, verify both the normal build and the absence of `TutorialEditorExtension` and its service file in the normal JAR.

## Windows release icon

Windows release packaging must preserve the canonical application icon. Keep these rules intact:

- `app-icon.png` is the canonical artwork used by the running Swing application, but it does not set the Windows launcher icon by itself.
- Before invoking `jpackage` on Windows, generate a proper multi-resolution `.ico` file from `app-icon.png`. Include at least 16, 24, 32, 48, 64, 128, and 256 pixel variants.
- Always pass that `.ico` file to `jpackage` with `--icon`. Never ship a Windows app image created without an explicit icon.
- Build each version's launcher normally. Do not copy or rename a launcher from an older release, because its embedded version metadata may be stale.
- Before delivering the release, extract the icon from the packaged `.exe` and visually or programmatically verify that it is the intended application icon rather than the default Java launcher icon.
- If the packaged `.exe` contains the correct icon but Explorer still shows an older one, refresh the Windows icon cache. Cache refresh is a display workaround only and must not replace the packaging verification above.
