package org.hcjf.cloud.impl.messages;

import java.util.UUID;

/**
 * @author javaito
 */
public class ResponseMessage extends Message {

    private Object value;
    private Boolean notFound;
    private Throwable throwable;

    public ResponseMessage() {
    }

    public ResponseMessage(UUID id) {
        super(id);
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Boolean getNotFound() {
        return notFound;
    }

    public void setNotFound(Boolean notFound) {
        this.notFound = notFound;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }
}