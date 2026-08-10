import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorHelpUiTest {
    @TempDir Path temp;

    @Test
    void articleAlwaysOffersCopyExportWithoutDependingOnHtmlSelectionOffsets() throws Exception {
        EditorHelpPanel[] holder = new EditorHelpPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new EditorHelpPanel(null, null));
        assertEquals(TransferHandler.COPY,
                holder[0].articleForTest().getTransferHandler().getSourceActions(holder[0].articleForTest()));
    }

    @Test
    void locatesPackRebuildsIndexAndCreatesPanelHeadlessly() throws Exception {
        Path packDirectory = createPack();
        String previous = System.getProperty(EditorHelpEnvironment.CONTENT_PROPERTY);
        System.setProperty(EditorHelpEnvironment.CONTENT_PROPERTY, packDirectory.toString());
        try (EditorHelpEnvironment.Session session = new EditorHelpEnvironment().open()) {
            assertEquals("en", session.pack().language());
            assertEquals(2, session.search().documentCount());
            assertTrue(Files.isRegularFile(packDirectory.resolve("search-index/.catalog-sha256")));

            EditorHelpPanel[] holder = new EditorHelpPanel[1];
            SwingUtilities.invokeAndWait(() ->
                    holder[0] = new EditorHelpPanel(session.pack(), session.search()));
            EditorHelpPanel panel = holder[0];
            SwingUtilities.invokeAndWait(() -> panel.openDocument(
                    session.pack().documents().get(0).id(), true));
            assertEquals(session.pack().documents().get(0).id(), panel.currentDocumentIdForTest());
            var root = (javax.swing.tree.DefaultMutableTreeNode)
                    panel.categoryTreeForTest().getModel().getRoot();
            assertTrue(root.getChildCount() >= 1);
        } finally {
            if (previous == null) System.clearProperty(EditorHelpEnvironment.CONTENT_PROPERTY);
            else System.setProperty(EditorHelpEnvironment.CONTENT_PROPERTY, previous);
        }
    }

    @Test
    void removesLegacyPageColorsAndCharsetDirectivesDoNotBlockRendering() throws Exception {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><meta http-equiv="Content-Type" content="text/html; charset=windows-1252"></head>
                <body bgcolor="#ffffff" text="#eeeeee" style="background-color:white;color:white;padding:4px">
                <font color="white">Readable tutorial</font>
                <table bgcolor="white"><tr><td style="color:#fff">Text</td></tr></table>
                </body></html>
                """);
        assertTrue(!cleaned.toLowerCase().contains("bgcolor"));
        assertTrue(!cleaned.toLowerCase().contains("color:white"));
        assertTrue(!cleaned.toLowerCase().contains("color=\"#"));
        assertTrue(cleaned.contains("Readable tutorial"));
    }

    @Test
    void keepsLinkMarkupForBlueStylingButRemovesAllClickTargets() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <p>
                  <a href="https://example.com">Website</a>
                  <a href="mailto:test@example.com">Email</a>
                  <a href="https://editor-help.local/tutorial/next">Next tutorial</a>
                </p>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        assertEquals(3, document.select("a").size());
        assertTrue(document.select("a[href]").isEmpty());
    }

    @Test
    void shortensCodeRulersAndWrapsLongPreformattedLines() {
        String ruler = "//" + "=".repeat(120);
        String longComment = "// Import the sounds. Note: commands are only executed when you rebuild "
                + "from the command line using unreal make and additional arguments.";
        String cleaned = EditorHelpPanel.prepareArticleHtml(
                "<pre><font size='2'>" + ruler + "\n" + longComment + "</font></pre>");
        String code = org.jsoup.Jsoup.parse(cleaned).selectFirst("pre").wholeText();
        String[] lines = code.split("\\R");
        assertEquals("//" + "=".repeat(64), lines[0]);
        for (String line : lines) assertTrue(line.length() <= 80, line);
        assertTrue(code.contains("additional arguments."));
    }

    @Test
    void normalizesPageTitleRuleWidthAndLeadingIndentation() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                  <p align="center"><font size="5">&nbsp; UnrealScript Language Reference</font></p>
                  <p>&nbsp;</p>
                  <hr width="900" size="3" align="center">
                  <p>&nbsp;&nbsp; Tim Sweeney</p>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        var title = document.selectFirst(".help-page-title");
        assertEquals("UnrealScript Language Reference", title.text());
        assertEquals("left", title.attr("align"));
        var rule = document.selectFirst("hr");
        assertEquals("97%", rule.attr("width"));
        assertEquals("left", rule.attr("align"));
        assertTrue(!rule.hasAttr("size"));
        assertEquals("Tim Sweeney", document.select("p").last().text());
    }

    @Test
    void stitchesLegacySplitScreenshotsWithoutAnIntermediateCellGap() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <table><tr>
                  <td width="100"><img src="../assets/tool-step1-left.jpg" width="100"></td>
                  <td width="550"><img src="../assets/tool-step1-right.jpg" width="270" align="left">
                    <p>Tutorial text</p></td>
                  <td width="100"></td>
                </tr></table>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        var firstRowImages = document.select("table > tbody > tr, table > tr").first().select("img");
        assertEquals(2, firstRowImages.size());
        assertEquals("../assets/tool-step1-left.jpg", firstRowImages.get(0).attr("src"));
        assertEquals("../assets/tool-step1-right.jpg", firstRowImages.get(1).attr("src"));
        assertTrue(document.text().contains("Tutorial text"));
    }

    @Test
    void normalizesLegacyTypographyAndLeftAlignsTutorialText() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                  <h1 align="center"><font face="Arial" size="2">Terminology</font></h1>
                  <p align="right"><font size="1">A readable paragraph.</font></p>
                  <ul align="right"><li align="right">Solid Brushes</li></ul>
                  <p align="center"><img src="screenshot.jpg"></p>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        assertEquals("left", document.selectFirst("h1").attr("align"));
        assertEquals("left", document.selectFirst("p").attr("align"));
        assertEquals("left", document.selectFirst("li").attr("align"));
        assertTrue(!document.selectFirst("font").hasAttr("size"));
        assertTrue(!document.selectFirst("font").hasAttr("face"));
        var imageParagraph = document.select("p").get(1);
        assertEquals("center", imageParagraph.attr("align"));
        assertTrue(imageParagraph.hasClass("help-image-only"));
    }

    @Test
    void separatesIconHeadingFromItsDescription() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                  <p><img align="left" src="button.jpg" width="33" height="35">
                    <b>Brush disintersection.</b> This does the opposite of the intersect button.
                    <br clear="left">
                  </p>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        var entry = document.selectFirst("table.help-icon-entry");
        assertEquals("button.jpg", entry.selectFirst("td.help-icon img").attr("src"));
        assertEquals("Brush disintersection", entry.selectFirst("td.help-icon-heading").text());
        assertEquals("This does the opposite of the intersect button.",
                document.selectFirst("p.help-icon-description").text());
        assertTrue(entry.select("br[clear]").isEmpty());
        assertEquals(3, entry.selectFirst("tr").childrenSize());
        assertEquals("4", entry.selectFirst("td.help-icon-gap").attr("width"));
        assertEquals("middle", entry.selectFirst("td.help-icon-heading").attr("valign"));
        assertTrue(!entry.hasAttr("width"));
        assertEquals("left", entry.attr("align"));
    }

    @Test
    void unwrapsIndentationOnlyListsAndCompactsLegacyIconColumns() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                  <ul><p align="right">Provided by: unrealed.exe</p><h3>Buttons</h3></ul>
                  <table width="646"><tr>
                    <td width="300"><p><img src="camera.gif" width="31">&nbsp;</p></td>
                    <td width="605"><p><b>Camera:</b> Default mode.</p></td>
                  </tr></table>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        assertTrue(document.select("ul, ol").isEmpty());
        assertEquals("left", document.selectFirst("p").attr("align"));
        assertEquals(1, document.selectFirst("tr").childrenSize());
        assertEquals("left", document.selectFirst("td").attr("align"));
        assertTrue(!document.selectFirst("td").hasAttr("width"));
        assertEquals("camera.gif", document.selectFirst(".help-icon-heading img").attr("src"));
        assertEquals("left", document.selectFirst("table").attr("align"));
    }

    @Test
    void appliesVocabularyAndBrushDefinitionSpecialFormatting() {
        String vocabulary = EditorHelpPanel.prepareArticleHtml("""
                <h1>Unreal Editor Vocabulary</h1><ul><li><p>Brushes</p><ul>
                <li><p><font>Solid Brushes<br>A solid brush description.</font></p></li>
                </ul></li></ul>
                """);
        org.jsoup.nodes.Document vocabularyDocument = org.jsoup.Jsoup.parse(vocabulary);
        assertTrue(vocabularyDocument.selectFirst("ul").hasClass("help-vocabulary-lists"));
        assertTrue(vocabularyDocument.selectFirst("p").hasClass("help-vocabulary-root"));
        assertEquals("Solid Brushes",
                vocabularyDocument.selectFirst(".help-vocabulary-term").text());

        String brush = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>The Active Brush</title></head><body>
                <p class="heading">The Active Brush</p><hr>
                <p><img src="active.jpg" align="left" width="74" height="149">Description</p>
                </body></html>
                """);
        org.jsoup.nodes.Document brushDocument = org.jsoup.Jsoup.parse(brush);
        assertTrue(brushDocument.selectFirst("p.help-image-only img").hasClass("help-rotate-90"));
        assertEquals("Description", brushDocument.select("p").last().text());

        String abbreviatedTitle = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>The Semi</title></head><body>
                <p class="heading">The Semi-Solid Brush</p><hr>
                <p><font><img src="semi.jpg" align="left" width="73" height="149">
                <b>Semi Solid Brushes</b> are a special brush class.</font></p>
                </body></html>
                """);
        var abbreviatedDocument = org.jsoup.Jsoup.parse(abbreviatedTitle);
        assertEquals("semi.jpg",
                abbreviatedDocument.selectFirst("p.help-image-only img.help-rotate-90").attr("src"));
        assertTrue(abbreviatedDocument.selectFirst("p:contains(Semi Solid Brushes)")
                .select("img").isEmpty());
    }

    @Test
    void placesInterfaceIconsBesideTheirHeadings() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                <b><font>Camera mode:</font></b><font><br>
                <img src="camera.gif" width="32" height="32"><br>
                This is the default mode and you will only move selected object.<br>
                </font>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        var entry = document.selectFirst("table.help-icon-entry");
        assertEquals("camera.gif", entry.selectFirst("td.help-icon img").attr("src"));
        assertEquals("Camera mode:", entry.selectFirst("td.help-icon-heading").text());
        assertEquals(3, entry.selectFirst("tr").childrenSize());
        assertEquals("32", entry.selectFirst("tr").attr("height"));
        assertEquals("middle", entry.selectFirst("td.help-icon-heading").attr("valign"));
        assertEquals("middle", entry.selectFirst("td.help-icon").attr("valign"));
        assertEquals(1, document.select("img").size());
        assertTrue(document.text().contains("This is the default mode"));
    }

    @Test
    void normalizesOnlyNamedInterfaceHeadingsWithAlternateLegacyMarkup() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                <p>This is the default mode and you will only move selected object.</p>
                <font size="3"><b>Deintersect:</b><br>
                <img src="deintersect.gif" width="32" height="32"></font>
                <font size="2"><br>Deintersect description.</font>
                <font size="3"><b>Hide selected actors:</b><br>
                <img src="hide.gif" width="33" height="34"></font>
                <font size="2"><br>Hide description.</font>
                <font size="3"><b>Cylinder brush:</b><br>
                <img src="cylinder.gif" width="32" height="32"></font><br>
                <font size="2"><br>Cylinder description.</font>
                <b><font size="3">Cube brush:</font></b><br>
                <img src="cube.gif" width="31" height="32"><br>
                <font size="2">Cube description.</font>
                <font size="3"><b>Unlisted heading:</b><br>
                <img src="unlisted.gif" width="32" height="32"></font>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        assertEquals(4, document.select("table.help-icon-entry").size());
        assertEquals("deintersect.gif",
                document.select("table.help-icon-entry td.help-icon img").get(0).attr("src"));
        assertEquals("hide.gif",
                document.select("table.help-icon-entry td.help-icon img").get(1).attr("src"));
        assertEquals("cube.gif",
                document.select("table.help-icon-entry td.help-icon img").get(3).attr("src"));
        assertTrue(document.select("table.help-icon-entry br").isEmpty());
        assertTrue(document.select("table.help-icon-entry + font > br:first-child").isEmpty());
        assertEquals("Cylinder description.",
                document.select("table.help-icon-entry").get(2).nextElementSibling().text());
        assertTrue(document.selectFirst("img[src=unlisted.gif]").parent().normalName().equals("font"));
        assertTrue(document.text().contains("Deintersect description."));
        assertTrue(document.text().contains("Hide description."));
        assertTrue(document.text().contains("Cube description."));
    }

    @Test
    void removesOnlyGoldabarTipPrefixes() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>UnrealEd 2</title></head><body>
                <p>By: Dean "Goldabar" Tate</p>
                <p><font>-First tip<br> - Second tip<br>Keep de-intersect intact.</font></p>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        assertTrue(document.text().contains("First tip Second tip"));
        assertTrue(document.text().contains("de-intersect"));
        assertTrue(!document.text().contains("-First"));
        assertTrue(!document.text().contains("- Second"));
    }

    @Test
    void placesUnrealedButtonIconsAfterVerticallyCenteredHeadings() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>The Buttons of Unrealed 2</title></head><body>
                <table width="676"><tr><td width="33"><img src="search.gif" width="24" height="24"></td>
                <td><b>Search for Actors:</b><br>Find an actor.</td></tr></table>
                </body></html>
                """);
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(cleaned);
        var entry = document.selectFirst("table.help-icon-entry");
        assertEquals("Search for Actors:", entry.selectFirst("td.help-icon-heading").text());
        assertEquals("search.gif", entry.selectFirst("td.help-icon img").attr("src"));
        assertTrue(entry.html().indexOf("Search for Actors:") < entry.html().indexOf("search.gif"));
        assertEquals("middle", entry.selectFirst("td.help-icon-heading").attr("valign"));
        assertEquals("middle", entry.selectFirst("td.help-icon").attr("valign"));
    }

    @Test
    void formatsEditorHeaderButtonSpecialCasesAndRemovesLegacyLabels() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Editor Tool Buttons</title></head><body>
                <h1>Editor 2.0 Toolbar Buttons</h1>
                <p><a><u>unrealed.exe</u></a></p>
                <p><a><u>UED Resource Lab</u></a></p>
                <h3><font size="3">Functions / Modes:</font></h3>
                <h3><font size="3">Brushes:</font></h3>
                <p><b><font size="3">Operations:</font></b></p>
                <p><b><font size="3">Viewing Modes:</font></b></p>
                <p><font><img src="vertex.jpg" width="34" height="33">
                <b>Vertex Editing.</b> Vertex description.</font></p>
                <p><font><img src="scale.jpg" width="31" height="30">
                <b>Scale brush.</b> Scale description.</font></p>
                <p><br><img src="last.jpg" width="33" height="31">
                <b>Change Camera Speed.</b> Last description.</p>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);

        assertTrue(document.body().hasClass("help-editor-header-buttons"));
        assertTrue(document.text().contains("Functions / Modes:"));
        assertEquals(List.of("Functions / Modes:", "Brushes:", "Operations:", "Viewing Modes:"),
                document.select(".help-toolbar-section").eachText());
        assertTrue(!document.text().contains("unrealed.exe"));
        assertTrue(!document.text().contains("UED Resource Lab"));
        assertEquals(3, document.select("table.help-icon-entry").size());
        assertEquals(List.of("Vertex Editing", "Scale brush", "Change Camera Speed"),
                document.select("td.help-icon-heading").eachText());
        assertTrue(document.select("td.help-icon-heading[valign=middle]").size() == 3);
        assertTrue(document.select("td.help-icon[valign=middle]").size() == 3);
        assertTrue(document.select("p.help-icon-description").size() == 3);
    }

    @Test
    void formatsEditorToolbarButtonsAndCentersBrowserScreenshots() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Editor Tool Buttons</title></head><body>
                <h1>Editor 2.0 Header Buttons</h1>
                <p><a><u>UED Resource Lab</u></a></p>
                <h3><font size="3">Functions:</font></h3>
                <p><font><img src="open.jpg"><b>Open.</b> Open description.</font></p>
                <p><font><img src="save.jpg"><b>Save.</b> Save description.</font></p>
                <p><img src="search-icon.jpg"><b>Search for Actors.</b> Search description.
                <br><img src="search-window.gif"></p>
                <p><img src="group-icon.jpg"><b>Group Browser.</b> Group description.
                <br><img src="group-window.gif"></p>
                <p><img src="music-icon.jpg"><b>Music Browser.</b> Music description.
                <br><img src="music-window.gif"><img src="music-import.gif"></p>
                <p><img src="build.jpg"><b>Build Options.</b>&nbsp;</p>
                <p>Build options description.</p>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);

        assertTrue(!document.text().contains("UED Resource Lab"));
        assertTrue(document.selectFirst("h3").hasClass("help-toolbar-section"));
        assertEquals(List.of("Open", "Save", "Search for Actors", "Group Browser",
                        "Music Browser", "Build Options"),
                document.select("td.help-icon-heading").eachText());
        assertEquals(4, document.select("p.help-toolbar-screenshot").size());
        assertEquals(List.of("search-window.gif", "group-window.gif",
                        "music-window.gif", "music-import.gif"),
                document.select("p.help-toolbar-screenshot img").eachAttr("src"));
        assertTrue(document.select("p.help-toolbar-screenshot[align=center]").size() == 4);
        assertEquals("Build options description.",
                document.select("p.help-icon-description").last().text());
    }

    @Test
    void removesLegacySiteLabelsFromEveryArticle() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                <p><a><u>UED Resource Lab</u></a></p>
                <h1><a><u>UT City</u></a></h1>
                <p>By: Mapper <a><u>UT City</u></a></p>
                <p>A normal mention of UT City remains article text.</p>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        assertTrue(document.select("a:contains(UED Resource Lab), a:contains(UT City)").isEmpty());
        assertTrue(!document.text().contains("UED Resource Lab"));
        assertEquals(1, document.select("p:contains(By: Mapper)").size());
        assertTrue(document.text().contains("A normal mention of UT City remains article text."));
    }

    @Test
    void formatsDefinitiveGuideHeadingsAndSeparatesPlayerStartImage() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                <h3>THE DEFINITIVE UNREALED v2.0 INTRODUCTION AND GUIDE</h3>
                <h3><font>INTRODUCTION</font></h3>
                <p><b>WHO THIS TUTORIAL IS FOR:</b></p>
                <p><b>FIRE IT UP:</b></p>
                <p>Introduction text.</p>
                <p><font><br>(a). <u><img src="../assets/UED/unrealed12.jpg"></u>
                INSERT PLAYERSTART: The player start is an actor.</font></p>
                <p><font>(b). REBUILDING THE LEVEL: Rebuild text.</font></p>
                <p><font>(c). SAVING AND PLAYING: Save text.</font></p>
                <h3><font>THE CONCEPT OF INTERSECTION AND DEINTERSECTION</font></h3>
                <h3><font>CONNECTING TWO ROOMS &amp; COLORED LIGHTING</font></h3>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);

        assertEquals(List.of("Introduction:", "Who this Tutorial is for:", "Fire it up:",
                        "The concept of Intersection and Deintersection:",
                        "Connecting two rooms & Colored lighting:"),
                document.select("p.help-guide-section").eachText());
        assertEquals(5, document.select("p.help-guide-section > strong").size());
        assertEquals("center", document.selectFirst("p.help-playerstart-image").attr("align"));
        assertTrue(document.selectFirst(".help-playerstart-image img").attr("src")
                .endsWith("unrealed12.jpg"));
        assertTrue(document.selectFirst("p.help-playerstart-text").text()
                .startsWith("(a). Insert Playerstart: The player start is an actor."));
        assertEquals(List.of("(a). Insert Playerstart:", "(b). Rebuilding the Level:",
                        "(c). Saving and playing:"),
                document.select("strong.help-guide-step").eachText());
        assertEquals(3, document.select("strong.help-guide-step + br").size());
        assertEquals("The Definitive UnrealEd 2.0 Introduction and Guide by Machismo (2.0)",
                EditorHelpPanel.correctHelpTitle(
                        "The Definative UnrealEd 2.0 Introduction and Guide by Machismo (2.0)"));
    }

    @Test
    void alignsBrushActionCaptionsAndBreaksSpecialBrushInstructions() {
        String subtracted = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>The Subtracted Brush</title></head><body>
                <p class="heading">The Subtracted Brush</p>
                <p>Instruction.<br><br><img src="button_subtractq.jpg" align="left"
                width="32" height="32"><i>This will create the Subtracted brush.</i></p>
                </body></html>
                """);
        var subtractedDocument = org.jsoup.Jsoup.parse(subtracted);
        assertEquals("4", subtractedDocument.selectFirst(".help-brush-action-gap").attr("width"));
        assertEquals("middle",
                subtractedDocument.selectFirst(".help-brush-action-text").attr("valign"));
        assertEquals("This will create the Subtracted brush.",
                subtractedDocument.selectFirst(".help-brush-action-text").text());

        String semiSolid = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>The Semi</title></head><body>
                <p class="heading">The Semi-Solid Brush</p>
                <p><i>This will open a dialogue window (shown below). Select
                <b>Semi Solid</b> from the options.</i></p>
                </body></html>
                """);
        var semiSolidDocument = org.jsoup.Jsoup.parse(semiSolid);
        assertEquals("br", semiSolidDocument.selectFirst("i").child(0).normalName());
        assertTrue(semiSolidDocument.selectFirst("i").html()
                .contains("(shown below).<br>"));
    }

    @Test
    void correctsHelpNavigationTyposAndGlobalTopMarkers() {
        assertEquals("Advanced Brushes", EditorHelpPanel.correctHelpTitle("Adavnced Brushes"));
        assertEquals("Creating Assault Levels by Silencer (2.0)",
                EditorHelpPanel.correctHelpTitle(
                        "Creating Assult Levels by Silencer (2.0)"));
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body><table><tr><td><a><font>[^TOP]</font></a></td></tr></table>
                </body></html>
                """);
        assertTrue(!org.jsoup.Jsoup.parse(cleaned).text().contains("[^TOP]"));
    }

    @Test
    void cleansAndFormatsKeyMoverTutorial() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Movers That Are Triggered By Keys</title></head><body>
                <p>&nbsp;</p><p>&nbsp;</p><table><tr><td><p><b>Movers That Are
                Triggered By Keys</b></p></td></tr>
                <tr><td><p>Editor used: Unrealed2.0 Download .zip</p>
                <p>Keep this sentence. But at www.planetunreal.com/chimeric I found
                some very Interesting stuff.</p></td></tr>
                <tr><td><p>5. Copy the following lines into the script
                (overwriting all the text there is):</p></td></tr>
                <tr><td>&nbsp;</td></tr>
                <tr height="100"><td><p><font face="Courier New">class KeyMover;</font></p>
                <p><font face="Courier New">{</font></p>
                <p><font face="Courier New">}</font></p></td></tr></table>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        assertTrue(document.body().children().first().normalName().equals("table"));
        assertTrue(!document.text().contains("Editor used:"));
        assertTrue(!document.text().contains("Interesting stuff"));
        assertTrue(document.text().contains("Keep this sentence."));
        assertEquals("class KeyMover;\n{\n}", document.selectFirst("pre.help-keymover-code").text());
    }

    @Test
    void centersRequestedTutorialImagesAndRepairsEzkeelQuotes() {
        String slippery = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Slippery Surfaces</title></head><body>
                <p class="heading">Slippery Surfaces</p>
                <p>Before <img src="one.jpg"> after <img src="two.jpg"></p>
                </body></html>
                """);
        assertEquals(2, org.jsoup.Jsoup.parse(slippery)
                .select("div.help-centered-tutorial-image[align=center]").size());

        String water = EditorHelpPanel.prepareArticleHtml("""
                <html><head><meta name="Author" content="EZkeel"><title>Smashing Windows</title>
                </head><body><p><b>A room that fills with water</b></p>
                <p><img src="water.jpg">If youÂ’re careful, donÂ’t stop.</p>
                </body></html>
                """);
        var waterDocument = org.jsoup.Jsoup.parse(water);
        assertEquals(1, waterDocument.select(
                "div.help-centered-tutorial-image[align=center]").size());
        assertTrue(waterDocument.text().contains("If you're careful, don't stop."));
        assertTrue(!waterDocument.text().contains("Â"));
    }

    @Test
    void usesDarkLocalTabChrome() throws Exception {
        Path packDirectory = createPack();
        String previous = System.getProperty(EditorHelpEnvironment.CONTENT_PROPERTY);
        System.setProperty(EditorHelpEnvironment.CONTENT_PROPERTY, packDirectory.toString());
        try (EditorHelpEnvironment.Session session = new EditorHelpEnvironment().open()) {
            EditorHelpPanel panel = new EditorHelpPanel(session.pack(), session.search());
            var tabs = panel.browseTabsForTest();
            assertEquals(AssistantTheme.BACKGROUND, tabs.getBackground());
            BufferedImage image = new BufferedImage(360, 300, BufferedImage.TYPE_INT_ARGB);
            tabs.setSize(360, 300);
            tabs.paint(image.getGraphics());
            assertTrue(!tabs.getUI().getClass().getName().contains("DarkHelpTabbedPaneUI"));
        } finally {
            if (previous == null) System.clearProperty(EditorHelpEnvironment.CONTENT_PROPERTY);
            else System.setProperty(EditorHelpEnvironment.CONTENT_PROPERTY, previous);
        }
    }

    @Test
    void expandsBlackswayPageHeaderAcrossItsLegacyOuterTable() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Group Browser</title></head><body>
                <table width="95%" cellpadding="5"><tr>
                <td class="heading"><font>Group Browser</font><hr><p>By: Blacksway</p></td>
                <td><hr><p>&nbsp;</p></td></tr>
                <tr><td><img src="group.jpg"></td><td>Article text</td></tr>
                </table></body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        var headerRow = document.selectFirst("table > tbody > tr");
        assertEquals(1, headerRow.childrenSize());
        assertEquals("2", headerRow.child(0).attr("colspan"));
        assertEquals("100%", document.selectFirst("table").attr("width"));
        assertEquals(1, headerRow.select("hr").size());
        assertEquals(2, document.select("table > tbody > tr").get(1).childrenSize());
    }

    @Test
    void movesOnlyBegFireImageAboveAssumptions() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Fire</title></head><body><ul>
                <p>By:BEG</p>
                <h3>ASSUMPTIONS:</h3><p>Basic editor knowledge.</p>
                <h3>TUTORIAL:</h3><p>Add a PlayerStart and rebuild.
                <img src="fire_1.jpg" width="411" height="284"></p>
                </ul></body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        var assumptions = document.selectFirst("h3:contains(ASSUMPTIONS:)");
        var imageParagraph = assumptions.previousElementSibling();
        assertTrue(imageParagraph.hasClass("help-image-only"));
        assertEquals("center", imageParagraph.attr("align"));
        assertEquals("fire_1.jpg", imageParagraph.selectFirst("img").attr("src"));
        assertTrue(document.selectFirst("p:contains(Add a PlayerStart)").select("img").isEmpty());

        String unrelated = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Fire</title></head><body>
                <p>By:Someone Else</p><h3>ASSUMPTIONS:</h3>
                <p>End <img src="other.jpg"></p></body></html>
                """);
        var unrelatedDocument = org.jsoup.Jsoup.parse(unrelated);
        assertEquals("other.jpg",
                unrelatedDocument.selectFirst("p:contains(End) img").attr("src"));
    }

    @Test
    void formatsOnlyMillenniumFogImagesAndHeadings() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Fog</title></head><body>
                <p>Tomasz 'Millennium' Jachimczak</p>
                <h3>Forward:</h3><h3>Abstract:</h3><h3>Assumptions:</h3>
                <h3>Tutorial:</h3><h5>Introduction:</h5><h5>Setting the scene:</h5>
                <p><img align="left" src="../assets/UED/fog1.jpg">First description.</p>
                <h5>The light settings:</h5>
                <p>Lead text.<br><img align="left" src="../assets/UED/fog2.jpg">Second description.</p>
                <h5>A Little Warning:</h5>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        assertEquals(2, document.select("img + br[clear=all]").size());
        assertEquals(6, document.select(".help-fog-heading").size());
        assertTrue(document.selectFirst("h3:contains(Tutorial:)").hasClass("help-fog-tutorial"));
        assertTrue(document.selectFirst("h5:contains(A Little Warning:)")
                .hasClass("help-fog-warning"));

        String unrelated = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Fog</title></head><body>
                <p>By: Another author</p><h3>Tutorial:</h3>
                <p><img src="fog1.jpg">Description.</p></body></html>
                """);
        var unrelatedDocument = org.jsoup.Jsoup.parse(unrelated);
        assertTrue(unrelatedDocument.select(".help-fog-tutorial").isEmpty());
        assertTrue(unrelatedDocument.select("img + br[clear=all]").isEmpty());
    }

    @Test
    void addsThinBordersOnlyToLegacyDataTables() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><body>
                <table id="layout"><tr><td>Layout left</td><td>Layout right</td></tr>
                <tr><td>More layout</td><td>More layout</td></tr></table>
                <table id="properties" border="0"><tr>
                <td style="border-style: solid; border-width: 1">Level Property</td>
                <td style="border-style: solid; border-width: 1">Specific</td></tr>
                <tr><td style="border: 1 solid #006600">Author</td>
                <td style="border: 1 solid #006600">Name of the creator.</td></tr></table>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        var properties = document.selectFirst("#properties");
        assertTrue(properties.hasClass("help-data-table"));
        assertEquals("1", properties.attr("border"));
        assertEquals("0", properties.attr("cellspacing"));
        assertEquals(4, properties.select("td.help-data-cell").size());
        assertTrue(properties.select("td").stream()
                .allMatch(cell -> cell.attr("style").contains("1px solid #3a4352")));
        assertTrue(!document.selectFirst("#layout").hasClass("help-data-table"));
        assertTrue(!document.selectFirst("#layout").hasAttr("border"));
    }

    @Test
    void repositionsOnlyTheRequestedWolfWaterImages() {
        String cleaned = EditorHelpPanel.prepareArticleHtml("""
                <html><head><title>Wolf's Tutorial Water</title></head><body>
                <p><font><img src="shot0000.jpg">&nbsp;<img src="shot0001.jpg"></font></p>
                <font><img src="SHOT0002.JPG"></font>
                <p id="note">Letting the rest "overhang."<img src="image1.jpg"></p>
                <h3>Waterfalls</h3>
                <p id="later"><img src="shot0008.jpg"></p>
                </body></html>
                """);
        var document = org.jsoup.Jsoup.parse(cleaned);
        assertEquals(3, document.select("p.help-wolf-prior-image img").size());
        var note = document.selectFirst("#note");
        assertTrue(note.select("img").isEmpty());
        assertEquals("Letting the rest \"overhang.\"", note.text());
        var overhangImageRow = note.nextElementSibling();
        assertTrue(overhangImageRow.hasClass("help-wolf-overhang-image"));
        assertEquals("center", overhangImageRow.attr("align"));
        assertEquals("image1.jpg", overhangImageRow.selectFirst("img").attr("src"));
        assertTrue(!document.selectFirst("#later").hasClass("help-wolf-prior-image"));
    }

    private Path createPack() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("help.hhc"), """
                <html><body><ul><li><object><param name="Name" value="Level Editing"></object><ul>
                  <li><object><param name="Name" value="Brush Basics"><param name="Local" value="brush.htm"></object></li>
                  <li><object><param name="Name" value="Mover Setup"><param name="Local" value="mover.htm"></object></li>
                </ul></li></ul></body></html>
                """);
        Files.writeString(source.resolve("brush.htm"),
                "<html><head><title>Brush Basics</title></head><body><h1>Brushes</h1>"
                        + "<p>Additive brush geometry.</p><a href='mover.htm'>Movers</a></body></html>");
        Files.writeString(source.resolve("mover.htm"),
                "<html><head><title>Mover Setup</title></head><body><h1>Movers</h1>"
                        + "<p>Create moving doors.</p></body></html>");
        Path pack = temp.resolve("pack");
        new EditorHelpImporter().importHelp(source, pack);
        return pack;
    }
}
