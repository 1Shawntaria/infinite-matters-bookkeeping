package com.infinitematters.bookkeeping.ingest;

import java.util.List;

public class CsvValidationException extends IllegalArgumentException {
    private final List<String> errors;

    public CsvValidationException(List<String> errors) {
        super(errors.size() == 1
                ? errors.get(0)
                : "CSV file contains %d validation errors.".formatted(errors.size()));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
