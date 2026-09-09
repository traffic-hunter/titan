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
package org.traffichunter.titan.incubator.resilience.backup;

/**
 * Thrown when an AOF record header or body is incomplete.
 *
 * <p>A truncated record can be acceptable only at the end of the file and only when
 * {@link AofRecoveryPolicy#LOAD_TRUNCATED_TAIL} is selected. The offset points to the start of the
 * incomplete record.</p>
 *
 * @author yun
 */
public final class TruncatedBackupRecordException extends BackupException {

    private final int offset;

    /**
     * Creates a truncated-record failure at a replay offset.
     */
    public TruncatedBackupRecordException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    /**
     * Returns the byte offset where the truncated record starts.
     */
    public int offset() {
        return offset;
    }
}
