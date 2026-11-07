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
package org.traffichunter.titan.smoke.titan;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.traffichunter.titan.client.TitanClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks host name verification against the packaged server.
 *
 * @author yun
 */
@TitanSmokeTest
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TitanTlsSmokeTest {

    @ParameterizedTest(name = "host name mismatch, transport={0}")
    @EnumSource(value = TitanSmokeTransport.class, names = {"TLS", "WSS"})
    void client_rejects_certificate_for_another_host_unless_verification_is_off(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.keyStoreResource("/tls/titan-mismatch.p12");
        runtime.start(transport);

        TitanClient verifying = runtime.startClient(runtime.clientBuilder());
        assertThatThrownBy(() -> verifying.connect().get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(CertificateException.class)
                .rootCause()
                .hasMessageContaining("127.0.0.1");

        TitanClient unverified = runtime.startClient(runtime.clientBuilder().tls(runtime.clientTls(false)));
        unverified.connect().get(10, TimeUnit.SECONDS);
        String destination = "/queue/" + UUID.randomUUID();
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        unverified.subscribe(destination, frame -> received.add(new String(frame.body(), StandardCharsets.UTF_8)))
                .get(10, TimeUnit.SECONDS);
        unverified.send(destination, "hello").get(10, TimeUnit.SECONDS);

        assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("hello");
    }
}
