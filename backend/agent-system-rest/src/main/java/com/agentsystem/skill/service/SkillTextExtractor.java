package com.agentsystem.skill.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts plain text from binary document formats (PDF, DOCX, ...) via Apache Tika —
 * the same reader DocumentIngestionService uses for knowledge-base uploads. Skills only
 * ever store plain text in Garage, so binary formats are extracted here before upload.
 */
@Component
public class SkillTextExtractor {

    public String extract(byte[] content, String filename) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() { return filename; }
        };
        List<Document> docs = new TikaDocumentReader(resource).read();
        return docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
    }
}
