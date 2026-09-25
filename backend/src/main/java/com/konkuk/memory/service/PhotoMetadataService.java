package com.konkuk.memory.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.konkuk.memory.dto.PhotoMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class PhotoMetadataService {

    private final RestClient restClient;

    @Value("${app.kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    public PhotoMetadataService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public PhotoMetadata extract(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {

            Metadata metadata =
                    ImageMetadataReader.readMetadata(inputStream);

            LocalDateTime takenAt = extractTakenAt(metadata);
            String location = extractLocation(metadata);
            Integer orientation = extractOrientation(metadata);

            return new PhotoMetadata(
                    takenAt,
                    location,
                    orientation
            );

        } catch (Exception e) {

            // 메타데이터가 없거나 읽지 못해도
            // 사진 업로드 자체는 허용
            return new PhotoMetadata(
                    null,
                    null,
                    null
            );
        }
    }

    private LocalDateTime extractTakenAt(Metadata metadata) {

        ExifSubIFDDirectory directory =
                metadata.getFirstDirectoryOfType(
                        ExifSubIFDDirectory.class
                );

        if (directory == null) {
            return null;
        }

        Date date = directory.getDateOriginal();

        if (date == null) {
            return null;
        }

        return LocalDateTime.ofInstant(
                date.toInstant(),
                ZoneId.systemDefault()
        );
    }

    private String extractLocation(Metadata metadata) {

        GpsDirectory gpsDirectory =
                metadata.getFirstDirectoryOfType(
                        GpsDirectory.class
                );

        if (gpsDirectory == null) {
            return null;
        }

        GeoLocation geoLocation =
                gpsDirectory.getGeoLocation();

        if (geoLocation == null || geoLocation.isZero()) {
            return null;
        }

        double latitude = geoLocation.getLatitude();
        double longitude = geoLocation.getLongitude();

        // Kakao API 키가 없으면 좌표라도 보존
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            return latitude + "," + longitude;
        }

        String kakaoLocation =
                getLocationFromKakao(longitude, latitude);

        // Kakao API 호출이 실패해도 좌표는 보존
        if (kakaoLocation == null) {
            return latitude + "," + longitude;
        }

        return kakaoLocation;
    }

    private Integer extractOrientation(Metadata metadata) {

        ExifIFD0Directory directory =
                metadata.getFirstDirectoryOfType(
                        ExifIFD0Directory.class
                );

        if (directory == null ||
                !directory.containsTag(
                        ExifIFD0Directory.TAG_ORIENTATION
                )) {
            return null;
        }

        return directory.getInteger(
                ExifIFD0Directory.TAG_ORIENTATION
        );
    }

    @SuppressWarnings("unchecked")
    private String getLocationFromKakao(
            double longitude,
            double latitude
    ) {

        try {

            Map<String, Object> response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .header(
                            "Authorization",
                            "KakaoAK " + kakaoRestApiKey
                    )
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            Object documentsObject = response.get("documents");

            if (!(documentsObject instanceof List<?> documents)
                    || documents.isEmpty()) {
                return null;
            }

            Map<String, Object> document =
                    (Map<String, Object>) documents.get(0);

            Object region = document.get("region_1depth_name");

            return region == null
                    ? null
                    : region.toString();

        } catch (Exception e) {

            // 장소 변환 API가 실패했다고
            // 사진 업로드 전체를 실패시키지 않음
            return null;
        }
    }
}