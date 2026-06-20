/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.bootstrap.environment.proprerty.sub;

/**
 * YAML DTO for append-only backup settings.
 */
public class BackupProperty {

    private boolean enabled;

    private String type;

    private String path;

    private String syncPolicy;

    private String recoveryPolicy;

    public BackupProperty() {
    }

    public BackupProperty(
            boolean enabled,
            String type,
            String path,
            String syncPolicy,
            String recoveryPolicy
    ) {
        this.enabled = enabled;
        this.type = type;
        this.path = path;
        this.syncPolicy = syncPolicy;
        this.recoveryPolicy = recoveryPolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSyncPolicy() {
        return syncPolicy;
    }

    public void setSyncPolicy(String syncPolicy) {
        this.syncPolicy = syncPolicy;
    }

    public String getRecoveryPolicy() {
        return recoveryPolicy;
    }

    public void setRecoveryPolicy(String recoveryPolicy) {
        this.recoveryPolicy = recoveryPolicy;
    }
}
