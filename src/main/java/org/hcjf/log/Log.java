package org.hcjf.log;

import org.hcjf.properties.SystemProperties;
import org.hcjf.service.Service;
import org.hcjf.utils.Strings;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Stream;

/**
 * Static class that contains the funcionality in order to
 * maintain and organize a log file with the same records format
 * The log behavior is affected by the following system properties
 * <br><b>hcfj_log_path</b>: work directory of the log, by default app work directory
 * <br><b>hcfj_log_initial_queue_size</b>: initial size of the internal queue, by default 10000;
 * <br><b>hcfj_log_file_prefix</b>: all the log files start with this prefix, by default hcjf
 * <br><b>hcfj_log_error_file</b>: if the property is true then log create a particular file for error group only, by default false
 * <br><b>hcfj_log_warning_file</b>: if the property is true then log create a particular file for warning group only, by default false
 * <br><b>hcfj_log_info_file</b>: if the property is true then log create a particular file for info group only, by default false
 * <br><b>hcfj_log_debug_file</b>: if the property is true then log create a particular file for debug group only, by default false
 * <br><b>hcfj_log_level</b>: min level to write file, by default "I"
 * <br><b>hcfj_log_date_format</b>: date format to show in the log file, by default "yyyy-mm-dd hh:mm:ss"
 * @author javaito
 *
 */
public final class Log extends Service<LogPrinter> {

    private static final Log instance;

    static {
        instance = new Log();
    }

    private final List<LogPrinter> printers;
    private final Queue<LogRecord> queue;
    private final Object logMonitor;
    private Boolean shuttingDown;

    /**
     * Private constructor
     */
    private Log() {
        super(SystemProperties.get(SystemProperties.Log.SERVICE_NAME),
                SystemProperties.getInteger(SystemProperties.Log.SERVICE_PRIORITY));
        this.printers = new ArrayList<>();
        this.queue = new PriorityBlockingQueue<>(
                SystemProperties.getInteger(SystemProperties.Log.QUEUE_INITIAL_SIZE),
                (o1, o2) -> (int)(o1.getDate().getTime() - o2.getDate().getTime()));
        this.logMonitor = new Object();
        this.shuttingDown = false;
    }

    /**
     * Start the log thread.
     */
    @Override
    protected void init() {
        for (int i = 0; i < SystemProperties.getInteger(SystemProperties.Log.LOG_CONSUMERS_SIZE); i++) {
            fork(new LogRunnable());
        }
        List<String> logConsumers = SystemProperties.getList(SystemProperties.Log.CONSUMERS);
        logConsumers.forEach(S -> {
            try {
                LogPrinter printer = (LogPrinter) Class.forName(S).newInstance();
                registerConsumer(printer);
            } catch (Exception ex){
                ex.printStackTrace();
            }
        });
    }

    /**
     * Only valid with the stage is START
     * @param stage Shutdown stage.
     */
    @Override
    protected void shutdown(ShutdownStage stage) {
        switch (stage) {
            case START: {
                shuttingDown = true;
                synchronized (this.logMonitor) {
                    this.logMonitor.notifyAll();
                }
                break;
            }
        }
    }

    /**
     * This method register a printer.
     * @param consumer Printer.
     * @throws NullPointerException If the printer is null.
     */
    @Override
    public void registerConsumer(LogPrinter consumer) {
        if(consumer == null) {
            throw new NullPointerException("Log printer null");
        }

        printers.add(consumer);
    }

    @Override
    public void unregisterConsumer(LogPrinter consumer) {
        printers.remove(consumer);
    }

    /**
     * Add the record to the queue and notify the consumer thread.
     * @param record Record to add.
     */
    private UUID addRecord(LogRecord record) {
        if (instance.queue.add(record)) {
            synchronized (this.logMonitor) {
                this.logMonitor.notifyAll();
            }
        }
        return record.getId();
    }

    /**
     * This method register a printer.
     * @param printer Printer.
     * @throws NullPointerException If the printer is null.
     */
    public static void addPrinter(LogPrinter printer) {
        instance.registerConsumer(printer);
    }

    /**
     * Create a record with debug group ("[D]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID d(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.DEBUG, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with debug group ("[D]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param throwable Throwable whose message will be printed as part of record
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID d(String tag, String message, Throwable throwable, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.DEBUG, tag, message, throwable, params));
        }
        return result;
    }

    /**
     * Create a record with info group ("[I]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID i(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.INFO, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with info group ("[IN]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID in(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.INPUT, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with info group ("[OUT]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID out(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.OUTPUT, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with warning group ("[W]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID w(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.WARNING, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with warning group ("[W]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param throwable Throwable whose message will be printed as part of record
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID w(String tag, String message, Throwable throwable, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.WARNING, tag, message, throwable, params));
        }
        return result;
    }

    /**
     * Create a record with error group ("[E]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID e(String tag, String message, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.ERROR, tag, message, params));
        }
        return result;
    }

    /**
     * Create a record with error group ("[E]"). All the places in the messages
     * are replaced for each param in the natural order.
     * @param tag Tag of the record
     * @param message Message to the record. This message use the syntax for class
     *                {@link java.util.Formatter}.
     * @param throwable Throwable whose message will be printed as part of record
     * @param params Parameters for the places in the message.
     * @return Returns the id assigned to the log record created, this id could be null
     * if the service does not generates any log records
     */
    public static UUID e(String tag, String message, Throwable throwable, Object... params) {
        UUID result = null;
        if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED) ||
                instance.printers.size() > 0) {
            result = instance.addRecord(new LogRecord(LogGroup.ERROR, tag, message, throwable, params));
        }
        return result;
    }

    /**
     * Return the size of the log queue.
     * @return Size of the log queue.
     */
    public static Integer getLogQueueSize() {
        return instance.queue.size();
    }

    private class LogRunnable implements Runnable {

        /**
         * Wait to found a recor to print.
         */
        @Override
        public void run() {
            try {
                while(!shuttingDown) {
                    if(instance.queue.isEmpty()) {
                        synchronized (Log.this.logMonitor) {
                            Log.this.logMonitor.wait();
                        }
                    }

                    try {
                        writeRecord(instance.queue.remove());
                    } catch (Exception ex) {}
                }
            } catch (InterruptedException e) {}
        }

        /**
         * Print the record in all the printers registered and the
         * syste out printer.
         * @param record Record to print.
         */
        private void writeRecord(LogRecord record) {
            if(record.getGroup().getOrder() >= SystemProperties.getInteger(SystemProperties.Log.LEVEL)) {
                printers.forEach(printer -> printer.print(record));
            }

            if(SystemProperties.getBoolean(SystemProperties.Log.SYSTEM_OUT_ENABLED)) {
                System.out.println(record.toString());
                System.out.flush();
            }
        }
    }

    /**
     * This class contains all the information to write a record in the log.
     * The instances of this class will be queued sorted chronologically waiting for
     * be write
     */
    public static final class LogRecord {

        private final UUID id;
        private final Date date;
        private final LogGroup group;
        private final String tag;
        private final String originalMessage;
        private final String message;
        private final SimpleDateFormat dateFormat;
        private final Object[] params;

        /**
         * Constructor
         * @param group Group of the record.
         * @param tag Tag for the record.
         * @param message Message with wildcard for the parameters.
         * @param throwable The error object, could be null
         * @param params Values that will be put in the each places of the message.
         */
        private LogRecord(LogGroup group, String tag, String message, Throwable throwable, Object... params) {
            this.id = UUID.randomUUID();
            this.date = new Date();
            this.group = group;
            this.tag = tag;
            this.dateFormat = new SimpleDateFormat(SystemProperties.get(SystemProperties.Log.DATE_FORMAT));
            this.originalMessage = message;
            this.message = createMessage(message, throwable, params);
            this.params = params;
        }

        /**
         * Constructor
         * @param group Group of the record.
         * @param tag Tag for the record.
         * @param message Message with wildcard for the parameters.
         * @param params Values that will be put in the each places of the message.
         */
        private LogRecord(LogGroup group, String tag, String message, Object... params) {
            this(group, tag, message, null, params);
        }

        /**
         * Return the id of log record.
         * @return Log record id.
         */
        public UUID getId() {
            return id;
        }

        /**
         * Create a final version of string to print the record.
         * @param message Message to be format
         * @param throwable The error object, could be null.
         * @param params Parameters to format the message.
         * @return Return the last version of the message.
         */
        private String createMessage(String message, Throwable throwable, Object... params) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);

            String group = getGroup().getGroup();
            String tag = getTag();
            if(SystemProperties.getBoolean(SystemProperties.Log.TRUNCATE_TAG)) {
                int truncateSize = SystemProperties.getInteger(SystemProperties.Log.TRUNCATE_TAG_SIZE);
                if(truncateSize > tag.length()) {
                    tag = Strings.rightPad(tag, truncateSize);
                } else if(truncateSize < tag.length()) {
                    tag = tag.substring(0, truncateSize);
                }
            }

            printWriter.print(dateFormat.format(getDate()));
            printWriter.print(" [");
            printWriter.print(group);
            printWriter.print("][");
            printWriter.print(tag);
            printWriter.print("] ");
            printWriter.printf(message, params);

            if(throwable != null) {
                printWriter.print("\r\n");
                throwable.printStackTrace(printWriter);
            }

            printWriter.flush();
            printWriter.close();

            return stringWriter.toString();
        }

        /**
         * Return the record date
         * @return Record date.
         */
        public Date getDate() {
            return date;
        }

        /**
         * Return the record group.
         * @return Record group.
         */
        public LogGroup getGroup() {
            return group;
        }

        /**
         * Return the log record tag.
         * @return Log record tag.
         */
        public String getTag() {
            return tag;
        }

        /**
         * Return the last version of the message.
         * @return Record message.
         */
        public String getMessage() {
            return message;
        }

        /**
         * Return the original message.
         * @return Original message.
         */
        public String getOriginalMessage() {
            return originalMessage;
        }

        /**
         * Return de format message.
         * @return Format message.
         */
        @Override
        public String toString() {
            return getMessage();
        }

        /**
         * Return the log record params.
         * @return Log record params.
         */
        public Object[] getParams() {
            return params;
        }
    }

    /**
     * This enum contains all the possible groups for the records
     */
    public static enum LogGroup {

        DEBUG("D", 0),

        INPUT("A", 1),

        OUTPUT("O", 1),

        INFO("I", 1),

        WARNING("W", 2),

        ERROR("E", 3);

        private Integer order;
        private String group;

        LogGroup(String tag, Integer order) {
            this.order = order;
            this.group = tag;
        }

        /**
         * Return the group order.
         * @return Tag order.
         */
        public Integer getOrder() {
            return order;
        }

        /**
         * Return the group label.
         * @return Tag label.
         */
        public String getGroup() {
            return group;
        }

        /**
         * Return the group instance that correspond with the parameter.
         * @param tagString String group
         * @return Tag instance founded or null if the instance is not exist.
         */
        public LogGroup valueOfByTag(String tagString) {
            LogGroup result = null;

            try {
                result = Stream.of(values()).
                        filter(r -> r.getGroup().equals(tagString)).
                        findFirst().get();
            } catch (NoSuchElementException ex) {}

            return result;
        }
    }
}