import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
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

    /**
     * Test setup:
     * - Create /target/downloads folder
     * - Configure Chrome to download automatically into that folder
     * - Start ChromeDriver
     */
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
    /**
     * Close Chrome window
     */
    @AfterMethod
    public void teardown() {
         if (driver != null) driver.quit();
    }
    /**
     * Main E2E scenario:
     * - Upload a video "CrabRaveUncompressed" from /resources/video
     * - Wait for upload + compression
     * - Assert UI sizes (Original vs Output) and print reduction %
     * - Download the compressed video
     * - Verify downloaded file is smaller than the original on disk
     * - Rename the downloaded file to a stable name for easier verification ("CrabRaveCompressed.mp4")
     */
    @Test
    public void compressVideo_and_download_and_verify_sizes() throws Exception {
        Path originalVideo = getTestResourcePath("video/CrabRaveUncompressed.mp4");
        long originalBytes = Files.size(originalVideo);

        HomePage page = new HomePage(driver);

        // Navigate to homepage
        page.openHome();
        // cookie banner + marketing popup; close them to avoid click interception
        page.closeOverlaysIfPresent();
        // Click "Compress Videos" (opens a new tab) and switch to it
        page.clickCompressVideosAndSwitchToNewTab();
        // Ensure the compress page is ready (file input exists)
        page.waitForCompressPageReady();
        // Upload using drag-and-drop mechanics
        page.dragAndDropFile(originalVideo);

        // Gets the "Compressing CrabRaveUncompressed.mp4"
        String fileName = originalVideo.getFileName().toString();

        // Wait for upload and compression lifecycle
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

        // Clean folder before download
        cleanDirectory(downloadDir);
        // Click Download the video
        page.clickDownloadVideo();

        Path downloaded = waitForDownloadedVideo(driver, downloadDir, Duration.ofMinutes(5));
        long downloadedBytes = Files.size(downloaded);

        System.out.println("Downloaded file size: " + downloadedBytes + "bytes");
        System.out.println("Original file size: " + originalBytes + "bytes");

        // Rename the downloaded video
        String ext = getExtension(downloaded.getFileName().toString());
        Path renamed = downloadDir.resolve("CrabRaveCompressed" + ext);

        // If file already exists from previous run, delete it
        Files.deleteIfExists(renamed);

        // Rename and move the file
        Files.move(downloaded, renamed);
        System.out.println("Renamed downloaded file to: " + renamed.getFileName());

        // Compare file sizes on disk (reference vs downloaded output)
        Assert.assertTrue(downloadedBytes < originalBytes,
                "Downloaded compressed file is not smaller than original. original=" +
                        originalBytes + " compressed=" + downloadedBytes);
    }

    // ---------- helpers ----------

    /**
     * Loads a file from src/test/resources using the classpath and returns a real filesystem path.
     * e.g. "video/CrabRaveUncompressed.mp4"
     */
    private static Path getTestResourcePath(String resourceRelativePath) throws Exception {
        URL url = JpegMiniTest.class.getClassLoader().getResource(resourceRelativePath);
        if (url == null) {
            throw new RuntimeException("Missing test resource: src/test/resources/" + resourceRelativePath);
        }
        return Paths.get(url.toURI());
    }

    /**
     * Deletes files inside a directory. Used to ensure a clean download folder before we click "Download the video".
     */
    private static void cleanDirectory(Path dir) throws Exception {
        if (!Files.exists(dir))
            return;
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /**
     * Waits for Chrome to finish downloading an .mp4 file into the given directory.
     * Returns the first .mp4 file found once download is complete.
     */
private static Path waitForDownloadedVideo(WebDriver driver, Path dir, Duration timeout) {
    return new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
            .pollingEvery(Duration.ofMillis(200)) // adjust as you like
            .ignoring(java.nio.file.NoSuchFileException.class)
            .until(d -> {
                try (var stream = Files.list(dir)) {
                    var files = stream.toList();

                    // If any .crdownload exists, Chrome is still downloading
                    boolean hasTemp = files.stream().anyMatch(p -> p.toString().endsWith(".crdownload"));
                    if (hasTemp) return null;

                    // Return the newest .mp4 file found
                    for (int i = files.size() - 1; i >= 0; i--) {
                        Path p = files.get(i);
                        String name = p.getFileName().toString().toLowerCase();
                        if (Files.isRegularFile(p) && name.endsWith(".mp4")) {
                            return p;
                        }
                    }
                    return null;
                } catch (Exception e) {
                    return null; // keep waiting
                }
            });
}

    /**
     * Returns file extension including dot (".mp4"), or empty string if no extension exists.
     */
    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(dot) : "";
    }
}