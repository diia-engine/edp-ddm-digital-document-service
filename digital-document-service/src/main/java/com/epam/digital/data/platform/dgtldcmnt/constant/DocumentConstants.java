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

package com.epam.digital.data.platform.dgtldcmnt.constant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;

public final class DocumentConstants {

  public static final String SIGNATURE_TYPE = "application/pkcs7-signature";
  public static final String P7S_EXTENSION = "p7s";

  /**
   * Canonical media types of the supported multimedia formats, as Apache Tika reports them from
   * file content.
   */
  public static final String AVI_TYPE = "video/x-msvideo";
  public static final String MPEG_VIDEO_TYPE = "video/mpeg";
  public static final String MPEG_AUDIO_TYPE = "audio/mpeg";
  public static final String MP4_VIDEO_TYPE = "video/mp4";
  public static final String MP4_AUDIO_TYPE = "audio/mp4";

  /**
   * Canonical media type of a ZIP archive. Tika looks inside a ZIP container and reports the most
   * specific subtype it recognises, so an OOXML document, an ASiC container or a JAR renamed to
   * {@code .zip} is detected as its own type and rejected - only a plain archive matches this one.
   */
  public static final String ZIP_TYPE = "application/zip";

  /**
   * Input {@code Content-Type} to the file extensions accepted for it.
   *
   * <p>Multimedia and archive formats are listed together with the legacy media types browsers and
   * operating systems still send for them ({@code video/avi}, {@code video/mpg}, {@code audio/mp3},
   * {@code application/x-zip-compressed} sent by Windows...). A request carrying an unknown {@code Content-Type} is rejected with 415 before its content is
   * ever read, so every alias a client may send has to be a key here.
   */
  public static final Map<String, Set<String>> MEDIA_TYPE_TO_EXTENSIONS_MAP = Map.ofEntries(
      Map.entry("application/pdf", Set.of("pdf")),
      Map.entry("image/png", Set.of("png")),
      Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
      Map.entry("text/csv", Set.of("csv")),
      Map.entry("application/octet-stream", Set.of(P7S_EXTENSION, "asics")),
      Map.entry(SIGNATURE_TYPE, Set.of(P7S_EXTENSION)),

      Map.entry(AVI_TYPE, Set.of("avi")),
      Map.entry("video/avi", Set.of("avi")),
      Map.entry("video/msvideo", Set.of("avi")),

      Map.entry(MPEG_VIDEO_TYPE, Set.of("mpg", "mpeg")),
      Map.entry("video/mpg", Set.of("mpg", "mpeg")),

      Map.entry(MPEG_AUDIO_TYPE, Set.of("mp3")),
      Map.entry("audio/mp3", Set.of("mp3")),
      Map.entry("audio/x-mpeg", Set.of("mp3")),

      Map.entry(MP4_VIDEO_TYPE, Set.of("mp4")),
      Map.entry(MP4_AUDIO_TYPE, Set.of("m4a", "mp4")),
      Map.entry("audio/x-m4a", Set.of("m4a", "mp4")),

      Map.entry(ZIP_TYPE, Set.of("zip")),
      Map.entry("application/x-zip-compressed", Set.of("zip"))
  );

  public static final List<MediaType> SUPPORTED_MEDIA_TYPES = MEDIA_TYPE_TO_EXTENSIONS_MAP.keySet()
      .stream()
      .map(MediaType::parseMediaType)
      .collect(Collectors.toList());

  /**
   * Input {@code Content-Type} to the detected media types accepted for it on top of an exact
   * match.
   *
   * <p>Tika reports the canonical type of its own alias set, so a legacy input type never equals
   * the detected one and has to be mapped here. {@code video/mp4} and {@code audio/mp4} are mapped
   * onto each other because they are the same container - an audio-only MP4 is detected as
   * {@code audio/mp4} no matter whether it is named {@code .mp4} or {@code .m4a}.
   */
  public static final Map<String, Set<String>> CORRESPONDED_MEDIA_TYPES = Map.ofEntries(
      Map.entry("application/octet-stream",
          Set.of(SIGNATURE_TYPE, "application/vnd.etsi.asic-s+zip")),

      Map.entry("video/avi", Set.of(AVI_TYPE)),
      Map.entry("video/msvideo", Set.of(AVI_TYPE)),

      Map.entry("video/mpg", Set.of(MPEG_VIDEO_TYPE)),

      Map.entry("audio/mp3", Set.of(MPEG_AUDIO_TYPE)),
      Map.entry("audio/x-mpeg", Set.of(MPEG_AUDIO_TYPE)),

      Map.entry("audio/x-m4a", Set.of(MP4_AUDIO_TYPE)),
      Map.entry(MP4_VIDEO_TYPE, Set.of(MP4_AUDIO_TYPE)),
      Map.entry(MP4_AUDIO_TYPE, Set.of(MP4_VIDEO_TYPE)),

      Map.entry("application/x-zip-compressed", Set.of(ZIP_TYPE))
  );

  private DocumentConstants() {
  }
}
