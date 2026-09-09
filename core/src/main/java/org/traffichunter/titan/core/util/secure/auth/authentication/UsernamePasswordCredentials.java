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
package org.traffichunter.titan.core.util.secure.auth.authentication;

/**
 * @author yun
 */
public class UsernamePasswordCredentials implements Credentials {

    private final String username;
    private final String password;

    public UsernamePasswordCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static UsernamePasswordCredentialsBuilder builder() {
        return new UsernamePasswordCredentialsBuilder();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public void apply() {
        if(username.isBlank()) {
            throw new CredentialsException("Username cannot be blank");
        }

        if(password.isBlank()) {
            throw new CredentialsException("Password cannot be blank");
        }
    }

    public static final class UsernamePasswordCredentialsBuilder {

        private String username;
        private String password;

        private UsernamePasswordCredentialsBuilder() {
        }

        public UsernamePasswordCredentialsBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UsernamePasswordCredentialsBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UsernamePasswordCredentials build() {
            return new UsernamePasswordCredentials(username, password);
        }
    }
}
