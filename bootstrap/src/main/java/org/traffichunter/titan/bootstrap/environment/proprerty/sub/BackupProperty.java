/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
