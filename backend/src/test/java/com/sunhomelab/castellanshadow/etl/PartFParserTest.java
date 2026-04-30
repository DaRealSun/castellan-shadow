package com.sunhomelab.castellanshadow.etl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PartFParserTest {

    private static final Path Q3 = Paths.get("../docs/sources/CTEF_part_f_q3.pdf").toAbsolutePath().normalize();
    private static final Path CTIF_Q1 = Paths.get("../docs/sources/CTIF_part_f_q1.pdf").toAbsolutePath().normalize();

    @Test
    void dumpCtifQ1() throws Exception {
        if (!Files.exists(CTIF_Q1)) return;
        byte[] bytes = Files.readAllBytes(CTIF_Q1);
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            System.out.println("---CTIF Q1 BEGIN---");
            System.out.println(text);
            System.out.println("---CTIF Q1 END---");
        }
    }

    @Test
    void dumpPdfBoxText() throws Exception {
        if (!Files.exists(Q3)) {
            System.out.println("Skipping; PDF not found at " + Q3);
            return;
        }
        byte[] bytes = Files.readAllBytes(Q3);
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            System.out.println("---BEGIN PDF TEXT---");
            System.out.println(text);
            System.out.println("---END PDF TEXT---");
            System.out.println("Length: " + text.length());
        }
    }

    @Test
    void parsesCtefQ3AndFindsRatioSpreads() throws Exception {
        if (!Files.exists(Q3)) {
            System.out.println("Skipping; PDF not found at " + Q3);
            return;
        }
        PartFParser parser = new PartFParser();
        Optional<HoldingsFetcher.Snapshot> snap = parser.parseBytes(Files.readAllBytes(Q3));

        assertThat(snap).isPresent();
        HoldingsFetcher.Snapshot s = snap.get();
        assertThat(s.asOfDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        long equities = s.rows().stream().filter(r -> "EQUITY".equals(r.securityType())).count();
        long options  = s.rows().stream().filter(r -> "OPTION".equals(r.securityType())).count();
        long cash     = s.rows().stream().filter(r -> "CASH".equals(r.securityType())).count();

        System.out.println("Parsed Part F: " + equities + " equities, " + options + " options, " + cash + " cash");
        s.rows().forEach(r -> System.out.println("  " + r.securityType() + "  " + r.instrumentSymbol()
            + "  shr=" + r.shares() + "  v=" + r.marketValue()));
    }
}
