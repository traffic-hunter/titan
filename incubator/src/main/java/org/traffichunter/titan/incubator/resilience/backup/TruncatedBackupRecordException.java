/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
