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
package org.traffichunter.titan.core.util;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class OptionValuesTest {

    @Test
    void read_existing_value_with_requested_type() {
        OptionValues options = OptionValues.of(Map.of("threads", 4));

        assertThat(options.getRequired("threads", Integer.class)).isEqualTo(4);
    }

    @Test
    void convert_string_values_to_requested_types() {
        OptionValues options = OptionValues.of(Map.of(
                "size", "4096",
                "enabled", "true",
                "mode", "ACTIVE"
        ));

        assertThat(options.getRequired("size", int.class)).isEqualTo(4096);
        assertThat(options.getRequired("enabled", boolean.class)).isTrue();
        assertThat(options.getRequired("mode", Mode.class)).isEqualTo(Mode.ACTIVE);
    }

    @Test
    void return_null_or_default_when_value_is_missing() {
        OptionValues options = OptionValues.of(Map.of("blank", " "));

        assertThat(options.get("missing", Integer.class)).isNull();
        assertThat(options.getOrDefault("missing", Integer.class, 8)).isEqualTo(8);
        assertThat(options.get("blank", Integer.class)).isNull();
    }

    @Test
    void reject_missing_required_value() {
        OptionValues options = OptionValues.of(Map.of());

        assertThatThrownBy(() -> options.getRequired("threads", Integer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Required option is missing: threads");
    }

    @Test
    void reject_invalid_boolean_and_type_mismatch() {
        OptionValues options = OptionValues.of(Map.of("enabled", "yes", "threads", 4L));

        assertThatThrownBy(() -> options.getRequired("enabled", Boolean.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid value for option 'enabled'");
        assertThatThrownBy(() -> options.getRequired("threads", Integer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option 'threads' must be Integer but was Long");
    }

    @Test
    void copy_source_options_and_expose_immutable_view() {
        Map<String, Object> source = new HashMap<>();
        source.put("threads", 4);
        OptionValues options = new OptionValues(source);

        source.put("threads", 8);

        assertThat(options.getRequired("threads", Integer.class)).isEqualTo(4);
        assertThatThrownBy(() -> options.asMap().put("threads", 16))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private enum Mode {
        ACTIVE
    }
}
