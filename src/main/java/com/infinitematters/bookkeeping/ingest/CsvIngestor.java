package com.infinitematters.bookkeeping.ingest;

import com.infinitematters.bookkeeping.domain.Transaction;
import org.apache.commons.csv.CSVFormat;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvIngestor {

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
        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);
        List<Transaction> out = new ArrayList<>();
        for (CSVRecord r : records) {
            Transaction t = new Transaction();
            t.id = r.get("id");
            t.date = LocalDate.parse(r.get("date"));
            t.merchant = r.get("merchant");
            t.memo = r.get("memo");
            t.amount = new BigDecimal(r.get("amount"));
            t.mcc = r.isSet("mcc") ? r.get("mcc") : null;
            out.add(t);
        }
        return out;
    }
}
