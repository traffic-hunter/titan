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
package org.traffichunter.titan.perftest;

/**
 * Internal process entry point launched by the Go CLI.
 *
 * @author yun
 */
public final class PerfTestApplication {

    private static final String RESULT_PREFIX = "TITAN_PERF_RESULT=";

    private PerfTestApplication() {
    }

    public static void main(String[] arguments) {
        try {
            PerfTestOptions options = PerfTestOptions.parse(arguments);
            System.out.println(RESULT_PREFIX + new StompPerfTest().run(options).toJson());
        } catch (Exception error) {
            System.err.println("Performance test failed: " + error.getMessage());
            System.exit(2);
        }
    }
}
