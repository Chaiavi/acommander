package org.chaiware.acommander.vfs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class FtpConnectionOptions {
    public enum Protocol {
        FTP("ftp", 21),
        SFTP("sftp", 22),
        FTPS("ftp", 21);  // Use ftp:// scheme with --ssl --ssl-reqd for explicit FTPS

        private final String scheme;
        private final int defaultPort;

        Protocol(String scheme, int defaultPort) {
            this.scheme = scheme;
            this.defaultPort = defaultPort;
        }

        public String getScheme() {
            return scheme;
        }

        public int getDefaultPort() {
            return defaultPort;
        }
    }

    private String name;
    private String host;
    @Builder.Default
    private int port = 21;
    private String username;
    private String password;
    @Builder.Default
    private Protocol protocol = Protocol.FTP;
    @Builder.Default
    private boolean autoDiscover = false;

    public String getUrl() {
        return String.format("%s://%s:%d", protocol.getScheme(), host, port);
    }

    public String getFullUrl(String path) {
        String baseUrl = getUrl();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }
}
