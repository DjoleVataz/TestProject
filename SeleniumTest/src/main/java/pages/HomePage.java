package pages;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HomePage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    // URLs
    private static final String HOME_URL = "https://jpegmini.com/";

    // --- Locators ---
    // homepage
    private final By compressVideosBtn = By.cssSelector("a[href*='./compress-videos']");

    // Download the video
    private final By downloadVideoBtn = By.cssSelector("div.optimizer-download-btn");

    // Upload progress bar (exists while uploading)
    private final By uploadProgressBar = By.cssSelector("div.uploader-progressbar");

    // Result sizes
    private final By sizeValuesInResult = By.cssSelector("div.sizes div.size-value");

    public HomePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(40));
    }

    public void openHome() {
        driver.get(HOME_URL);
        driver.manage().window().maximize();
        waitForPageReady();
    }

    public void clickCompressVideosAndSwitchToNewTab() {
        closeOverlaysIfPresent();

        String original = driver.getWindowHandle();
        int before = driver.getWindowHandles().size();

        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(compressVideosBtn));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);

        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(d -> d.getWindowHandles().size() > before);

        for (String h : driver.getWindowHandles()) {
            if (!h.equals(original)) {
                driver.switchTo().window(h);
                break;
            }
        }

        waitForPageReady();
        closeOverlaysIfPresent();
    }

    public void dragAndDropFile(Path filePath) {
        closeOverlaysIfPresent();
        waitForCompressPageReady();

        String abs = filePath.toAbsolutePath().toString();

        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("file-input")));

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].style.display='block'; arguments[0].style.visibility='visible'; arguments[0].style.opacity=1;",
                input);

        input.sendKeys(abs);

        // Waits for the upload to start
        waitForUploadToStart(filePath.getFileName().toString());

        try {
            ((JavascriptExecutor) driver).executeScript("""
                        const dropSelector = arguments[0];
                        const inputSelector = arguments[1];

                        const dropTarget = document.querySelector(dropSelector);
                        const inputEl = document.querySelector(inputSelector);

                        if (!dropTarget) return "NO_DROPZONE";
                        if (!inputEl || !inputEl.files || inputEl.files.length === 0) return "NO_FILE";

                        const file = inputEl.files[0];
                        const dt = new DataTransfer();
                        dt.items.add(file);

                        ['dragenter','dragover','drop'].forEach(type => {
                          const evt = new DragEvent(type, { dataTransfer: dt, bubbles: true, cancelable: true });
                          dropTarget.dispatchEvent(evt);
                        });

                        return "DROPPED";
                    """, "label.dropzone, label.dropzone[for='file-input']", "#file-input");
        } catch (Exception ignored) {
            // do nothing, upload already started
        }
    }

    public void waitForUploadToStart(String filename) {
        // waits until “Uploading CrabRaveUncompressed” or the progress bar appears
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
            String text = d.findElement(By.tagName("body")).getText();
            boolean hasUploadingText = text.contains("Uploading") && text.contains(filename);
            boolean hasProgress = !d.findElements(uploadProgressBar).isEmpty();
            return hasUploadingText || hasProgress;
        });
    }

    public void waitForUploadToFinish() {
        new WebDriverWait(driver, Duration.ofMinutes(3))
                .until(ExpectedConditions.invisibilityOfElementLocated(uploadProgressBar));
    }

    public void waitForCompressionToStart(String filename) {
        new WebDriverWait(driver, Duration.ofMinutes(2)).until(d -> {
            String t = d.findElement(By.tagName("body")).getText();
            return t.contains("Compressing") && t.contains(filename);
        });
    }

    // Wait condition: Download button becomes clickable (compression done).
    public void waitForCompressionToFinish() {
        closeOverlaysIfPresent();
        new WebDriverWait(driver, Duration.ofMinutes(10)).until(d -> {
            List<WebElement> btns = d.findElements(downloadVideoBtn);
            return !btns.isEmpty() && btns.get(0).isDisplayed() && btns.get(0).isEnabled();
        });
    }

    public void clickDownload() {
        wait.until(ExpectedConditions.elementToBeClickable(downloadVideoBtn)).click();
    }

    private static long parseSizeValueElementToBytes(WebElement el) {
        // text is like: "10.7\nMB"
        String normalized = el.getText().replace("\n", " ").trim(); // "10.7 MB"

        Pattern p = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*(B|KB|MB|GB)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normalized);
        if (!m.find())
            throw new RuntimeException("Cannot parse size: " + normalized);

        double value = Double.parseDouble(m.group(1).replace(",", "."));
        String unit = m.group(2).toUpperCase(Locale.ROOT);

        return switch (unit) {
            case "B" -> (long) value;
            case "KB" -> (long) (value * 1024);
            case "MB" -> (long) (value * 1024 * 1024);
            case "GB" -> (long) (value * 1024 * 1024 * 1024);
            default -> throw new RuntimeException("Unknown unit: " + unit);
        };
    }

    // ---------- helpers ----------

    private void waitForPageReady() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
        } catch (Exception ignored) {
        }
    }

    public record SizePair(long originalBytes, long compressedBytes) {

        public double reductionPercent() {
            return (1.0 - ((double) compressedBytes / (double) originalBytes)) * 100.0;
        }
    }

    public void closeOverlaysIfPresent() {
        try {
            List<WebElement> cookies = driver.findElements(By.cssSelector("button[data-testid='banner-accept']"));
            if (!cookies.isEmpty() && cookies.get(0).isDisplayed()) {
                cookies.get(0).click();
            }
        } catch (Exception ignored) {
        }
        // MARKETING POPUP CLOSE
        try {
            List<WebElement> closeBtns = driver.findElements(By.cssSelector(
                    "[data-framer-name='CloseButton'],[data-framer-name='CloseBtn'],[data-framer-name='Close']"));
            for (WebElement b : closeBtns) {
                if (b.isDisplayed()) {
                    b.click();
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            driver.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE);
        } catch (Exception ignored) {
        }
    }

    public void waitForCompressPageReady() {
        closeOverlaysIfPresent();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("file-input")));
    }

    public void waitForResultPanel() {
        new WebDriverWait(driver, Duration.ofMinutes(5)).until(d -> {
            List<WebElement> values = d.findElements(sizeValuesInResult);
            return values.size() >= 2
                    && values.get(0).isDisplayed()
                    && values.get(1).isDisplayed()
                    && !values.get(0).getText().trim().isEmpty()
                    && !values.get(1).getText().trim().isEmpty();
        });
    }

    public SizePair readOriginalAndOutputSizesFromResult() {
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> d.findElements(sizeValuesInResult).size() >= 2);

        List<WebElement> values = driver.findElements(sizeValuesInResult);
        long original = parseSizeValueElementToBytes(values.get(0));
        long output = parseSizeValueElementToBytes(values.get(1));

        if (output >= original) {
            throw new AssertionError(
                    "UI sizes do not indicate compression. original=" + original + " output=" + output);
        }
        return new SizePair(original, output);
    }

    public void clickDownloadVideo() {
        closeOverlaysIfPresent();
        WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfElementLocated(downloadVideoBtn));

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
    }

}
