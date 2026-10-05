# My Trail Log branding

The display name is My Trail Log. The Android package stays com.field.logger so existing Play installations can update, and fieldlogger.co.uk remains the verified domain/API origin. SQLite, browser database names, cookie names and backup format are retained for compatibility.

The wordmark uses Kalam Bold, self-hosted on the website and bundled as an Android font resource. Its SIL Open Font License is in public/fonts/OFL-Kalam.txt. Body/control text remains standard readable type. Forest teal, ivory, citron, coral, leaf green and sky blue provide the nature palette.

The notebook/leaf icon was generated for this rebrand and is bundled into the app and website. Licensed Unsplash woodland imagery is retained; store examples use the existing public demonstration photographs and do not use account photos.

Generate native Play screenshot renders with the opt-in StoreScreenshotsTest. Set MYTRAILLOG_SCREENSHOTS=1, MYTRAILLOG_PHOTO_DIR to this checkout's public folder and MYTRAILLOG_SCREENSHOT_DIR to your chosen output folder, then run android/gradlew testDebugUnitTest --tests com.field.logger.StoreScreenshotsTest from android/. These render the real Android view hierarchy via Robolectric's native graphics, without browser UI, private accounts or paid identification requests. See STORE-UPLOAD-GUIDE.md.
