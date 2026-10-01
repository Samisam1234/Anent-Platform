package com.agentplatform.orchestrator.resume;

import com.agentplatform.orchestrator.resume.ResumeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeParserService {
    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);


    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF byte array must not be null or empty");
        }
        String contentType = this.detectContentType(pdfBytes);
        try {
            String rawText = "application/pdf".equals(contentType) ? this.extractFromPdf(pdfBytes) : ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType) ? this.extractFromDocx(pdfBytes) : this.extractFromPdf(pdfBytes));
            if (rawText == null || rawText.trim().length() < 50) {
                log.warn("File produced only {} characters \u2014 likely image-based or empty", (Object)(rawText == null ? 0 : rawText.trim().length()));
                throw new ResumeException("No useful text could be extracted from the file. The file may be image-based (scanned) or empty. Please provide a text-selectable PDF or DOCX.");
            }
            String trimmed = rawText.trim();
            log.info("Extracted {} characters from {} resume", (Object)trimmed.length(), (Object)contentType);
            return trimmed;
        }
        catch (ResumeException | IllegalArgumentException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Failed to parse resume: {}", (Object)e.getMessage());
            throw new ResumeException("Failed to parse the file. Please ensure the file is not corrupt or password-protected.", e);
        }
    }

    private String extractFromPdf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF((byte[])pdfBytes);){
            PDFTextStripper stripper = new PDFTextStripper();
            String string = stripper.getText(document);
            return string;
        }
    }

    private String extractFromDocx(byte[] docxBytes) throws IOException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(docxBytes);){
            String string;
            try (XWPFDocument document = new XWPFDocument((InputStream)is);){
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(paragraph.getText());
                }
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(cell.getText());
                        }
                    }
                }
                string = sb.toString();
            }
            return string;
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 5 && bytes[0] == 37 && bytes[1] == 80 && bytes[2] == 68 && bytes[3] == 70 && bytes[4] == 45) {
            return "application/pdf";
        }
        if (bytes.length >= 4 && bytes[0] == 80 && bytes[1] == 75 && bytes[2] == 3 && bytes[3] == 4) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "unknown";
    }
}

