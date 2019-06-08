package io.yooksi.commons.logger;

import io.yooksi.commons.define.MethodsNotNull;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * <p>Contains extended appender information such as {@code LoggerConfig} affiliation and
 * programmatically assigned threshold level which are normally only accessible through
 * reflection since {@code log4j} hides these values in private fields.</p>
 *
 * We store this data here so that it's easily accessible without
 * having to constantly use reflection.
 *
 * @param <T> Appender implementation type
 * @see org.apache.logging.log4j.core.config.AppenderControl
 * @see org.apache.logging.log4j.core.config.AppenderControlArraySet
 */
@MethodsNotNull
@SuppressWarnings("WeakerAccess")
public class AppenderData<T extends Appender> {

    private final LoggerConfig loggerConfig;
    private final T appender;
    private final AppenderType<T> type;

    /**
     * Used to keep track of when an appender is programmatically started
     * or stopped by {@code LoggerControl}. Only the following values are used:
     * <ul>
     *     <li><b>STARTED</b> - Appender currently operates in {@code LoggerConfig}.</li>
     *     <li><b>STOPPED</b> - Appender has been removed from {@code LoggerConfig}.</li>
     * </ul>
     */
    private LifeCycle.State state;
    private Level level;

    /**
     * @param loggerConfig {@code LoggerConfig} this appender belongs to
     * @param appender appender object that holds this data
     * @param type designates the appender object type
     * @param level appender's programmatically assigned threshold level.
     *              If the value is {@code null} it will be treated as {@code ALL}.
     */
    @SuppressWarnings("unchecked")
    AppenderData(LoggerConfig loggerConfig, Appender appender, AppenderType<T> type, @Nullable Level level) {

        this.loggerConfig = loggerConfig;
        this.appender = (T) appender;
        this.type = type;
        this.state = LifeCycle.State.STARTED;
        this.level = level != null ? level : Level.ALL;
    }
    /**
     * Constructor used in appender initializations.
     *
     * @param appender appender object that holds this data
     * @param iPack package to extract other data from
     */
    AppenderData(Appender appender, InitializationPackage<T> iPack) {
        this(iPack.loggerConfig, appender, iPack.type, iPack.level);
    }

    void setLevel(Level level) {
        this.level = level;
    }
    void setState(LifeCycle.State state) {
        this.state = state;
    }

    public AppenderType<T> getType() {
        return type;
    }
    public LifeCycle.State getState() {
        return state;
    }
    public LoggerConfig getLoggerConfig() {
        return loggerConfig;
    }
    public T getAppender() {
        return appender;
    }
    public Level getLevel() {
        return level;
    }
    public int IntLevel() {
        return level.intLevel();
    }
    public boolean isFiltering(Level level) {
        return level.intLevel() > IntLevel();
    }
    public boolean isLevel(Level level) {
        return level.equals(this.level);
    }
    public boolean isLoggerConfig(LoggerConfig config) {
        return config.equals(loggerConfig);
    }
    public Layout<? extends Serializable> getLayout() {
        return appender.getLayout();
    }
}
