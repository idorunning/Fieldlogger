package com.field.logger;

import android.net.Uri;

/** Keep Chrome's offline journal, camera and account cookies on their HTTPS origin. */
public class LauncherActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    @Override protected Uri getLaunchingUrl() {
        Uri requested = getIntent().getData();
        if (requested != null && "https".equals(requested.getScheme())
                && "fieldlogger.co.uk".equals(requested.getHost())
                && (requested.getPort() == -1 || requested.getPort() == 443)
                && requested.getUserInfo() == null) {
            return requested;
        }
        return Uri.parse("https://fieldlogger.co.uk/");
    }
}
