import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import pages.HomePage;

public class JpegMiniTest {

    private WebDriver driver;
    private Path downloadDir;

    @BeforeMethod
    public void setup() throws Exception {
        downloadDir = Paths.get(System.getProperty("user.dir"), "target", "downloads");
        Files.createDirectories(downloadDir);

        ChromeOptions options = new ChromeOptions();

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir.toAbsolutePath().toString());
        prefs.put("download.prompt_for_download", false);
        prefs.put("safebrowsing.enabled", true);
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
    }

    @AfterMethod
    public void teardown() {
        // if (driver != null) driver.quit();
    }

    @Test
    public void compressVideo_and_download_and_verify_sizes() throws Exception {
        Path originalVideo = getTestResourcePath("video/CrabRaveUncompressed.mp4");
        long originalBytes = Files.size(originalVideo);

        HomePage page = new HomePage(driver);

        page.openHome();
        page.closeOverlaysIfPresent();
        page.clickCompressVideosAndSwitchToNewTab();
        page.closeOverlaysIfPresent();
        page.waitForCompressPageReady();

        page.dragAndDropFile(originalVideo);

        String fileName = originalVideo.getFileName().toString();

        page.waitForUploadToStart(fileName);
        page.waitForUploadToFinish();
        page.waitForCompressionToStart(fileName);
        page.waitForCompressionToFinish();

        // Assert on webpage sizes (Original vs Output) and print reduction
        HomePage.SizePair ui = page.readOriginalAndOutputSizesFromResult();
        Assert.assertTrue(ui.compressedBytes() < ui.originalBytes(),
                "UI output is not smaller than original.");

        System.out.printf("UI: original=%d bytes, output=%d bytes, reduction=%.2f%%%n",
                ui.originalBytes(), ui.compressedBytes(), ui.reductionPercent());

        cleanDirectory(downloadDir);
        page.clickDownloadVideo();

        Path downloaded = waitForDownloadedVideo(downloadDir, Duration.ofMinutes(5));
        long downloadedBytes = Files.size(downloaded);

        System.out.println("Downloaded file size: " + downloadedBytes);
        System.out.println("Original file size: " + originalBytes);

        String ext = getExtension(downloaded.getFileName().toString());
        Path renamed = downloadDir.resolve("CrabRaveCompressed" + ext);

        // If file already exists from previous run, delete it
        Files.deleteIfExists(renamed);

        // Rename (move) the file
        Files.move(downloaded, renamed);
        System.out.println("Renamed downloaded file to: " + renamed.getFileName());

        Assert.assertTrue(downloadedBytes < originalBytes,
                "Downloaded compressed file is not smaller than original. original=" +
                        originalBytes + " compressed=" + downloadedBytes);
    }

    // ---------- helpers ----------

    private static Path getTestResourcePath(String resourceRelativePath) throws Exception {
        URL url = JpegMiniTest.class.getClassLoader().getResource(resourceRelativePath);
        if (url == null) {
            throw new RuntimeException("Missing test resource: src/test/resources/" + resourceRelativePath);
        }
        return Paths.get(url.toURI());
    }

    private static void cleanDirectory(Path dir) throws Exception {
        if (!Files.exists(dir))
            return;
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static Path waitForDownloadedVideo(Path dir, Duration timeout) throws Exception {
        long end = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < end) {
            try (var stream = Files.list(dir)) {
                var files = stream.toList();

                // still downloading
                boolean hasTemp = files.stream().anyMatch(p -> p.toString().endsWith(".crdownload"));
                if (hasTemp) {
                    Thread.sleep(500);
                    continue;
                }

                // pick newest MP4 only (ignore dmg)
                for (int i = files.size() - 1; i >= 0; i--) {
                    Path p = files.get(i);
                    String name = p.getFileName().toString().toLowerCase();
                    if (Files.isRegularFile(p) && name.endsWith(".mp4")) {
                        return p;
                    }
                }
            } catch (NoSuchFileException ignored) {
            }

            Thread.sleep(500);
        }
        throw new RuntimeException("Video download (.mp4) did not complete in time. Folder: " + dir);
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(dot) : "";
    }
}