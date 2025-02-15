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
package com.facebook.buck.parser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.cell.TestCellPathResolver;
import com.facebook.buck.core.description.RuleDescription;
import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.platform.ConstraintResolver;
import com.facebook.buck.core.model.platform.ConstraintSetting;
import com.facebook.buck.core.model.platform.ConstraintValue;
import com.facebook.buck.core.model.platform.Platform;
import com.facebook.buck.core.model.platform.PlatformResolver;
import com.facebook.buck.core.model.platform.impl.ConstraintBasedPlatform;
import com.facebook.buck.core.model.platform.impl.EmptyPlatform;
import com.facebook.buck.core.rules.actions.ActionCreationException;
import com.facebook.buck.core.rules.analysis.RuleAnalysisContext;
import com.facebook.buck.core.rules.config.registry.ConfigurationRuleRegistry;
import com.facebook.buck.core.rules.config.registry.ImmutableConfigurationRuleRegistry;
import com.facebook.buck.core.rules.knowntypes.KnownNativeRuleTypes;
import com.facebook.buck.core.rules.platform.ConstraintSettingRule;
import com.facebook.buck.core.rules.platform.ConstraintValueRule;
import com.facebook.buck.core.rules.platform.DummyConfigurationRule;
import com.facebook.buck.core.rules.platform.RuleBasedConstraintResolver;
import com.facebook.buck.core.rules.providers.ProviderInfoCollection;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.coercer.ConstructorArgBuilder;
import com.facebook.buck.rules.coercer.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;
import org.junit.Before;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class TargetCompatibilityCheckerTest {

  private final ConstraintSetting cs1 =
      ConstraintSetting.of(
          ConfigurationBuildTargetFactoryForTests.newInstance("//cs:cs1"), Optional.empty());
  private final ConstraintValue cs1v1 =
      ConstraintValue.of(ConfigurationBuildTargetFactoryForTests.newInstance("//cs:cs1v1"), cs1);
  private final ConstraintValue cs1v2 =
      ConstraintValue.of(ConfigurationBuildTargetFactoryForTests.newInstance("//cs:cs1v2"), cs1);

  private Platform platform;
  private ConfigurationRuleRegistry configurationRuleRegistry;
  private ConstraintBasedPlatform compatiblePlatform;
  private ConstraintBasedPlatform nonCompatiblePlatform;

  @Before
  public void setUp() {
    platform =
        new ConstraintBasedPlatform(
            BuildTargetFactory.newInstance("//platform:platform"), ImmutableSet.of(cs1v1));
    ConstraintResolver constraintResolver =
        new RuleBasedConstraintResolver(
            buildTarget -> {
              if (buildTarget.equals(cs1.getBuildTarget())) {
                return new ConstraintSettingRule(
                    buildTarget, buildTarget.getShortName(), Optional.empty());
              } else {
                return new ConstraintValueRule(
                    buildTarget, buildTarget.getShortName(), cs1.getBuildTarget());
              }
            });
    compatiblePlatform =
        new ConstraintBasedPlatform(
            BuildTargetFactory.newInstance("//platforms:p1"), ImmutableSet.of(cs1v1));
    nonCompatiblePlatform =
        new ConstraintBasedPlatform(
            BuildTargetFactory.newInstance("//platforms:p2"), ImmutableSet.of(cs1v2));
    PlatformResolver platformResolver =
        buildTarget -> {
          if (buildTarget.toString().equals(compatiblePlatform.toString())) {
            return compatiblePlatform;
          }
          if (buildTarget.toString().equals(nonCompatiblePlatform.toString())) {
            return nonCompatiblePlatform;
          }
          throw new IllegalArgumentException("Unknown platform: " + buildTarget);
        };
    configurationRuleRegistry =
        new ImmutableConfigurationRuleRegistry(
            DummyConfigurationRule::of,
            constraintResolver,
            platformResolver,
            configuration -> EmptyPlatform.INSTANCE);
  }

  @Test
  public void testTargetNodeIsCompatibleWithEmptyConstraintList() throws Exception {
    Object targetNodeArg = createTargetNodeArg(ImmutableMap.of());
    assertTrue(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsCompatibleWithMatchingConstraintList() throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatibleWith",
                ImmutableList.of(cs1v1.getBuildTarget().getFullyQualifiedName())));
    assertTrue(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsNotCompatibleWithNonMatchingConstraintList() throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatibleWith",
                ImmutableList.of(cs1v2.getBuildTarget().getFullyQualifiedName())));
    assertFalse(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsNotCompatibleWithNonMatchingPlatformAndNonMatchingConstraintList()
      throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms",
                ImmutableList.of(nonCompatiblePlatform.toString()),
                "targetCompatibleWith",
                ImmutableList.of(cs1v2.getBuildTarget().getFullyQualifiedName())));
    assertFalse(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsNotCompatibleWithNonMatchingPlatformListAndMatchingConstraintList()
      throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms",
                ImmutableList.of(nonCompatiblePlatform.toString()),
                "targetCompatibleWith",
                ImmutableList.of(cs1v1.getBuildTarget().getFullyQualifiedName())));
    assertFalse(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsNotCompatibleWithMatchingPlatformListAndNonMatchingConstraintList()
      throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms",
                ImmutableList.of(compatiblePlatform.toString()),
                "targetCompatibleWith",
                ImmutableList.of(cs1v2.getBuildTarget().getFullyQualifiedName())));
    assertFalse(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsNotCompatibleWithNonMatchingPlatformList() throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms", ImmutableList.of(nonCompatiblePlatform.toString())));
    assertFalse(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsCompatibleWithMatchingPlatformList() throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms",
                ImmutableList.of(compatiblePlatform.toString(), nonCompatiblePlatform.toString())));
    assertTrue(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  @Test
  public void testTargetNodeIsCompatibleWithMatchingPlatformListAndMatchingConstraintList()
      throws Exception {
    Object targetNodeArg =
        createTargetNodeArg(
            ImmutableMap.of(
                "targetCompatiblePlatforms",
                ImmutableList.of(compatiblePlatform.toString()),
                "targetCompatibleWith",
                ImmutableList.of(cs1v1.getBuildTarget().getFullyQualifiedName())));
    assertTrue(
        TargetCompatibilityChecker.targetNodeArgMatchesPlatform(
            configurationRuleRegistry, targetNodeArg, platform));
  }

  private Object createTargetNodeArg(Map<String, Object> rawNode) throws Exception {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    DefaultTypeCoercerFactory typeCoercerFactory = new DefaultTypeCoercerFactory();
    ConstructorArgMarshaller marshaller = new DefaultConstructorArgMarshaller(typeCoercerFactory);
    KnownNativeRuleTypes knownRuleTypes =
        KnownNativeRuleTypes.of(ImmutableList.of(new TestRuleDescription()), ImmutableList.of());

    BuildTarget buildTarget = BuildTargetFactory.newInstance(projectFilesystem, "//:target");

    ConstructorArgBuilder<TestDescriptionArg> builder =
        knownRuleTypes.getConstructorArgBuilder(
            typeCoercerFactory,
            knownRuleTypes.getRuleType("test_rule"),
            TestDescriptionArg.class,
            buildTarget);

    return marshaller.populate(
        TestCellPathResolver.get(projectFilesystem),
        projectFilesystem,
        buildTarget,
        builder,
        ImmutableSet.builder(),
        ImmutableMap.<String, Object>builder().putAll(rawNode).put("name", "target").build());
  }

  static class TestRuleDescription implements RuleDescription<AbstractTestDescriptionArg> {

    @Override
    public boolean producesCacheableSubgraph() {
      return false;
    }

    @Override
    public ProviderInfoCollection ruleImpl(
        RuleAnalysisContext context, BuildTarget target, AbstractTestDescriptionArg args)
        throws ActionCreationException {
      throw new NotImplementedException();
    }

    @Override
    public Class<AbstractTestDescriptionArg> getConstructorArgType() {
      return AbstractTestDescriptionArg.class;
    }
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractTestDescriptionArg extends CommonDescriptionArg {}
}
