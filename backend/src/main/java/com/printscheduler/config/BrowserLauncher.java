package com.printscheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final Environment env;

    public BrowserLauncher(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        String port = env.getProperty("server.port", "8080");
        String url  = "http://localhost:" + port;

        log.info("Dashboard ready → {}", url);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (Exception e) {
                log.debug("AWT Desktop.browse failed: {}", e.getMessage());
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd;
        if (os.contains("linux"))   cmd = new String[]{"xdg-open", url};
        else if (os.contains("mac")) cmd = new String[]{"open", url};
        else                         cmd = new String[]{"cmd", "/c", "start", url};

        try {
            new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            log.info("Browser opened via: {}", String.join(" ", cmd));
        } catch (Exception e) {
            log.warn("Could not open browser automatically. Navigate to: {}", url);
        }
    }
}
