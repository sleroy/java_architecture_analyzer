package com.analyzer.core.collector;

import java.io.IOException;

public class InvalidResourceException extends RuntimeException {

    public InvalidResourceException(final String message) {
        super(message);
    }

    public InvalidResourceException(final String message, final IOException e) {
        super(message, e);
    }
}
