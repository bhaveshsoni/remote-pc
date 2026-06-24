package io.orbion.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Enables HTTPS using the self-signed certificate from {@link CertificateService}.
 * Serving over a secure origin is what allows the browser microphone (voice mode)
 * to work without any manual setup. If the certificate can't be prepared, the app
 * silently stays on HTTP (everything except voice still works).
 */
@Component
public class SslCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(SslCustomizer.class);

    private final CertificateService certificateService;
    private final SessionService sessionService;
    private final Environment environment;
    private volatile boolean sslEnabled;

    public SslCustomizer(CertificateService certificateService, SessionService sessionService,
                         Environment environment) {
        this.certificateService = certificateService;
        this.sessionService = sessionService;
        this.environment = environment;
    }

    /** Whether HTTPS was successfully enabled (used to build the pairing URL/QR). */
    public boolean isSslEnabled() {
        return sslEnabled;
    }

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // If the run was started with an explicit keystore (e.g. the https profile),
        // respect that configuration instead of generating our own.
        if (environment.getProperty("server.ssl.key-store") != null) {
            sslEnabled = true;
            return;
        }
        if (!certificateService.ensureCertificate(sessionService.getLocalIp())) {
            sslEnabled = false;
            return;
        }
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setKeyStore("file:" + certificateService.getKeystorePath().toString().replace('\\', '/'));
        ssl.setKeyStorePassword(new String(certificateService.getPassword()));
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyAlias(CertificateService.ALIAS);
        factory.setSsl(ssl);
        sslEnabled = true;
        log.info("HTTPS enabled using self-signed certificate (voice mode available).");
    }
}
