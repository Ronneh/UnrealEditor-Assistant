import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class CoreRegressionTest {
    @Test
    void everyUnrealScriptLessonHasItsOwnExample() {
        long distinctExamples = java.util.Arrays.stream(UnrealScriptLearning.LESSONS)
                .map(UnrealScriptLearning::exampleForLesson)
                .distinct()
                .count();

        assertEquals(UnrealScriptLearning.LESSONS.length, distinctExamples);
    }

    @Test
    void todoNamesReplaceColonWithUnderscore() {
        assertEquals("Todo_", NotesPanel.safeName("Todo:"));
    }

    @Test
    void brushOptimizerDefaultsToTwoUnitGrid() throws Exception {
        BrushOptimizer optimizer = new BrushOptimizer();
        optimizer.createContent();
        Field field = BrushOptimizer.class.getDeclaredField("gridStepBox");
        field.setAccessible(true);

        assertEquals(2, ((JComboBox<?>) field.get(optimizer)).getSelectedItem());
    }

    @Test
    void manualFileTreeOrderIsPersisted() throws Exception {
        Path folder = Files.createTempDirectory("tree-order");
        Path alpha = Files.writeString(folder.resolve("Alpha.t3d"), "A");
        Path beta = Files.writeString(folder.resolve("Beta.t3d"), "B");

        FileTreeOrder.place(folder, beta, 0);
        List<Path> ordered = FileTreeOrder.sort(folder, List.of(alpha, beta));

        assertEquals(List.of(beta, alpha), ordered);
    }

    @Test
    void prefabExplorerSupportsBothRedoShortcuts() throws Exception {
        PrefabExplorerPanel panel = new PrefabExplorerPanel(Files.createTempDirectory("prefab-redo"));
        Field field = PrefabExplorerPanel.class.getDeclaredField("code");
        field.setAccessible(true);
        JTextArea editor = (JTextArea) field.get(panel);

        assertEquals("redoPrefab", shortcut(editor, "control Y"));
        assertEquals("redoPrefab", shortcut(editor, "control shift Z"));
        Field selectorField = PrefabExplorerPanel.class.getDeclaredField("brushSelector");
        selectorField.setAccessible(true);
        JComboBox<?> selector = (JComboBox<?>) selectorField.get(panel);
        assertEquals("None", selector.getSelectedItem());
        assertFalse(selector.isEnabled());
    }

    @Test
    void prefabPasteAddsExactlyOneTrailingLineBreak() {
        String lineBreak = System.lineSeparator();

        assertEquals("Begin Brush" + lineBreak,
                PrefabExplorerPanel.withTrailingLineBreak("Begin Brush"));
        assertEquals("Begin Brush\n", PrefabExplorerPanel.withTrailingLineBreak("Begin Brush\n"));
    }



    @Test
    void prefabExplorerRecognizesSupportedFiles() throws Exception {
        Path folder = Files.createTempDirectory("prefab-types");
        Path t3d = Files.writeString(folder.resolve("Hall.t3d"), "Begin Brush");
        Path u3d = Files.writeString(folder.resolve("Lift.U3D"), "Begin Brush");
        Path text = Files.writeString(folder.resolve("Hall.txt"), "Begin PolyList");

        assertTrue(PrefabExplorerPanel.isPrefab(t3d));
        assertTrue(PrefabExplorerPanel.isPrefab(text));
        assertFalse(PrefabExplorerPanel.isPrefab(u3d));
        assertEquals(new Color(34, 211, 238), PrefabExplorerPanel.PREFAB_COLOR);
    }

    @Test
    void brushPreviewAcceptsStandalonePolyList() {
        String polyList = """
                Begin PolyList
                    Begin Polygon Texture=Default
                        Vertex   +00000.000000,+00000.000000,+00000.000000
                        Vertex   +00128.000000,+00000.000000,+00000.000000
                        Vertex   +00128.000000,+00128.000000,+00000.000000
                    End Polygon
                End PolyList""";

        assertEquals(1, BrushPreviewPanel.polygonCount(polyList));
    }

    @Test
    void brushPreviewFindsMultipleBrushes() {
        String brush = """
                Begin Brush
                  Begin PolyList
                    Begin Polygon
                      Vertex 0,0,0
                      Vertex 1,0,0
                    End Polygon
                  End PolyList
                End Brush
                """;

        assertEquals(2, BrushPreviewPanel.brushCount(brush + brush));
        assertEquals(2, BrushPreviewPanel.polygonCount(brush + brush));
    }

    @Test
    void atomicTextFileReplacesExistingContent() throws Exception {
        Path file = Files.writeString(Files.createTempFile("atomic-prefab", ".t3d"), "old");

        AtomicTextFile.write(file, "new prefab");

        assertEquals("new prefab", Files.readString(file));
    }

    @Test
    void detectsWindowsForNativeTitleBarStyling() {
        assertTrue(WindowsTitleBar.isWindows("Windows 11"));
        assertTrue(WindowsTitleBar.isWindows("windows 10"));
        assertFalse(WindowsTitleBar.isWindows("Linux"));
    }

    @Test
    void convertsLazilyLoadedClipboardImages() {
        BufferedImage source = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        Image lazyImage = source.getScaledInstance(4, 3, Image.SCALE_SMOOTH);

        BufferedImage converted = ImageToolSupport.toBuffered(lazyImage);

        assertEquals(4, converted.getWidth());
        assertEquals(3, converted.getHeight());
    }

    @Test
    void doubleMapChangesOnlyEventAndTagValues() {
        String input = """
                Begin Actor Class=Mover Name=RedMover
                    bDamageTriggered=True
                    MultiSkins(1)=Texture'MyLevel.General.RonLampRed'
                    Event=OpenRedDoor
                    Tag=RedTrigger
                    Begin Brush Name=RedMover
                        Begin PolyList
                            Begin Polygon Texture=rclfwl4-RED
                            End Polygon
                        End PolyList
                    End Brush
                End Actor""";

        String output = MapDoublerPanel.transform(input).output();

        assertTrue(output.contains("Event=OpenblueDoor"));
        assertTrue(output.contains("Tag=blueTrigger"));
        assertTrue(output.contains("bDamageTriggered=True"));
        assertTrue(output.contains("MultiSkins(1)=Texture'MyLevel.General.RonLampRed'"));
        assertTrue(output.contains("Begin Polygon Texture=rclfwl4-RED"));
        assertTrue(output.contains("Begin Brush Name=RedMover"));
        assertFalse(output.contains("bDamageTriggeblue"));
    }

    @Test
    void doubleMapLogUsesRequestedCategoryOrder() throws Exception {
        String input = """
                Begin Actor Class=SpecialEvent Name=SpecialZ
                    Event=RedSpecial
                End Actor
                Begin Actor Class=Trigger Name=TriggerZ
                    Event=RedTrigger
                End Actor
                Begin Actor Class=Mover Name=MoverZ
                    Tag=RedMover
                End Actor
                Begin Actor Class=PlayerStart Name=PlayerZ
                    TeamNumber=0
                End Actor
                Begin Actor Class=FlagBase Name=FlagZ
                    Team=0
                End Actor""";
        MapDoublerPanel.TransformResult result = MapDoublerPanel.transform(input);
        MapDoublerPanel panel = new MapDoublerPanel();
        Method writeLog = MapDoublerPanel.class.getDeclaredMethod("writeLog", List.class, boolean.class);
        writeLog.setAccessible(true);
        writeLog.invoke(panel, result.changes(), true);
        Field logField = MapDoublerPanel.class.getDeclaredField("logArea");
        logField.setAccessible(true);
        String log = ((JTextPane) logField.get(panel)).getText();

        assertInOrder(log, "Flags & PlayerStarts:", "Movers:", "Triggers:", "SpecialEvents:");
    }

    @Test
    void searchShortcutsAreInstalled() {
        JTextArea area = new JTextArea("red blue red");
        TextSearchSupport.install(area, area, "Test");

        assertEquals("findText", shortcut(area, "control F"));
        assertEquals("findNextText", shortcut(area, "F3"));
        assertEquals("findPreviousText", shortcut(area, "shift F3"));
    }

    @Test
    void generatedCylinderContainsValidBrushEnvelope() {
        String brush = BrushGeneratorPanel.generateCylinder(
                "TestBrush", "CSG_Add", 8, 256, 0, 256, true, 32, 0, 0, 0);

        assertTrue(brush.contains("Begin Actor Class=Brush Name=TestBrush"));
        assertTrue(brush.contains("CsgOper=CSG_Add"));
        assertTrue(brush.contains("Begin PolyList"));
        assertTrue(brush.contains("End PolyList"));
        assertTrue(brush.contains("End Actor"));
    }

    @Test
    void seamlessTextureMirrorsBothAxes() {
        BufferedImage quarter = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        quarter.setRGB(0, 0, Color.RED.getRGB());
        quarter.setRGB(1, 0, Color.GREEN.getRGB());
        quarter.setRGB(0, 1, Color.BLUE.getRGB());
        quarter.setRGB(1, 1, Color.WHITE.getRGB());

        BufferedImage output = SeamlessTexture.createMirroredTexture(quarter);

        assertEquals(4, output.getWidth());
        assertEquals(4, output.getHeight());
        assertEquals(output.getRGB(0, 0), output.getRGB(3, 0));
        assertEquals(output.getRGB(0, 0), output.getRGB(0, 3));
        assertEquals(output.getRGB(1, 1), output.getRGB(2, 2));
    }

    @Test
    void screenshotMakerUsesLargestPossibleSquareCrop() {
        BufferedImage square = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB);
        BufferedImage widescreen = new BufferedImage(2560, 1440, BufferedImage.TYPE_INT_RGB);

        assertEquals(new Rectangle(0, 0, 2048, 2048),
                ScreenshotMakerPanel.initialCropFor(square));
        assertEquals(new Rectangle(560, 0, 1440, 1440),
                ScreenshotMakerPanel.initialCropFor(widescreen));
    }

    @Test
    void screenshotMakerExportDirectoryPrefersSavedLocationThenDesktop() throws Exception {
        File home = java.nio.file.Files.createTempDirectory("screenshot-maker-home").toFile();
        File desktop = new File(home, "Desktop");
        File saved = new File(home, "Saved");
        assertTrue(desktop.mkdir());
        assertTrue(saved.mkdir());

        assertEquals(saved, FileSaveSupport.preferredDirectory(saved.getPath(), home));
        assertEquals(desktop, FileSaveSupport.preferredDirectory(null, home));
        assertEquals(desktop, FileSaveSupport.preferredDirectory(
                new File(home, "missing").getPath(), home));
    }

    private static Object shortcut(JTextArea area, String keyStroke) {
        return area.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyStroke));
    }

    private static void assertInOrder(String text, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = text.indexOf(value);
            assertTrue(current > previous, () -> "Wrong order for " + value + " in:\n" + text);
            previous = current;
        }
    }
}
