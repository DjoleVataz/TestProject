# JPEGmini Video Compression Automation Test

This project contains an automated test for verifying the video compression workflow on (https://jpegmini.com) using Selenium WebDriver, Java, TestNG, and Maven.

The test uploads a reference video, waits for compression to complete, verifies that the compressed size is smaller than the original (both on UI and filesystem), downloads the compressed video, and renames it for easier validation.

---

## Test Scenario Covered

The automated test performs the following steps:

1. Navigate to the JPEGmini homepage
2. Clicks on "Compress Videos" (opens in a new tab)
3. Upload a video using the drag-and-drop mechanism
4. Wait for upload and compression to complete
5. Assert on the web page that the compressed size is smaller than the original
6. Clicks on "Download the video"
7. Wait for the compressed video to download
8. Compare original and downloaded file sizes to ensure compression occurred
9. Rename the file for easier validation.

---

## Tech Stack

- Java 17+
- Selenium WebDriver
- TestNG
- Maven
- ChromeDriver

---

## Prerequisites

Make sure you have the following installed:

- Java 17 or newer
- Maven 3.8+
- Google Chrome browser

ChromeDriver is managed automatically via Selenium (matching browser version).

---

## Project Structure
src
└── test
├── java
│ ├── pages
│ │ └── HomePage.java # Page Object with all UI interactions
│ └── JpegMiniTest.java # Main end-to-end test
│
└── resources
└── video
└── CrabRaveUncompressed.mp4 # Reference test video

Downloaded videos are saved to:
/target/downloads

The downloaded file is automatically renamed to:
CrabRaveCompressed.mp4

---

## Running the Test

From the project root directory:
mvn clean test

# Notes

For Steps 5 and 8:

The test reads:

Original Size
Output Size

and verifies:
Output Size < Original Size

It also prints the reduction percentage:

UI: original=11219763 bytes, output=6815744 bytes, reduction=39.25%
File System Validation

After download, the test verifies:
downloaded_file_size < original_file_size

This ensures real compression occurred, not just a UI display change.