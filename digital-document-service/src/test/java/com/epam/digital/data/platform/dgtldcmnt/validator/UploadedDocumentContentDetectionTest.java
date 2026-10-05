/*
 * Copyright 2023 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.dgtldcmnt.validator;

import com.epam.digital.data.platform.dgtldcmnt.constant.DocumentConstants;
import com.epam.digital.data.platform.dgtldcmnt.detector.DigitalDocumentMediaTypeDetector;
import com.epam.digital.data.platform.dgtldcmnt.dto.UploadDocumentDto;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.validation.ConstraintValidatorContext;
import lombok.SneakyThrows;
import org.apache.tika.Tika;
import org.apache.tika.detect.DefaultDetector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mockito;

/**
 * Runs {@link AllowedUploadedDocumentValidator} over real file bytes with a real {@link Tika},
 * wired the same way {@code MediaTypeValidationConfig} wires it in production.
 *
 * <p>{@link AllowedUploadedDocumentValidatorTest} mocks Tika out, so it proves the validator's
 * branching but says nothing about what Tika actually reports for a given file. That matters most
 * for the multimedia formats, where the type a browser sends is routinely an alias of the one Tika
 * derives from content: Tika normalises {@code video/avi} to {@code video/x-msvideo}, and does not
 * know {@code audio/mp3} or {@code video/mpg} at all. Only the headers that drive detection are
 * built here - the payload after them is irrelevant to the detector.
 *
 * <p>ZIP archives are the opposite case: Tika looks inside the container and reports the most
 * specific subtype it recognises, so archives are built as real ZIP files and the containers that
 * are ZIP underneath (OOXML, ASiC, JAR) must not pass as {@code application/zip}.
 */
class UploadedDocumentContentDetectionTest {

  private static final int PADDING_LENGTH = 300;

  private final Tika tika = new Tika(new DigitalDocumentMediaTypeDetector(new DefaultDetector()));

  static Stream<Arguments> acceptedUploads() {
    return Stream.of(
        Arguments.of("AVI, canonical type", avi(), "movie.avi", DocumentConstants.AVI_TYPE),
        Arguments.of("AVI, browser alias", avi(), "movie.avi", "video/avi"),
        Arguments.of("AVI, legacy OS alias", avi(), "movie.avi", "video/msvideo"),
        Arguments.of("AVI, uppercase extension", avi(), "movie.AVI", DocumentConstants.AVI_TYPE),

        Arguments.of("MPEG program stream", mpegProgramStream(), "movie.mpg",
            DocumentConstants.MPEG_VIDEO_TYPE),
        Arguments.of("MPEG elementary stream", mpegVideoStream(), "movie.mpg",
            DocumentConstants.MPEG_VIDEO_TYPE),
        Arguments.of("MPEG, .mpeg extension", mpegProgramStream(), "movie.mpeg",
            DocumentConstants.MPEG_VIDEO_TYPE),
        Arguments.of("MPEG, browser alias", mpegProgramStream(), "movie.mpg", "video/mpg"),

        Arguments.of("MP3 with ID3 tag", mp3WithId3Tag(), "song.mp3",
            DocumentConstants.MPEG_AUDIO_TYPE),
        Arguments.of("MP3 without ID3 tag", mp3WithFrameSync(), "song.mp3",
            DocumentConstants.MPEG_AUDIO_TYPE),
        Arguments.of("MP3, browser alias", mp3WithId3Tag(), "song.mp3", "audio/mp3"),
        Arguments.of("MP3, Tika alias", mp3WithId3Tag(), "song.mp3", "audio/x-mpeg"),

        Arguments.of("MP4, mp42 brand", mp4(), "clip.mp4", DocumentConstants.MP4_VIDEO_TYPE),
        Arguments.of("MP4, isom brand", mp4Isom(), "clip.mp4", DocumentConstants.MP4_VIDEO_TYPE),
        Arguments.of("M4A", m4a(), "song.m4a", DocumentConstants.MP4_AUDIO_TYPE),
        Arguments.of("M4A, macOS alias", m4a(), "song.m4a", "audio/x-m4a"),
        // the same container: an audio-only MP4 named .mp4 is still detected as audio/mp4
        Arguments.of("audio-only MP4 named .mp4", m4a(), "clip.mp4",
            DocumentConstants.MP4_VIDEO_TYPE),

        Arguments.of("ZIP archive", zipWithText(), "archive.zip", DocumentConstants.ZIP_TYPE),
        Arguments.of("ZIP, Windows alias", zipWithText(), "archive.zip",
            "application/x-zip-compressed"),
        Arguments.of("ZIP, uppercase extension", zipWithText(), "archive.ZIP",
            DocumentConstants.ZIP_TYPE),
        Arguments.of("ZIP with several documents", zipWithDocuments(), "archive.zip",
            DocumentConstants.ZIP_TYPE),
        // only the outer container matters - a nested docx doesn't change the detected type
        Arguments.of("ZIP with a nested DOCX", zipWithNestedDocx(), "archive.zip",
            DocumentConstants.ZIP_TYPE),
        Arguments.of("empty ZIP", emptyZip(), "archive.zip", DocumentConstants.ZIP_TYPE),
        // ASiC is ZIP underneath; adding ZIP must not break the existing .asics path
        Arguments.of("ASiC-S as .asics", asicS(), "file.pdf.asics", "application/octet-stream")
    );
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("should accept real multimedia content for every supported input media type")
  @MethodSource("acceptedUploads")
  @SneakyThrows
  void validate_realContentAccepted(String caseName, byte[] content, String filename,
      String inputContentType) {
    Assertions.assertThat(validate(content, filename, inputContentType)).isTrue();
  }

  static Stream<Arguments> rejectedUploads() {
    return Stream.of(
        Arguments.of("MP3 renamed to .mp4", mp3WithId3Tag(), "clip.mp4",
            DocumentConstants.MP4_VIDEO_TYPE),
        Arguments.of("PDF renamed to .avi", pdf(), "movie.avi", DocumentConstants.AVI_TYPE),
        Arguments.of("AVI renamed to .mpg", avi(), "movie.mpg",
            DocumentConstants.MPEG_VIDEO_TYPE),
        Arguments.of("MP4 declared as MP3", mp4(), "song.mp3",
            DocumentConstants.MPEG_AUDIO_TYPE),
        Arguments.of("CSV declared as MP3", csv(), "song.mp3", DocumentConstants.MPEG_AUDIO_TYPE),

        Arguments.of("DOCX renamed to .zip", docx(), "archive.zip", DocumentConstants.ZIP_TYPE),
        Arguments.of("ASiC-S renamed to .zip", asicS(), "archive.zip", DocumentConstants.ZIP_TYPE),
        Arguments.of("JAR renamed to .zip", jar(), "archive.zip",
            "application/x-zip-compressed"),
        Arguments.of("PDF renamed to .zip", pdf(), "archive.zip", DocumentConstants.ZIP_TYPE)
    );
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("should reject content that doesn't match the declared media type")
  @MethodSource("rejectedUploads")
  @SneakyThrows
  void validate_realContentRejected(String caseName, byte[] content, String filename,
      String inputContentType) {
    Assertions.assertThat(validate(content, filename, inputContentType)).isFalse();
  }

  @SneakyThrows
  private boolean validate(byte[] content, String filename, String inputContentType) {
    final var uploadDocumentDto = UploadDocumentDto.builder()
        .fileInputStream(new BufferedInputStream(new ByteArrayInputStream(content)))
        .filename(filename)
        .contentType(inputContentType)
        .build();

    final var context = Mockito.mock(ConstraintValidatorContext.class, Answers.RETURNS_DEEP_STUBS);
    return new AllowedUploadedDocumentValidator(tika, true, true)
        .isValid(uploadDocumentDto, context);
  }

  private static byte[] avi() {
    // RIFF header: "RIFF", little-endian payload size, "AVI " form type
    return withPadding("RIFF".getBytes(StandardCharsets.US_ASCII),
        new byte[]{(byte) 0xD0, 0x07, 0x00, 0x00},
        "AVI LIST".getBytes(StandardCharsets.US_ASCII));
  }

  private static byte[] mpegProgramStream() {
    return withPadding(new byte[]{0x00, 0x00, 0x01, (byte) 0xBA});
  }

  private static byte[] mpegVideoStream() {
    return withPadding(new byte[]{0x00, 0x00, 0x01, (byte) 0xB3});
  }

  private static byte[] mp3WithId3Tag() {
    return withPadding("ID3".getBytes(StandardCharsets.US_ASCII),
        new byte[]{0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
  }

  private static byte[] mp3WithFrameSync() {
    return withPadding(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x44});
  }

  private static byte[] mp4() {
    return isoBaseMediaFile("ftypmp42", "mp42isomavc1");
  }

  private static byte[] mp4Isom() {
    return isoBaseMediaFile("ftypisom", "isomiso2avc1mp41");
  }

  private static byte[] m4a() {
    return isoBaseMediaFile("ftypM4A ", "M4A mp42isom");
  }

  private static byte[] pdf() {
    return withPadding("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
  }

  private static byte[] csv() {
    return "id,name\n1,first\n2,second\n".getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] zipWithText() {
    return zip(deflated("readme.txt", "plain text"));
  }

  private static byte[] zipWithDocuments() {
    return zip(deflated("notes.txt", "plain text"),
        deflated("table.csv", "id,name\n1,first\n"),
        new ZipFileEntry("scan.pdf", pdf(), false));
  }

  private static byte[] zipWithNestedDocx() {
    return zip(new ZipFileEntry("document.docx", docx(), false));
  }

  private static byte[] emptyZip() {
    return zip();
  }

  /**
   * OOXML package: Tika recognises it by {@code [Content_Types].xml} being the first entry.
   */
  private static byte[] docx() {
    return zip(deflated("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" "
            + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/"
            + "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "</Types>"),
        deflated("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Target=\"word/document.xml\" Type=\"http://schemas."
            + "openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/>"
            + "</Relationships>"),
        deflated("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body/></w:document>"));
  }

  /**
   * ASiC-S container (ETSI EN 319 162): an uncompressed {@code mimetype} entry goes first.
   */
  private static byte[] asicS() {
    return zip(new ZipFileEntry("mimetype",
            "application/vnd.etsi.asic-s+zip".getBytes(StandardCharsets.US_ASCII), true),
        new ZipFileEntry("file.pdf", pdf(), false),
        new ZipFileEntry("META-INF/signature.p7s", new byte[64], false));
  }

  private static byte[] jar() {
    return zip(deflated("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"),
        new ZipFileEntry("com/example/App.class",
            new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, false));
  }

  private static ZipFileEntry deflated(String name, String content) {
    return new ZipFileEntry(name, content.getBytes(StandardCharsets.UTF_8), false);
  }

  @SneakyThrows
  private static byte[] zip(ZipFileEntry... entries) {
    final var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      for (ZipFileEntry entry : entries) {
        final var zipEntry = new ZipEntry(entry.name);
        if (entry.stored) {
          final var crc = new CRC32();
          crc.update(entry.content);
          zipEntry.setMethod(ZipEntry.STORED);
          zipEntry.setSize(entry.content.length);
          zipEntry.setCrc(crc.getValue());
        }
        zip.putNextEntry(zipEntry);
        zip.write(entry.content);
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private static final class ZipFileEntry {

    private final String name;
    private final byte[] content;
    private final boolean stored;

    private ZipFileEntry(String name, byte[] content, boolean stored) {
      this.name = name;
      this.content = content;
      this.stored = stored;
    }
  }

  /**
   * ISO base media file: 4-byte big-endian box size, the {@code ftyp} box with its major brand,
   * a minor version and the list of compatible brands.
   */
  private static byte[] isoBaseMediaFile(String ftypBox, String compatibleBrands) {
    return withPadding(new byte[]{0x00, 0x00, 0x00, 0x20},
        ftypBox.getBytes(StandardCharsets.US_ASCII),
        new byte[]{0x00, 0x00, 0x02, 0x00},
        compatibleBrands.getBytes(StandardCharsets.US_ASCII));
  }

  @SneakyThrows
  private static byte[] withPadding(byte[]... chunks) {
    final var out = new ByteArrayOutputStream();
    for (byte[] chunk : chunks) {
      out.write(chunk);
    }
    out.write(new byte[PADDING_LENGTH]);
    return out.toByteArray();
  }
}
