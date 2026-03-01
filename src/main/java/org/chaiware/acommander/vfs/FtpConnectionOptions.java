package org.chaiware.acommander.vfs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FtpConnectionOptions {
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    
    public String getUrl() {
        return String.format("ftp://%s:%d", host, port);
    }
    
    public String getFullUrl(String path) {
        String baseUrl = getUrl();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }
}
