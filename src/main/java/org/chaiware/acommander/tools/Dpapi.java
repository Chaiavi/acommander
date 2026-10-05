package org.chaiware.acommander.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Windows DPAPI for the current user, through one hidden PowerShell run per batch; secrets travel on stdin only. */
public class Dpapi {
    // One base64 line in, one base64 line out; a line that fails (other user or PC) comes out empty.
    private static final String SCRIPT = "Add-Type -AssemblyName System.Security; "
            + "$scope = [Security.Cryptography.DataProtectionScope]::CurrentUser; "
            + "while ($null -ne ($line = [Console]::In.ReadLine())) { "
            + "try { [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::%s("
            + "[Convert]::FromBase64String($line), $null, $scope)) } catch { '' } }";

    /** Encrypts each text; the results are base64 and can only be decrypted by this Windows user on this PC. */
    public List<String> protect(List<String> texts) throws IOException {
        List<String> encoded = texts.stream()
                .map(text -> Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)))
                .toList();
        List<String> ciphers = run("Protect", encoded);
        if (ciphers.contains("")) {
            throw new IOException("Windows could not encrypt the password.");
        }
        return ciphers;
    }

    /** Decrypts each {@link #protect} result; one that can't be decrypted comes back as {@code ""}. */
    public List<String> unprotect(List<String> ciphers) throws IOException {
        return run("Unprotect", ciphers).stream()
                .map(plain -> new String(Base64.getDecoder().decode(plain), StandardCharsets.UTF_8))
                .toList();
    }

    private List<String> run(String method, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            return List.of();
        }
        ProcessRunner.Result result;
        try {
            result = ProcessRunner.of("powershell", "-NoProfile", "-NonInteractive", "-Command", SCRIPT.formatted(method))
                    .stdin(String.join("\n", lines) + "\n")
                    .run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Password encryption was interrupted", e);
        }
        if (!result.succeeded() || result.stdout().size() != lines.size()) {
            throw new IOException("Password encryption failed (PowerShell exit " + result.exitCode() + "): "
                    + result.stderrText().trim());
        }
        return result.stdout();
    }
}
