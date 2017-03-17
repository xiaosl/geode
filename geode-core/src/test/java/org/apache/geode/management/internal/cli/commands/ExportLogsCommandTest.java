/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.management.internal.cli.commands;

import static org.apache.geode.management.internal.cli.commands.ExportLogsCommand.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.geode.test.junit.categories.UnitTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTest.class)
public class ExportLogsCommandTest {

  @Test
  public void parseSize_sizeWithUnit_shouldReturnSize() throws Exception {
    assertThat(ExportLogsCommand.parseSize("1000m")).isEqualTo(1000);
  }

  @Test
  public void parseSize_sizeWithoutUnit_shouldReturnSize() throws Exception {
    assertThat(ExportLogsCommand.parseSize("1000")).isEqualTo(1000);
  }

  @Test
  public void parseByteMultiplier_sizeWithoutUnit_shouldReturnDefaultUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000")).isEqualTo(MEGABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_m_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000m")).isEqualTo(MEGABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_g_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000g")).isEqualTo(GIGABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_t_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000t")).isEqualTo(TERABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_M_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000M")).isEqualTo(MEGABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_G_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000G")).isEqualTo(GIGABYTE);
  }

  @Test
  public void parseByteMultiplier_sizeWith_T_shouldReturnUnit() throws Exception {
    assertThat(ExportLogsCommand.parseByteMultiplier("1000T")).isEqualTo(TERABYTE);
  }

  @Test
  public void parseByteMultiplier_illegalUnit_shouldThrow() throws Exception {
    assertThatThrownBy(() -> ExportLogsCommand.parseByteMultiplier("1000q")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void parseSize_garbage_shouldThrow() throws Exception {
    assertThatThrownBy(() -> ExportLogsCommand.parseSize("bizbap")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void parseByteMultiplier_garbage_shouldThrow() throws Exception {
    assertThatThrownBy(() -> ExportLogsCommand.parseByteMultiplier("bizbap")).isInstanceOf(IllegalArgumentException.class);
  }

}
