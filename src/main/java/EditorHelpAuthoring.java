import java.util.ServiceLoader;

/** Loads an authoring extension when a development-only provider is packaged. */
final class EditorHelpAuthoring {
    private EditorHelpAuthoring() {}

    static EditorHelpAuthoringExtension install(EditorHelpAuthoringExtension.Host host) {
        EditorHelpAuthoringExtension extension = ServiceLoader
                .load(EditorHelpAuthoringExtension.class)
                .findFirst()
                .orElse(null);
        if (extension != null) extension.install(host);
        return extension;
    }

    static boolean isAvailable() {
        return ServiceLoader.load(EditorHelpAuthoringExtension.class).findFirst().isPresent();
    }
}
