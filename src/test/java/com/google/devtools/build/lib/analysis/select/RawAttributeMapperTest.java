// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.analysis.select;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.RawAttributeMapper;
import com.google.devtools.build.lib.packages.Rule;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Unit tests for {@link RawAttributeMapper}.
 */
@RunWith(JUnit4.class)
public class RawAttributeMapperTest extends AbstractAttributeMapperTest {

  @Before
  public final void createMapper() throws Exception {
    // Run AbstractAttributeMapper tests through a RawAttributeMapper.
    mapper = RawAttributeMapper.of(rule);
  }

  private Rule setupGenRule() throws Exception {
    return createRule("x", "myrule",
        "sh_binary(",
        "    name = 'myrule',",
        "    srcs = select({",
        "        '//conditions:a': ['a.sh'],",
        "        '//conditions:b': ['b.sh'],",
        "        '" + BuildType.Selector.DEFAULT_CONDITION_KEY + "': ['default.sh'],",
        "    }),",
        "    data = [ ':data_a', ':data_b' ])");
  }

  @Test
  public void testGetAttribute() throws Exception {
    RawAttributeMapper rawMapper = RawAttributeMapper.of(setupGenRule());
    List<Label> value = rawMapper.get("data", BuildType.LABEL_LIST);
    assertNotNull(value);
    assertThat(value).containsExactly(Label.create("x", "data_a"), Label.create("x", "data_b"));

    // Configurable attribute: trying to directly access from a RawAttributeMapper throws a
    // type mismatch exception.
    try {
      rawMapper.get("srcs", BuildType.LABEL_LIST);
      fail("Expected srcs lookup to fail since the returned type is a SelectorList and not a list");
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause().getMessage())
          .contains("SelectorList cannot be cast to java.util.List");
    }
  }

  @Override
  @Test
  public void testGetAttributeType() throws Exception {
    RawAttributeMapper rawMapper = RawAttributeMapper.of(setupGenRule());
    assertEquals(BuildType.LABEL_LIST, rawMapper.getAttributeType("data")); // not configurable
    assertEquals(BuildType.LABEL_LIST, rawMapper.getAttributeType("srcs")); // configurable
  }

  @Test
  public void testConfigurabilityCheck() throws Exception {
    RawAttributeMapper rawMapper = RawAttributeMapper.of(setupGenRule());
    assertFalse(rawMapper.isConfigurable("data", BuildType.LABEL_LIST));
    assertTrue(rawMapper.isConfigurable("srcs", BuildType.LABEL_LIST));
  }

  /**
   * Tests that RawAttributeMapper can't handle label visitation with configurable attributes.
   */
  @Test
  public void testVisitLabels() throws Exception {
    RawAttributeMapper rawMapper = RawAttributeMapper.of(setupGenRule());
    try {
      rawMapper.visitLabels(new AttributeMap.AcceptsLabelAttribute() {
        @Override
        public void acceptLabelAttribute(Label label, Attribute attribute) {
          // Nothing to do.
        }
      });
      fail("Expected label visitation to fail since one attribute is configurable");
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause().getMessage())
          .contains("SelectorList cannot be cast to java.util.List");
    }
  }

  @Test
  public void testGetConfigurabilityKeys() throws Exception {
    RawAttributeMapper rawMapper = RawAttributeMapper.of(setupGenRule());
    assertThat(rawMapper.getConfigurabilityKeys("srcs", BuildType.LABEL_LIST))
        .containsExactlyElementsIn(
            ImmutableSet.of(
                Label.parseAbsolute("//conditions:a"),
                Label.parseAbsolute("//conditions:b"),
                Label.parseAbsolute("//conditions:default")));
    assertThat(rawMapper.getConfigurabilityKeys("data", BuildType.LABEL_LIST)).isEmpty();
  }

  @Test
  public void testGetMergedValues() throws Exception {
    Rule rule = createRule("x", "myrule",
        "sh_binary(",
        "    name = 'myrule',",
        "    srcs = select({",
        "        '//conditions:a': ['a.sh', 'b.sh'],",
        "        '//conditions:b': ['b.sh', 'c.sh'],",
        "    }))");
    RawAttributeMapper rawMapper = RawAttributeMapper.of(rule);
    assertThat(rawMapper.getMergedValues("srcs", BuildType.LABEL_LIST)).containsExactly(
        Label.parseAbsolute("//x:a.sh"),
        Label.parseAbsolute("//x:b.sh"),
        Label.parseAbsolute("//x:c.sh"))
        .inOrder();
  }

  @Test
  public void testMergedValuesWithConcatenatedSelects() throws Exception {
    Rule rule = createRule("x", "myrule",
        "sh_binary(",
        "    name = 'myrule',",
        "    srcs = select({",
        "            '//conditions:a1': ['a1.sh'],",
        "            '//conditions:b1': ['b1.sh', 'another_b1.sh']})",
        "        + select({",
        "            '//conditions:a2': ['a2.sh'],",
        "            '//conditions:b2': ['b2.sh']})",
        "    )");
    RawAttributeMapper rawMapper = RawAttributeMapper.of(rule);
    assertThat(rawMapper.getMergedValues("srcs", BuildType.LABEL_LIST)).containsExactly(
        Label.parseAbsolute("//x:a1.sh"),
        Label.parseAbsolute("//x:b1.sh"),
        Label.parseAbsolute("//x:another_b1.sh"),
        Label.parseAbsolute("//x:a2.sh"),
        Label.parseAbsolute("//x:b2.sh"))
        .inOrder();
  }
}
