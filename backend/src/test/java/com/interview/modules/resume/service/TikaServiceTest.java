package com.interview.modules.resume.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TikaServiceTest {

    private final TikaService tikaService = new TikaService();

    @Test
    void shouldGetFileTypePdf() {
        assertEquals("pdf", TikaService.getFileType("resume.pdf"));
    }

    @Test
    void shouldGetFileTypeDocx() {
        assertEquals("docx", TikaService.getFileType("简历.docx"));
    }

    @Test
    void shouldGetFileTypeTxt() {
        assertEquals("txt", TikaService.getFileType("readme.txt"));
    }

    @Test
    void shouldGetFileTypeUppercase() {
        assertEquals("pdf", TikaService.getFileType("RESUME.PDF"));
    }

    @Test
    void shouldDefaultToTxtForNoExtension() {
        assertEquals("txt", TikaService.getFileType("README"));
    }

    @Test
    void shouldReturnTxtForNullFileName() {
        assertEquals("txt", TikaService.getFileType(null));
    }
}