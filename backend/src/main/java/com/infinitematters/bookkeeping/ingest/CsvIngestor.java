package com.infinitematters.bookkeeping.ingest;

import com.infinitematters.bookkeeping.domain.Transaction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CsvIngestor {
    private static final List<String> REQUIRED_HEADERS = List.of("id", "date", "merchant", "memo", "amount", "mcc");

    public List<Transaction> readFile(File csv) throws IOException {
        try (Reader in = new FileReader(csv)) {
            return read(in);
        }
    }

    public List<Transaction> read(InputStream inputStream) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream)) {
            return read(reader);
        }
    }

    private List<Transaction> read(Reader in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        List<Transaction> out = new ArrayList<>();
        try (CSVParser parser = format.parse(in)) {
            validateHeaders(parser.getHeaderNames());
            Set<String> seenTransactionIds = new HashSet<>();
            List<String> validationErrors = new ArrayList<>();

            for (CSVRecord r : parser) {
                List<String> rowErrors = new ArrayList<>();
                Transaction transaction = parseRecord(r, rowErrors);

                if (transaction.id != null) {
                    validateUniqueTransactionId(r, transaction.id, seenTransactionIds, rowErrors);
                }

                if (rowErrors.isEmpty()) {
                    out.add(transaction);
                } else {
                    validationErrors.addAll(rowErrors);
                }
            }

            if (!validationErrors.isEmpty()) {
                throw new CsvValidationException(validationErrors);
            }
        }
        return out;
    }

    private Transaction parseRecord(CSVRecord record, List<String> rowErrors) {
        Transaction transaction = new Transaction();
        transaction.id = requiredValue(record, "id", rowErrors);
        transaction.merchant = requiredValue(record, "merchant", rowErrors);
        transaction.memo = requiredValue(record, "memo", rowErrors);
        transaction.mcc = record.isSet("mcc") ? value(record, "mcc") : null;
        transaction.date = parseDate(record, rowErrors);
        transaction.amount = parseAmount(record, rowErrors);
        return transaction;
    }

    private LocalDate parseDate(CSVRecord record, List<String> rowErrors) {
        String value = requiredValue(record, "date", rowErrors);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            rowErrors.add(invalidValueMessage(record, "date", value, "ISO-8601 date like 2026-03-15"));
            return null;
        }
    }

    private BigDecimal parseAmount(CSVRecord record, List<String> rowErrors) {
        String value = requiredValue(record, "amount", rowErrors);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            rowErrors.add(invalidValueMessage(record, "amount", value, "numeric amount like 18.45"));
            return null;
        }
    }

    private String value(CSVRecord record, String column) {
        return record.get(column).trim();
    }

    private String requiredValue(CSVRecord record, String column, List<String> rowErrors) {
        String value = value(record, column);
        if (value.isEmpty()) {
            rowErrors.add(
                    "CSV row "
                            + record.getRecordNumber()
                            + " is missing a value for required column '"
                            + column
                            + "'.");
            return null;
        }
        return value;
    }

    private String invalidValueMessage(CSVRecord record, String column, String value, String expectedFormat) {
        return
                "CSV row "
                        + record.getRecordNumber()
                        + " has invalid value for column '"
                        + column
                        + "': '"
                        + value
                        + "'. Expected "
                        + expectedFormat
                        + ".";
    }

    private void validateUniqueTransactionId(CSVRecord record,
                                             String transactionId,
                                             Set<String> seenTransactionIds,
                                             List<String> rowErrors) {
        if (!seenTransactionIds.add(transactionId)) {
            rowErrors.add(
                    "CSV row "
                            + record.getRecordNumber()
                            + " duplicates transaction id '"
                            + transactionId
                            + "' within the uploaded file.");
        }
    }

    private void validateHeaders(List<String> headers) {
        Set<String> presentHeaders = Set.copyOf(headers);
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !presentHeaders.contains(header))
                .toList();

        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV file is missing required columns: "
                            + String.join(", ", missingHeaders)
                            + ". Expected header id,date,merchant,memo,amount,mcc.");
        }
    }
}
