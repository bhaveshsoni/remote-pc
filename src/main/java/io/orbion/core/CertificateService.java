package io.orbion.core;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Provides a self-signed HTTPS certificate so the installed app can serve over
 * {@code https}/{@code wss} without any external tooling (the bundled runtime
 * has no {@code keytool}). Voice mode requires a secure browser origin, so this
 * is what makes the microphone work straight from the installer.
 *
 * <p>The keystore and its randomly-generated password are persisted under
 * {@code ~/.orbion} so the certificate stays stable across launches – a phone
 * only has to accept the browser's certificate warning once.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    public static final String ALIAS = "orbion";
    private static final String STORE_TYPE = "PKCS12";

    private final Path dir = Path.of(System.getProperty("user.home"), ".orbion");
    private final Path keystorePath = dir.resolve("orbion.p12");
    private final Path passwordPath = dir.resolve("orbion.p12.pass");

    private char[] password;

    public Path getKeystorePath() {
        return keystorePath;
    }

    public char[] getPassword() {
        return password;
    }

    /**
     * Ensures a usable keystore exists, generating one if needed.
     *
     * @param localIp the LAN IP to add as a Subject Alternative Name
     * @return true if HTTPS material is ready; false if generation failed
     */
    public synchronized boolean ensureCertificate(String localIp) {
        try {
            Files.createDirectories(dir);
            if (Files.exists(keystorePath) && Files.exists(passwordPath)) {
                password = Files.readString(passwordPath).trim().toCharArray();
                // Sanity check: the keystore must open with the stored password.
                try (var in = Files.newInputStream(keystorePath)) {
                    KeyStore ks = KeyStore.getInstance(STORE_TYPE);
                    ks.load(in, password);
                    if (ks.containsAlias(ALIAS)) {
                        return true;
                    }
                }
            }
            generate(localIp);
            return true;
        } catch (Exception e) {
            log.warn("Could not prepare HTTPS certificate; the app will fall back to HTTP "
                    + "(voice mode will be unavailable). Cause: {}", e.toString());
            return false;
        }
    }

    private void generate(String localIp) throws Exception {
        byte[] pw = new byte[16];
        new SecureRandom().nextBytes(pw);
        this.password = HexFormat.of().formatHex(pw).toCharArray();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        X500Name dn = new X500Name("CN=Orbion Local");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date notAfter = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, serial, notBefore, notAfter, dn, keyPair.getPublic());

        List<GeneralName> sans = new ArrayList<>();
        sans.add(new GeneralName(GeneralName.dNSName, "localhost"));
        sans.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
        if (localIp != null && !localIp.isBlank() && !"127.0.0.1".equals(localIp)) {
            sans.add(new GeneralName(GeneralName.iPAddress, localIp));
        }
        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(sans.toArray(new GeneralName[0])));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        KeyStore ks = KeyStore.getInstance(STORE_TYPE);
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), password, new X509Certificate[]{cert});
        try (OutputStream out = Files.newOutputStream(keystorePath)) {
            ks.store(out, password);
        }
        Files.writeString(passwordPath, new String(password));
        log.info("Generated self-signed HTTPS certificate at {}", keystorePath);
    }
}
