package org.traffichunter.titan.springframework.stomp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Titan Spring STOMP client.
 * Binds connection, heartbeat, frame, and lifecycle options.
 * Values are consumed by autoconfiguration and client manager components.
 *
 * @author yun
 */
@ConfigurationProperties(prefix = "spring.titan")
public class TitanProperties {

    private boolean enabled = true;

    private boolean autoStart = true;

    private boolean autoConnect = true;

    private int primaryThreads = 1;

    private int secondaryThreads = Runtime.getRuntime().availableProcessors();

    private String host = "127.0.0.1";

    private int port = 61613;

    private String login = "guest";

    private String passcode = "guest";

    private String virtualHost = "guest";

    private long connectTimeoutMillis = 30000L;

    private long heartbeatX = 1000L;

    private long heartbeatY = 1000L;

    private int maxFrameLength = 65536;

    private boolean autoComputeContentLength = true;

    private boolean useStompFrame = false;

    private boolean bypassHostHeader = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoConnect() {
        return autoConnect;
    }

    public void setAutoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
    }

    public int getPrimaryThreads() {
        return primaryThreads;
    }

    public void setPrimaryThreads(int primaryThreads) {
        this.primaryThreads = primaryThreads;
    }

    public int getSecondaryThreads() {
        return secondaryThreads;
    }

    public void setSecondaryThreads(int secondaryThreads) {
        if (secondaryThreads <= 0) {
            this.secondaryThreads = Runtime.getRuntime().availableProcessors();
            return;
        }
        this.secondaryThreads = secondaryThreads;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getHeartbeatX() {
        return heartbeatX;
    }

    public void setHeartbeatX(long heartbeatX) {
        this.heartbeatX = heartbeatX;
    }

    public long getHeartbeatY() {
        return heartbeatY;
    }

    public void setHeartbeatY(long heartbeatY) {
        this.heartbeatY = heartbeatY;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public void setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    public boolean isAutoComputeContentLength() {
        return autoComputeContentLength;
    }

    public void setAutoComputeContentLength(boolean autoComputeContentLength) {
        this.autoComputeContentLength = autoComputeContentLength;
    }

    public boolean isUseStompFrame() {
        return useStompFrame;
    }

    public void setUseStompFrame(boolean useStompFrame) {
        this.useStompFrame = useStompFrame;
    }

    public boolean isBypassHostHeader() {
        return bypassHostHeader;
    }

    public void setBypassHostHeader(boolean bypassHostHeader) {
        this.bypassHostHeader = bypassHostHeader;
    }
}
