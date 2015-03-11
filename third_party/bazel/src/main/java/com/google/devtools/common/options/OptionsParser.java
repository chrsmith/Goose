// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.devtools.common.options;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A parser for options. Typical use case in a main method:
 *
 * <pre>
 * OptionsParser parser = OptionsParser.newOptionsParser(FooOptions.class, BarOptions.class);
 * parser.parseAndExitUponError(args);
 * FooOptions foo = parser.getOptions(FooOptions.class);
 * BarOptions bar = parser.getOptions(BarOptions.class);
 * List&lt;String&gt; otherArguments = parser.getResidue();
 * </pre>
 *
 * <p>FooOptions and BarOptions would be options specification classes, derived
 * from OptionsBase, that contain fields annotated with @Option(...).
 *
 * <p>Alternatively, rather than calling
 * {@link #parseAndExitUponError(OptionPriority, String, String[])},
 * client code may call {@link #parse(OptionPriority,String,List)}, and handle
 * parser exceptions usage messages themselves.
 *
 * <p>This options parsing implementation has (at least) one design flaw. It
 * allows both '--foo=baz' and '--foo baz' for all options except void, boolean
 * and tristate options. For these, the 'baz' in '--foo baz' is not treated as
 * a parameter to the option, making it is impossible to switch options between
 * void/boolean/tristate and everything else without breaking backwards
 * compatibility.
 *
 * @see Options a simpler class which you can use if you only have one options
 * specification class
 */
public class OptionsParser implements OptionsProvider {

  /**
   * A cache for the parsed options data. Both keys and values are immutable, so
   * this is always safe. Only access this field through the {@link
   * #getOptionsData} method for thread-safety! The cache is very unlikely to
   * grow to a significant amount of memory, because there's only a fixed set of
   * options classes on the classpath.
   */
  private static final Map<ImmutableList<Class<? extends OptionsBase>>, OptionsData> optionsData =
      Maps.newHashMap();

  private static synchronized OptionsData getOptionsData(
      ImmutableList<Class<? extends OptionsBase>> optionsClasses) {
    OptionsData result = optionsData.get(optionsClasses);
    if (result == null) {
      result = OptionsData.of(optionsClasses);
      optionsData.put(optionsClasses, result);
    }
    return result;
  }

  /**
   * Returns all the annotated fields for the given class, including inherited
   * ones.
   */
  static Collection<Field> getAllAnnotatedFields(Class<? extends OptionsBase> optionsClass) {
    OptionsData data = getOptionsData(ImmutableList.<Class<? extends OptionsBase>>of(optionsClass));
    return data.getFieldsForClass(optionsClass);
  }

  /**
   * @see #newOptionsParser(Iterable)
   */
  public static OptionsParser newOptionsParser(Class<? extends OptionsBase> class1) {
    return newOptionsParser(ImmutableList.<Class<? extends OptionsBase>>of(class1));
  }

  /**
   * @see #newOptionsParser(Iterable)
   */
  public static OptionsParser newOptionsParser(Class<? extends OptionsBase> class1,
                                               Class<? extends OptionsBase> class2) {
    return newOptionsParser(ImmutableList.of(class1, class2));
  }

  /**
   * Create a new {@link OptionsParser}.
   */
  public static OptionsParser newOptionsParser(
      Iterable<Class<? extends OptionsBase>> optionsClasses) {
    return new OptionsParser(getOptionsData(ImmutableList.copyOf(optionsClasses)));
  }

  /**
   * Canonicalizes a list of options using the given option classes. The
   * contract is that if the returned set of options is passed to an options
   * parser with the same options classes, then that will have the same effect
   * as using the original args (which are passed in here), except for cosmetic
   * differences.
   */
  public static List<String> canonicalize(
      Collection<Class<? extends OptionsBase>> optionsClasses, List<String> args)
      throws OptionsParsingException {
    OptionsParser parser = new OptionsParser(optionsClasses);
    parser.setAllowResidue(false);
    parser.parse(args);
    return parser.impl.asCanonicalizedList();
  }

  private final OptionsParserImpl impl;
  private final List<String> residue = new ArrayList<String>();
  private boolean allowResidue = true;

  OptionsParser(Collection<Class<? extends OptionsBase>> optionsClasses) {
    this(OptionsData.of(optionsClasses));
  }

  OptionsParser(OptionsData optionsData) {
    impl = new OptionsParserImpl(optionsData);
  }

  /**
   * Indicates whether or not the parser will allow a non-empty residue; that
   * is, iff this value is true then a call to one of the {@code parse}
   * methods will throw {@link OptionsParsingException} unless
   * {@link #getResidue()} is empty after parsing.
   */
  public void setAllowResidue(boolean allowResidue) {
    this.allowResidue = allowResidue;
  }

  /**
   * Indicates whether or not the parser will allow long options with a
   * single-dash, instead of the usual double-dash, too, eg. -example instead of just --example.
   */
  public void setAllowSingleDashLongOptions(boolean allowSingleDashLongOptions) {
    this.impl.setAllowSingleDashLongOptions(allowSingleDashLongOptions);
  }

  public void parseAndExitUponError(String[] args) {
    parseAndExitUponError(OptionPriority.COMMAND_LINE, "unknown", args);
  }

  /**
   * A convenience function for use in main methods. Parses the command line
   * parameters, and exits upon error. Also, prints out the usage message
   * if "--help" appears anywhere within {@code args}.
   */
  public void parseAndExitUponError(OptionPriority priority, String source, String[] args) {
    try {
      parse(priority, source, Arrays.asList(args));
    } catch (OptionsParsingException e) {
      System.err.println("Error parsing command line: " + e.getMessage());
      System.err.println("Try --help.");
      System.exit(2);
    }
    for (String arg : args) {
      if (arg.equals("--help")) {
        System.out.println(describeOptions(Collections.<String, String>emptyMap(),
                                           HelpVerbosity.LONG));
        System.exit(0);
      }
    }
  }

  /**
   * The name and value of an option with additional metadata describing its
   * priority, source, whether it was set via an implicit dependency, and if so,
   * by which other option.
   */
  public static class OptionValueDescription {
    private final String name;
    private final Object value;
    private final OptionPriority priority;
    private final String source;
    private final String implicitDependant;
    private final String expandedFrom;

    public OptionValueDescription(String name, Object value,
        OptionPriority priority, String source, String implicitDependant, String expandedFrom) {
      this.name = name;
      this.value = value;
      this.priority = priority;
      this.source = source;
      this.implicitDependant = implicitDependant;
      this.expandedFrom = expandedFrom;
    }

    public String getName() {
      return name;
    }

    public Object getValue() {
      return value;
    }

    public OptionPriority getPriority() {
      return priority;
    }

    public String getSource() {
      return source;
    }

    public String getImplicitDependant() {
      return implicitDependant;
    }

    public boolean isImplicitDependency() {
      return implicitDependant != null;
    }

    public String getExpansionParent() {
      return expandedFrom;
    }

    public boolean isExpansion() {
      return expandedFrom != null;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("option '").append(name).append("' ");
      result.append("set to '").append(value).append("' ");
      result.append("with priority ").append(priority);
      if (source != null) {
        result.append(" and source '").append(source).append("'");
      }
      if (implicitDependant != null) {
        result.append(" implicitly by ");
      }
      return result.toString();
    }
  }

  /**
   * The name and unparsed value of an option with additional metadata describing its
   * priority, source, whether it was set via an implicit dependency, and if so,
   * by which other option.
   *
   * <p>Note that the unparsed value and the source parameters can both be null.
   */
  public static class UnparsedOptionValueDescription {
    private final String name;
    private final Field field;
    private final String unparsedValue;
    private final OptionPriority priority;
    private final String source;
    private final boolean explicit;

    public UnparsedOptionValueDescription(String name, Field field, String unparsedValue,
        OptionPriority priority, String source, boolean explicit) {
      this.name = name;
      this.field = field;
      this.unparsedValue = unparsedValue;
      this.priority = priority;
      this.source = source;
      this.explicit = explicit;
    }

    public String getName() {
      return name;
    }

    Field getField() {
      return field;
    }

    public boolean isBooleanOption() {
      return field.getType().equals(boolean.class);
    }

    private DocumentationLevel documentationLevel() {
      Option option = field.getAnnotation(Option.class);
      return OptionsParser.documentationLevel(option.category());
    }

    public boolean isDocumented() {
      return documentationLevel() == DocumentationLevel.DOCUMENTED;
    }

    public boolean isHidden() {
      return documentationLevel() == DocumentationLevel.HIDDEN;
    }

    boolean isExpansion() {
      Option option = field.getAnnotation(Option.class);
      return option.expansion().length > 0;
    }

    boolean isImplicitRequirement() {
      Option option = field.getAnnotation(Option.class);
      return option.implicitRequirements().length > 0;
    }

    boolean allowMultiple() {
      Option option = field.getAnnotation(Option.class);
      return option.allowMultiple();
    }

    public String getUnparsedValue() {
      return unparsedValue;
    }

    OptionPriority getPriority() {
      return priority;
    }

    public String getSource() {
      return source;
    }

    public boolean isExplicit() {
      return explicit;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("option '").append(name).append("' ");
      result.append("set to '").append(unparsedValue).append("' ");
      result.append("with priority ").append(priority);
      if (source != null) {
        result.append(" and source '").append(source).append("'");
      }
      return result.toString();
    }
  }

  /**
   * The verbosity with which option help messages are displayed: short (just
   * the name), medium (name, type, default, abbreviation), and long (full
   * description).
   */
  public enum HelpVerbosity { LONG, MEDIUM, SHORT }

  /**
   * The level of documentation. Only documented options are output as part of
   * the help.
   *
   * <p>We use 'hidden' so that options that form the protocol between the
   * client and the server are not logged.
   */
  enum DocumentationLevel {
    DOCUMENTED, UNDOCUMENTED, HIDDEN
  }

  /**
   * Returns a description of all the options this parser can digest.
   * In addition to {@link Option} annotations, this method also
   * interprets {@link OptionsUsage} annotations which give an intuitive short
   * description for the options.
   *
   * @param categoryDescriptions a mapping from category names to category
   *   descriptions.  Options of the same category (see {@link
   *   Option#category}) will be grouped together, preceded by the description
   *   of the category.
   * @param helpVerbosity if {@code long}, the options will be described
   *   verbosely, including their types, defaults and descriptions.  If {@code
   *   medium}, the descriptions are omitted, and if {@code short}, the options
   *   are just enumerated.
   */
  public String describeOptions(Map<String, String> categoryDescriptions,
                                HelpVerbosity helpVerbosity) {
    StringBuilder desc = new StringBuilder();
    if (!impl.getOptionsClasses().isEmpty()) {

      List<Field> allFields = Lists.newArrayList();
      for (Class<? extends OptionsBase> optionsClass : impl.getOptionsClasses()) {
        allFields.addAll(impl.getAnnotatedFieldsFor(optionsClass));
      }
      Collections.sort(allFields, OptionsUsage.BY_CATEGORY);
      String prevCategory = null;

      for (Field optionField : allFields) {
        String category = optionField.getAnnotation(Option.class).category();
        if (!category.equals(prevCategory)) {
          prevCategory = category;
          String description = categoryDescriptions.get(category);
          if (description == null) {
            description = "Options category '" + category + "'";
          }
          if (documentationLevel(category) == DocumentationLevel.DOCUMENTED) {
            desc.append("\n").append(description).append(":\n");
          }
        }

        if (documentationLevel(prevCategory) == DocumentationLevel.DOCUMENTED) {
          OptionsUsage.getUsage(optionField, desc, helpVerbosity);
        }
      }
    }
    return desc.toString().trim();
  }

  /**
   * Returns a description of the option value set by the last previous call to
   * {@link #parse(OptionPriority, String, List)} that successfully set the given
   * option. If the option is of type {@link List}, the description will
   * correspond to any one of the calls, but not necessarily the last.
   */
  public OptionValueDescription getOptionValueDescription(String name) {
    return impl.getOptionValueDescription(name);
  }

  static DocumentationLevel documentationLevel(String category) {
    if ("undocumented".equals(category)) {
      return DocumentationLevel.UNDOCUMENTED;
    } else if ("hidden".equals(category)) {
      return DocumentationLevel.HIDDEN;
    } else {
      return DocumentationLevel.DOCUMENTED;
    }
  }

  /**
   * A convenience method, equivalent to
   * {@code parse(OptionPriority.COMMAND_LINE, null, Arrays.asList(args))}.
   */
  public void parse(String... args) throws OptionsParsingException {
    parse(OptionPriority.COMMAND_LINE, (String) null, Arrays.asList(args));
  }

  /**
   * A convenience method, equivalent to
   * {@code parse(OptionPriority.COMMAND_LINE, null, args)}.
   */
  public void parse(List<String> args) throws OptionsParsingException {
    parse(OptionPriority.COMMAND_LINE, (String) null, args);
  }

  /**
   * Parses {@code args}, using the classes registered with this parser.
   * {@link #getOptions(Class)} and {@link #getResidue()} return the results.
   * May be called multiple times; later options override existing ones if they
   * have equal or higher priority. The source of options is a free-form string
   * that can be used for debugging. Strings that cannot be parsed as options
   * accumulates as residue, if this parser allows it.
   *
   * @see OptionPriority
   */
  public void parse(OptionPriority priority, String source,
      List<String> args) throws OptionsParsingException {
    parseWithSourceFunction(priority, Functions.constant(source), args);
  }

  /**
   * Parses {@code args}, using the classes registered with this parser.
   * {@link #getOptions(Class)} and {@link #getResidue()} return the results. May be called
   * multiple times; later options override existing ones if they have equal or higher priority.
   * The source of options is given as a function that maps option names to the source of the
   * option. Strings that cannot be parsed as options accumulates as* residue, if this parser
   * allows it.
   */
  public void parseWithSourceFunction(OptionPriority priority,
      Function<? super String, String> sourceFunction, List<String> args)
      throws OptionsParsingException {
    Preconditions.checkNotNull(priority);
    Preconditions.checkArgument(priority != OptionPriority.DEFAULT);
    residue.addAll(impl.parse(priority, sourceFunction, args));
    if (!allowResidue && !residue.isEmpty()) {
      String errorMsg = "Unrecognized arguments: " + Joiner.on(' ').join(residue);
      throw new OptionsParsingException(errorMsg);
    }
  }

  @Override
  public List<String> getResidue() {
    return ImmutableList.copyOf(residue);
  }

  /**
   * Returns a list of warnings about problems encountered by previous parse calls.
   */
  public List<String> getWarnings() {
    return impl.getWarnings();
  }

  @Override
  public <O extends OptionsBase> O getOptions(Class<O> optionsClass) {
    return impl.getParsedOptions(optionsClass);
  }

  @Override
  public boolean containsExplicitOption(String name) {
    return impl.containsExplicitOption(name);
  }

  @Override
  public List<UnparsedOptionValueDescription> asListOfUnparsedOptions() {
    return impl.asListOfUnparsedOptions();
  }

  @Override
  public List<UnparsedOptionValueDescription> asListOfExplicitOptions() {
    return impl.asListOfExplicitOptions();
  }

  @Override
  public List<OptionValueDescription> asListOfEffectiveOptions() {
    return impl.asListOfEffectiveOptions();
  }
}