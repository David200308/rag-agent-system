package com.agentsystem.auth.service;

public interface CliKeyService {

    String registerKey(String email, String publicKeyBase64);

    /**
     * Verifies an X-Cli-Signature header value.
     *
     * Canonical message signed by the CLI:
     *   "{cliVersion} {METHOD} {/api/path} {email} {unixTimestamp}"
     *
     * @return true if signature is valid and timestamp is fresh
     */
    boolean verify(String email,
                    String signatureBase64,
                    String cliVersion,
                    String method,
                    String path,
                    long   timestamp);
}
