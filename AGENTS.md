## Editor Guide authoring mode

The tutorial editor is an internal development tool. Keep these rules intact:

- The editor implementation and its `Edit` button are compiled only by the explicit Maven profile `tutorial-editor`.
- Normal builds (`mvn package`) and GitHub Actions release artifacts must not contain the editor implementation or its service registration.
- Development builds use `mvn -Ptutorial-editor package`.
- Edited tutorials are committed as HTML overrides below
  `src/main/resources/tutorial-overrides/`; `help-content-pack` remains ignored.
- Do not enable the profile in `.github/workflows/build.yml` or make it active by default.
- Before a release, verify both the normal build and the absence of `TutorialEditorExtension` and its service file in the normal JAR.
