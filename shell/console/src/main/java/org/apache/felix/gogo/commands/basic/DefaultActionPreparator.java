/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.gogo.commands.basic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jline.Terminal;
import org.apache.felix.gogo.commands.CommandException;
import org.apache.felix.gogo.commands.Option;
import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.converter.DefaultConverter;
import org.apache.felix.gogo.commands.converter.GenericType;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.console.NameScoping;
import org.fusesource.jansi.Ansi;

public class DefaultActionPreparator implements ActionPreparator {

    public static final Option HELP = new Option() {
        public String name()
        {
            return "--help";
        }

        public String[] aliases()
        {
            return new String[] { };
        }

        public String description()
        {
            return "Display this help message";
        }

        public boolean required()
        {
            return false;
        }

        public boolean multiValued()
        {
            return false;
        }

        public String valueToShowInHelp()
        {
            return Option.DEFAULT_STRING;
        }

        public Class<? extends Annotation> annotationType()
        {
            return Option.class;
        }
    };

    public boolean prepare(Action action, CommandSession session, List<Object> params) throws Exception
    {
        Map<Option, Field> options = new HashMap<Option, Field>();
        Map<Argument, Field> arguments = new HashMap<Argument, Field>();
        List<Argument> orderedArguments = new ArrayList<Argument>();
        // Introspect
        for (Class type = action.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Option option = field.getAnnotation(Option.class);
                if (option != null) {
                    options.put(option, field);
                }
                Argument argument = field.getAnnotation(Argument.class);
                if (argument != null) {
                    if (Argument.DEFAULT.equals(argument.name())) {
                        final Argument delegate = argument;
                        final String name = field.getName();
                        argument = new Argument() {
                            public String name() {
                                return name;
                            }
                            public String description() {
                                return delegate.description();
                            }
                            public boolean required() {
                                return delegate.required();
                            }
                            public int index() {
                                return delegate.index();
                            }
                            public boolean multiValued() {
                                return delegate.multiValued();
                            }
                            public String valueToShowInHelp() {
                                return delegate.valueToShowInHelp();
                            }
                            public Class<? extends Annotation> annotationType() {
                                return delegate.annotationType();
                            }
                        };
                    }
                    arguments.put(argument, field);
                    int index = argument.index();
                    while (orderedArguments.size() <= index) {
                        orderedArguments.add(null);
                    }
                    if (orderedArguments.get(index) != null) {
                        throw new IllegalArgumentException("Duplicate argument index: " + index);
                    }
                    orderedArguments.set(index, argument);
                }
            }
        }
        // Check indexes are correct
        for (int i = 0; i < orderedArguments.size(); i++) {
            if (orderedArguments.get(i) == null) {
                throw new IllegalArgumentException("Missing argument for index: " + i);
            }
        }
        // Populate
        Map<Option, Object> optionValues = new HashMap<Option, Object>();
        Map<Argument, Object> argumentValues = new HashMap<Argument, Object>();
        boolean processOptions = true;
        int argIndex = 0;
        for (Iterator<Object> it = params.iterator(); it.hasNext();) {
            Object param = it.next();
            // Check for help
            if (HELP.name().equals(param) || Arrays.asList(HELP.aliases()).contains(param)) {
                printUsage(session, action, options, arguments, System.out);
                return false;
            }
            if (processOptions && param instanceof String && ((String) param).startsWith("-")) {
                boolean isKeyValuePair = ((String) param).indexOf('=') != -1;
                String name;
                Object value = null;
                if (isKeyValuePair) {
                    name = ((String) param).substring(0, ((String) param).indexOf('='));
                    value = ((String) param).substring(((String) param).indexOf('=') + 1);
                } else {
                    name = (String) param;
                }
                Option option = null;
                for (Option opt : options.keySet()) {
                    if (name.equals(opt.name()) || Arrays.asList(opt.aliases()).contains(name)) {
                        option = opt;
                        break;
                    }
                }
                if (option == null) {
                    Command command = action.getClass().getAnnotation(Command.class);
                    throw new CommandException(
                            Ansi.ansi()
                                    .fg(Ansi.Color.RED)
                                    .a("Error executing command ")
                                    .a(command.scope())
                                    .a(":")
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(command.name())
                                    .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                    .a(" undefined option ")
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(param)
                                    .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                    .fg(Ansi.Color.DEFAULT)
                                    .toString(),
                            "Undefined option: " + param
                    );
                }
                Field field = options.get(option);
                if (value == null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                    value = Boolean.TRUE;
                }
                if (value == null && it.hasNext()) {
                    value = it.next();
                }
                if (value == null) {
                    Command command = action.getClass().getAnnotation(Command.class);
                    throw new CommandException(
                            Ansi.ansi()
                                    .fg(Ansi.Color.RED)
                                    .a("Error executing command ")
                                    .a(command.scope())
                                    .a(":")
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(command.name())
                                    .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                    .a(" missing value for option ")
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(param)
                                    .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                    .fg(Ansi.Color.DEFAULT)
                                    .toString(),
                            "Missing value for option: " + param
                    );
                }
                if (option.multiValued()) {
                    List<Object> l = (List<Object>) optionValues.get(option);
                    if (l == null) {
                        l = new ArrayList<Object>();
                        optionValues.put(option,  l);
                    }
                    l.add(value);
                } else {
                    optionValues.put(option, value);
                }
            } else {
                processOptions = false;
                if (argIndex >= orderedArguments.size()) {
                    Command command = action.getClass().getAnnotation(Command.class);
                    throw new CommandException(
                            Ansi.ansi()
                                    .fg(Ansi.Color.RED)
                                    .a("Error executing command ")
                                    .a(command.scope())
                                    .a(":")
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(command.name())
                                    .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                    .a(": too many arguments specified")
                                    .fg(Ansi.Color.DEFAULT)
                                    .toString(),
                            "Too many arguments specified"
                    );
                }
                Argument argument = orderedArguments.get(argIndex);
                if (!argument.multiValued()) {
                    argIndex++;
                }
                if (argument.multiValued()) {
                    List<Object> l = (List<Object>) argumentValues.get(argument);
                    if (l == null) {
                        l = new ArrayList<Object>();
                        argumentValues.put(argument, l);
                    }
                    l.add(param);
                } else {
                    argumentValues.put(argument, param);
                }
            }
        }
        // Check required arguments / options
        for (Option option : options.keySet()) {
            if (option.required() && optionValues.get(option) == null) {
                Command command = action.getClass().getAnnotation(Command.class);
                throw new CommandException(
                        Ansi.ansi()
                                .fg(Ansi.Color.RED)
                                .a("Error executing command ")
                                .a(command.scope())
                                .a(":")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(command.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(": option ")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(option.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(" is required")
                                .fg(Ansi.Color.DEFAULT)
                                .toString(),
                        "Option " + option.name() + " is required"
                );
            }
        }
        for (Argument argument : arguments.keySet()) {
            if (argument.required() && argumentValues.get(argument) == null) {
                Command command = action.getClass().getAnnotation(Command.class);
                throw new CommandException(
                        Ansi.ansi()
                                .fg(Ansi.Color.RED)
                                .a("Error executing command ")
                                .a(command.scope())
                                .a(":")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(command.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(": argument ")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(argument.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(" is required")
                                .fg(Ansi.Color.DEFAULT)
                                .toString(),
                        "Argument " + argument.name() + " is required"
                );
            }
        }
        // Convert and inject values
        for (Map.Entry<Option, Object> entry : optionValues.entrySet()) {
            Field field = options.get(entry.getKey());
            Object value;
            try {
                value = convert(action, session, entry.getValue(), field.getGenericType());
            } catch (Exception e) {
                Command command = action.getClass().getAnnotation(Command.class);
                throw new CommandException(
                        Ansi.ansi()
                                .fg(Ansi.Color.RED)
                                .a("Error executing command ")
                                .a(command.scope())
                                .a(":")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(command.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(": unable to convert option ")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(entry.getKey().name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(" with value '")
                                .a(entry.getValue())
                                .a("' to type ")
                                .a(new GenericType(field.getGenericType()).toString())
                                .fg(Ansi.Color.DEFAULT)
                                .toString(),
                        "Unable to convert option " + entry.getKey().name() + " with value '"
                                + entry.getValue() + "' to type " + new GenericType(field.getGenericType()).toString(),
                        e
                );
            }
            field.setAccessible(true);
            field.set(action, value);
        }
        for (Map.Entry<Argument, Object> entry : argumentValues.entrySet()) {
            Field field = arguments.get(entry.getKey());
            Object value;
            try {
                value = convert(action, session, entry.getValue(), field.getGenericType());
            } catch (Exception e) {
                Command command = action.getClass().getAnnotation(Command.class);
                throw new CommandException(
                        Ansi.ansi()
                                .fg(Ansi.Color.RED)
                                .a("Error executing command ")
                                .a(command.scope())
                                .a(":")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(command.name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(": unable to convert argument ")
                                .a(Ansi.Attribute.INTENSITY_BOLD)
                                .a(entry.getKey().name())
                                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                                .a(" with value '")
                                .a(entry.getValue())
                                .a("' to type ")
                                .a(new GenericType(field.getGenericType()).toString())
                                .fg(Ansi.Color.DEFAULT)
                                .toString(),
                        "Unable to convert argument " + entry.getKey().name() + " with value '" 
                                + entry.getValue() + "' to type " + new GenericType(field.getGenericType()).toString(),
                        e
                );
            }
            field.setAccessible(true);
            field.set(action, value);
        }
        return true;
    }

    protected void printUsage(CommandSession session, Action action, Map<Option,Field> optionsMap, Map<Argument,Field> argsMap, PrintStream out)
    {
        Command command = action.getClass().getAnnotation(Command.class);
        Terminal term = session != null ? (Terminal) session.get(".jline.terminal") : null;
        List<Argument> arguments = new ArrayList<Argument>(argsMap.keySet());
        Collections.sort(arguments, new Comparator<Argument>() {
            public int compare(Argument o1, Argument o2) {
                return Integer.valueOf(o1.index()).compareTo(Integer.valueOf(o2.index()));
            }
        });
        Set<Option> options = new HashSet<Option>(optionsMap.keySet());
        options.add(HELP);
        boolean globalScope = NameScoping.isGlobalScope(session, command.scope());
        if (command != null && (command.description() != null || command.name() != null))
        {
            out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a("DESCRIPTION").a(Ansi.Attribute.RESET));
            out.print("        ");
            if (command.name() != null) {
                if (globalScope) {
                    out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a(command.name()).a(Ansi.Attribute.RESET));
                } else {
                    out.println(Ansi.ansi().a(command.scope()).a(":").a(Ansi.Attribute.INTENSITY_BOLD).a(command.name()).a(Ansi.Attribute.RESET));
                }
                out.println();
            }
            out.print("\t");
            out.println(command.description());
            out.println();
        }
        StringBuffer syntax = new StringBuffer();
        if (command != null)
        {
            if (globalScope) {
                syntax.append(command.name());
            } else {
                syntax.append(String.format("%s:%s", command.scope(), command.name()));
            }
        }
        if (options.size() > 0)
        {
            syntax.append(" [options]");
        }
        if (arguments.size() > 0)
        {
            syntax.append(' ');
            for (Argument argument : arguments)
            {
                if (!argument.required())
                {
                    syntax.append(String.format("[%s] ", argument.name()));
                }
                else
                {
                    syntax.append(String.format("%s ", argument.name()));
                }
            }
        }

        out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a("SYNTAX").a(Ansi.Attribute.RESET));
        out.print("        ");
        out.println(syntax.toString());
        out.println();
        if (arguments.size() > 0)
        {
            out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a("ARGUMENTS").a(Ansi.Attribute.RESET));
            for (Argument argument : arguments)
            {
                out.print("        ");
                out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a(argument.name()).a(Ansi.Attribute.RESET));
                printFormatted("                ", argument.description(), term != null ? term.getWidth() : 80, out);
                if (!argument.required()) {
                    if (argument.valueToShowInHelp() != null && argument.valueToShowInHelp().length() != 0) {
                         try {
                            if (Argument.DEFAULT_STRING.equals(argument.valueToShowInHelp())) {
                                argsMap.get(argument).setAccessible(true);
                                Object o = argsMap.get(argument).get(action);
                                printObjectDefaultsTo(out, o);
                            } else {
                                printDefaultsTo(out, argument.valueToShowInHelp());
                            }
                        } catch (Throwable t) {
                            // Ignore
                        }
                    }
                }
            }
            out.println();
        }
        if (options.size() > 0)
        {
            out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a("OPTIONS").a(Ansi.Attribute.RESET));
            for (Option option : options)
            {
                String opt = option.name();
                for (String alias : option.aliases())
                {
                    opt += ", " + alias;
                }
                out.print("        ");
                out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a(opt).a(Ansi.Attribute.RESET));
                printFormatted("                ", option.description(), term != null ? term.getWidth() : 80, out);
                if (option.valueToShowInHelp() != null && option.valueToShowInHelp().length() != 0) {
                    try {
                        if(Option.DEFAULT_STRING.equals(option.valueToShowInHelp())) {
                            optionsMap.get(option).setAccessible(true);
                            Object o = optionsMap.get(option).get(action);
                            printObjectDefaultsTo(out, o);
                        } else {
                            printDefaultsTo(out, option.valueToShowInHelp());
                        }
                    } catch (Throwable t) {
                        // Ignore
                    }
                }
            }
            out.println();
        }
        if (command.detailedDescription().length() > 0) {
            out.println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a("DETAILS").a(Ansi.Attribute.RESET));
            String desc = loadDescription(action.getClass(), command.detailedDescription());
            printFormatted("        ", desc, term != null ? term.getWidth() : 80, out);
        }
    }

    private void printObjectDefaultsTo(PrintStream out, Object o) {
        if (o != null
                && (!(o instanceof Boolean) || ((Boolean) o))
                && (!(o instanceof Number) || ((Number) o).doubleValue() != 0.0)) {
            printDefaultsTo(out, o.toString());
        }
    }

    private void printDefaultsTo(PrintStream out, String value) {
        out.print("                (defaults to ");
        out.print(value);
        out.println(")");
    }

    protected String loadDescription(Class clazz, String desc) {
        if (desc.startsWith("classpath:")) {
            InputStream is = clazz.getResourceAsStream(desc.substring("classpath:".length()));
            if (is == null) {
                is = clazz.getClassLoader().getResourceAsStream(desc.substring("classpath:".length()));
            }
            if (is == null) {
                desc = "Unable to load description from " + desc;
            } else {
                try {
                    Reader r = new InputStreamReader(is);
                    StringWriter sw = new StringWriter();
                    int c;
                    while ((c = r.read()) != -1) {
                        sw.append((char) c);
                    }
                    desc = sw.toString();
                } catch (IOException e) {
                    desc = "Unable to load description from " + desc;
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
        return desc;
    }

    // TODO move this to a helper class?
    public static void printFormatted(String prefix, String str, int termWidth, PrintStream out) {
        int pfxLen = length(prefix);
        int maxwidth = termWidth - pfxLen;
        Pattern wrap = Pattern.compile("(\\S\\S{" + maxwidth + ",}|.{1," + maxwidth + "})(\\s+|$)");
        Matcher m = wrap.matcher(str);
        while (m.find()) {
            out.print(prefix);
            out.println(m.group());
        }
    }

    public static int length(String str) {
        return str.length();
    }

    protected Object convert(Action action, CommandSession session, Object value, Type toType) throws Exception
    {
        if (toType == String.class) {
            return value != null ? value.toString() : null;
        }
        return new DefaultConverter(action.getClass().getClassLoader()).convert(value, toType);
    }

}
