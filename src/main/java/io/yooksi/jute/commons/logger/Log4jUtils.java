/*
 * Copyright [2019] [Matthew Cain]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.yooksi.jute.commons.logger;

import io.yooksi.jute.commons.define.MethodsNotNull;
import io.yooksi.jute.commons.util.ReflectionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;

@MethodsNotNull
@SuppressWarnings({"unused", "WeakerAccess"})
public final class Log4jUtils {

    /* Make the constructor private to disable instantiation */
    private Log4jUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param logger name of the logger to construct a path for
     * @return a default path to a log file for the given logger name
     */
    public static String getStandardLogFilePath(String logger) {
        return "logs/" + logger + ".log";
    }

    /**
     * Create a return a new {@code ConsoleAppender} instance.
     *
     * @param loggerConfig logger this appender should be added to
     * @param level log event threshold
     * @param layout format to use when logging messages
     * @param filter {@code Filter} the new appender will use
     * @param initialize whether the appender should be initialized or not
     *
     * @see #initializeAppender(LoggerConfig, Appender, Level, Filter)
     */
    public static ConsoleAppender createNewConsoleAppender(LoggerConfig loggerConfig, Level level,
                                                           Layout<? extends java.io.Serializable> layout,
                                                           @Nullable Filter filter, boolean initialize) {

        CommonLogger.LOGGER.debug("Creating new ConsoleAppender for LoggerConfig %s", loggerConfig.getName());

        String name = AppenderType.CONSOLE.getPrimaryName();
        ConsoleAppender consoleAppender = ConsoleAppender.newBuilder().setName(name).setLayout(layout).setFilter(filter).build();
        return initialize ? initializeAppender(loggerConfig, consoleAppender, level, filter) : consoleAppender;
    }

    /**
     * Create a return a new {@code ConsoleAppender} instance with default patten layout.
     *
     * @param logger used to get {@code Configuration} and {@code LoggerConfig} from
     * @param level log event threshold
     * @param filter {@code Filter} the new appender will use
     * @param initialize whether the appender should be initialized or not
     *
     * @see #createNewConsoleAppender(LoggerConfig, Level, Layout, Filter, boolean)
     * @see #createPatternLayout(String, Configuration)
     */
    public static ConsoleAppender createNewConsoleAppender(LoggerControl logger, Level level, @Nullable Filter filter, boolean initialize) {

        PatternLayout layout = createPatternLayout(PatternLayout.SIMPLE_CONVERSION_PATTERN, logger.getConfiguration());
        return createNewConsoleAppender(logger.getLoggerConfig(), level, layout, filter, initialize);
    }

    /**
     * Construct a new {@code PatternLayout} with provided {@code pattern} and {@code config}
     *
     * @param pattern The pattern. If not specified, defaults to {@code DEFAULT_CONVERSION_PATTERN}.
     * @param config The Configuration. Some Converters require access to the Interpolator.
     * @return the newly constructed {@code PatternLayout}.
     */
    public static PatternLayout createPatternLayout(String pattern, Configuration config) {
        return PatternLayout.newBuilder().withPattern(pattern).withConfiguration(config).build();
    }

    /**
     * Create a return a new {@code FileAppender} instance.
     *
     * @param loggerConfig logger this appender should be added to
     * @param level log event threshold
     * @param layout format to use when logging messages
     * @param logFilePath path to the logfile this appender will use
     * @param filter {@code Filter} the new appender will use
     * @param initialize whether the appender should be initialized or not
     *
     * @throws ExceptionInInitializerError when tyring to create a new {@code FileAppender} with a logfile
     *                                     path that another appender with a different class is already using.
     *
     * @see #initializeAppender(LoggerConfig, Appender, Level, Filter)
     */
    public static FileAppender createNewFileAppender(LoggerConfig loggerConfig, Level level, Layout<? extends Serializable> layout,
                                                     String logFilePath, @Nullable Filter filter, boolean initialize) throws ExceptionInInitializerError {

        CommonLogger.LOGGER.debug( "Creating new FileAppender for LoggerConfig %s", loggerConfig.getName());

        String name = AppenderType.FILE.getPrimaryName();
        FileAppender fileAppender = FileAppender.newBuilder().setName(name).withFileName(logFilePath).setLayout(layout).setFilter(filter).build();
        return initialize ? initializeAppender(loggerConfig, fileAppender, level, filter) : fileAppender;
    }

    /**
     * This method will try to do the following things in the designated order:
     * <ul>
     *     <li>Recursively search through {@code LoggerConfig} tree for an appender
     *         that matches {@code AppenderType} class and one of the designated type names.
     *     <ul>
     *         <li>If no matching appender was found it will either be initialized or constructed
     *             for the {@code LoggerConfig} provided in the initialization package.
     *     </ul>
     *     <li>If a match has been found one of the following will happen:</li>
     *     <ul>
     *         <li>The matched appender will be returned within an {@code AppenderData} object.</li>
     *         <li>A new <b>additive appender</b> will be constructed and returned if:
     *         <ul>
     *             <li>the match <u>is being filtered</u> by the first {@code LoggerConfig} parent
     *             matching {@code AppenderType} class and name.
     *             <li>the match <u>is filtering</u> {@code AppenderData} provided in the initialization package.
     *         </ul>
     *     </ul>
     * </ul>
     * @param data contains information needed to find, initialize or construct an appender.
     * @param <T> Appender implementation type
     * @return {@code AppenderData} that was a result of search, initialization or construction.
     *         The origin of the object is not disclosed and needs to be externally resolved.
     */
    static <T extends Appender> AppenderData<T> getOrSetupAppender(final InitializationPackage<T> data) {

        final LoggerConfig loggerConfig = data.loggerControl.getLoggerConfig();
        final Configuration config = data.loggerControl.getConfiguration();
        /*
         * Recursively search through LoggerConfig tree for an appender match.
         * A null result means that neither the current LoggerConfig nor any of it's
         * parents contain the appender with a type and name contained in the package.
         */
        AppenderData<T> appenderData = findAppender(loggerConfig, data.type);
        if (appenderData == null)
        {
            CommonLogger.LOGGER.warn("Unable to find reachable %s in LoggerConfig %s",
                    data.type.toString(), loggerConfig.getName());

            Appender result;
            /*
             * Search through the Log4j Configuration for an appender match.
             * A null result means there is no available appenders of that type
             * and name present within the configuration file.
             */
            Appender configAppender = findAppender(config, data.type);

            if (configAppender != null)
            {
                /*
                 * File appender with a dedicated log file will need to be constructed,
                 * as the default appender found in Configuration has a different log file path.
                 */
                result = data.isDedicatedFileAppender(new AppenderData<>(configAppender, data)) ?
                        constructAppender(data.copyWithLayout(configAppender.getLayout()), null) :
                        initializeAppender(loggerConfig, configAppender, data.level, null);
            }
            /*
             * Since no matching appender was found in neither of the LoggerConfig parents
             * or the supplied Configuration file we have to construct a new one
             * with properties defined in the initialization package.
             */
            else {
                result = constructAppender(data, null);
            }
            return new AppenderData<>(loggerConfig, result, data.type, data.level);
        }
        else if (appenderData.isLoggerConfig(loggerConfig))
        {
            if (loggerConfig.isAdditive() && loggerConfig.getParent() != null)
            {
                Level level = appenderData.getLevel();
                AppenderData<T> parentData = findAppender(loggerConfig.getParent(), data.type);

                if (parentData != null) {
                    if (parentData.isFiltering(level)) {
                        return constructAdditiveAppender(parentData, data);
                    }
                }
            }
            else if (!appenderData.isLevel(data.level)) {
                LoggerControl.updateAppender(appenderData, data, null);
            }
        }
        else if (!appenderData.isLevel(data.level)) {
            if (appenderData.isFiltering(data.level)) {
                return constructAdditiveAppender(appenderData, data);
            }
        }
        return appenderData;
    }

    private static <T extends Appender> AppenderData<T> constructAdditiveAppender(AppenderData<T> data,
                                                                                  InitializationPackage<T> iPack) {

        final boolean dedicatedFile = iPack.isDedicatedFileAppender(data);
        InitializationPackage<T> newData = iPack.copyWithLayout(data.getLayout());
        Filter filter = dedicatedFile ? null : createLevelRangeFilter(Level.OFF, data.getLevel(), Filter.Result.DENY);
        return new AppenderData<>(iPack.loggerConfig, constructAppender(newData, filter), iPack.type, iPack.level);
    }

    @SuppressWarnings("SameParameterValue")
    private static LevelRangeFilter createLevelRangeFilter(Level min, Level max, Filter.Result action) {
        return LevelRangeFilter.createFilter(min, max, action, Filter.Result.NEUTRAL);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Appender> T constructAppender(InitializationPackage<T> data, @Nullable Filter filter) {

        if (data.type == AppenderType.FILE) {
            return (T) createNewFileAppender(data.loggerConfig, data.level, data.layout, data.logFilePath, filter, true);
        }
        else if (data.type == AppenderType.CONSOLE) {
            return (T) createNewConsoleAppender(data.loggerConfig, data.level, data.layout, filter, true);
        }
        else {
            String log = "Fatal error occurred while constructing %s. Construction for that type is not supported.";
            throw new NoClassDefFoundError(String.format(log, data.type.toString()));
        }
    }

    /**
     * Add the appender to supplied {@code LoggerConfig} and activate it.
     *
     * @param level logging Level the appender will operate under
     * @param <T> appender object type
     * @return the activated appender instance
     */
    public static <T extends Appender> T initializeAppender(LoggerConfig loggerConfig, T appender, Level level, @Nullable Filter filter) {

        String appenderClass = appender.getClass().getSimpleName();
        CommonLogger.LOGGER.debug("Initializing %s for LoggerConfig %s", appenderClass, loggerConfig.getName());

        loggerConfig.addAppender(appender, level, filter);

        if (!appender.isStarted())
            appender.start();

        return appender;
    }

    /**
     * Search inside the logger {@code Configuration} file to find the existing {@code LoggerConfig}
     * or create a new default one with the appropriate name and level if none was found.
     *
     * @return the current {@code LoggerConfig} for the name specified in {@code CommonLogger}
     * or the root logger configuration if no existing {@code LoggerConfig} was found
     */
    public static LoggerConfig getOrCreateLoggerConfig(String name, Configuration config, Level level, boolean additive) {

        if (!config.getLoggers().containsKey(name))
        {
            CommonLogger.LOGGER.debug("Creating new LoggerConfig for %s in current context", name);

            /* includeLocation - whether location should be passed downstream
             * Not quite sure what this parameter does so just set it to null...
             */
            String includeLoc = null;
            //noinspection ConstantConditions
            LoggerConfig loggerConfig = LoggerConfig.createLogger(additive, level,
                    name, includeLoc, new AppenderRef[]{},null, config, null);

            config.addLogger(name, loggerConfig);
            return loggerConfig;
        }
        else {
            CommonLogger.LOGGER.debug("Getting LoggerConfig %s from current context", name);
            return config.getLoggerConfig(name);
        }
    }

    /**
     * @return  the first appender entry with the supplied name found in
     * {@code Configuration} or {@code null} if no entry was found.
     */
    public static @Nullable <T extends Appender> AppenderData<T> findAppender(final LoggerConfig loggerConfig, AppenderType<T> type) {

        LoggerConfig lookupConfig = loggerConfig;

        for (String name : type.getNames()) {
            while (lookupConfig != null)
            {
                AppenderControlArraySet appenderCtrls = getAppenderControls(lookupConfig);
                for (AppenderControl control : appenderCtrls.get())
                {
                    Appender appender = control.getAppender();
                    if (appender.getName().equalsIgnoreCase(name))
                    {
                        if (type.getTypeClass().isAssignableFrom(appender.getClass())) {
                            return new AppenderData<>(lookupConfig, appender, type, getAppenderLevel(control));
                        }
                        else {
                            ClassNotFoundException e = new ClassNotFoundException("Expected class: " + type.getTypeClass().getSimpleName());
                            CommonLogger.LOGGER.error("Found appender \"%s\" by name in %s with an unexpected class.", e);
                            return null;
                        }
                    }
                }
                lookupConfig = lookupConfig.isAdditive() ? lookupConfig.getParent() : null;
            }
            /* Reset the search entry point before next name lookup */
            lookupConfig = loggerConfig;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable <T extends Appender> T findAppender(Configuration config, AppenderType<T> type) {

        for (String name : type.getNames())
        {
            for (java.util.Map.Entry<String, Appender> entry : config.getAppenders().entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey()))
                {
                    if (type.getTypeClass().isAssignableFrom(entry.getValue().getClass())) {
                        return (T) entry.getValue();
                    }
                    else {
                        ClassNotFoundException e = new ClassNotFoundException("Expected class: " + type.getTypeClass().getSimpleName());
                        CommonLogger.LOGGER.error("Found appender \"%s\" by name in %s with an unexpected class.", e);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static @Nullable Level getAppenderLevel(AppenderControlArraySet appenderCtrls, Appender appender) throws NoSuchElementException {

        for (AppenderControl control : appenderCtrls.get()) {
            if (control.getAppenderName().equals(appender.getName()))
                return getAppenderLevel(control);
        }
        throw new NoSuchElementException();
    }
    public static @Nullable Level getAppenderLevel(AppenderControl control) {
        return ReflectionUtils.readPrivateField(control, "level", Level.class);
    }

    public static AppenderControlArraySet getAppenderControls(LoggerConfig loggerConfig) {
        return Objects.requireNonNull(ReflectionUtils.readPrivateField(loggerConfig, "appenders", AppenderControlArraySet.class));
    }

    public static @Nullable <T extends AbstractFilterable> Property getAppenderProperty(T appender, String property) {

        for (Property prop : appender.getPropertyArray()) {
            if (prop.getName().equalsIgnoreCase(property)) {
                return prop;
            }
        }
        return null;
    }

    public static String getLogFilePath(AbstractOutputStreamAppender appender) {
        /*
         * Contrary to what the method name might imply
         * FileManager#getFileName returns log file path not name
         */
        return ((FileManager)appender.getManager()).getFileName();
    }
}
