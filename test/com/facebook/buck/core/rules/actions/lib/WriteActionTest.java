/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.core.rules.actions.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.artifact.Artifact;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.actions.ActionCreationException;
import com.facebook.buck.core.rules.actions.ActionRegistryForTests;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.step.impl.TestActionExecutionRunner;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WriteActionTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  ProjectFilesystem projectFilesystem;

  @Before
  public void setUp() {
    projectFilesystem = TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());
  }

  @Test
  public void writesContentsToFile() throws ActionCreationException, IOException {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");

    ActionRegistryForTests registry = new ActionRegistryForTests(target, projectFilesystem);
    TestActionExecutionRunner runner = new TestActionExecutionRunner(projectFilesystem, target);
    Artifact output1 = runner.declareArtifact(Paths.get("bar1"));
    Artifact output2 = runner.declareArtifact(Paths.get("bar2"));
    ImmutableSet<Artifact> outputs = ImmutableSet.of(output1, output2);

    TestActionExecutionRunner.ExecutionDetails<WriteAction> result =
        runner.runAction(new WriteAction(registry, ImmutableSet.of(), outputs, "foobar", false));

    Path outputPath1 =
        Objects.requireNonNull(output1.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();
    Path outputPath2 =
        Objects.requireNonNull(output2.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();

    assertTrue(result.getResult().isSuccess());
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertTrue(outputPath1.endsWith(Paths.get("bar1")));
    assertTrue(outputPath2.endsWith(Paths.get("bar2")));
  }

  @Test
  public void writesContentsToNestedFile() throws ActionCreationException, IOException {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");

    ActionRegistryForTests registry = new ActionRegistryForTests(target, projectFilesystem);
    TestActionExecutionRunner runner = new TestActionExecutionRunner(projectFilesystem, target);
    Artifact output1 = runner.declareArtifact(Paths.get("foo").resolve("bar1"));
    Artifact output2 = runner.declareArtifact(Paths.get("foo").resolve("bar2"));
    ImmutableSet<Artifact> outputs = ImmutableSet.of(output1, output2);

    TestActionExecutionRunner.ExecutionDetails<WriteAction> result =
        runner.runAction(new WriteAction(registry, ImmutableSet.of(), outputs, "foobar", false));

    Path outputPath1 =
        Objects.requireNonNull(output1.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();
    Path outputPath2 =
        Objects.requireNonNull(output2.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();

    assertTrue(result.getResult().isSuccess());
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertTrue(outputPath1.endsWith(Paths.get("foo", "bar1")));
    assertTrue(outputPath2.endsWith(Paths.get("foo", "bar2")));
  }

  @Test
  public void setsFileExecutable() throws ActionCreationException, IOException {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");

    ActionRegistryForTests registry = new ActionRegistryForTests(target, projectFilesystem);
    TestActionExecutionRunner runner = new TestActionExecutionRunner(projectFilesystem, target);
    Artifact output1 = runner.declareArtifact(Paths.get("foo").resolve("bar1"));
    Artifact output2 = runner.declareArtifact(Paths.get("foo").resolve("bar2"));
    ImmutableSet<Artifact> outputs = ImmutableSet.of(output1, output2);

    TestActionExecutionRunner.ExecutionDetails<WriteAction> result =
        runner.runAction(new WriteAction(registry, ImmutableSet.of(), outputs, "foobar", true));

    Path outputPath1 =
        Objects.requireNonNull(output1.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();
    Path outputPath2 =
        Objects.requireNonNull(output2.asBound().asBuildArtifact())
            .getSourcePath()
            .getResolvedPath();

    assertTrue(result.getResult().isSuccess());
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertEquals(
        Optional.of("foobar"),
        projectFilesystem.readFileIfItExists(projectFilesystem.resolve(outputPath1)));
    assertTrue(outputPath1.endsWith(Paths.get("foo", "bar1")));
    assertTrue(outputPath2.endsWith(Paths.get("foo", "bar2")));
    assertTrue(projectFilesystem.isExecutable(outputPath1));
    assertTrue(projectFilesystem.isExecutable(outputPath2));
  }
}
