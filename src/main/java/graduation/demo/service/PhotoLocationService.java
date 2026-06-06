package graduation.demo.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class PhotoLocationService {
    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractProvince(MultipartFile image){
        try{
            GeoLocation geoLocation = extractGeoLocation(image);

            if (geoLocation == null){
                System.out.println("GPS 정보 없음");
                return null;
            }

            double latitude = geoLocation.getLatitude();
            double longitude = geoLocation.getLongitude();

            System.out.println("longitude = " + longitude);
            System.out.println("latitude = " + latitude);

            String province = getProvinceFromKaKao(longitude, latitude);

            System.out.println("Kakao 변환 지역 = " + province);

            return province;

        } catch (Exception e){
            System.out.println("위치 추출 오류 = " + e.getMessage());
            return null;
        }
    }

    public List<String> createProvinceOptions(String answer){
        List<String> options = new ArrayList<>();
        options.add(answer);

        List<String> candidates = new ArrayList<>(List.of(
                "서울특별시",
                "경기도",
                "강원도",
                "충청북도",
                "충청남도",
                "전라북도",
                "전라남도",
                "경상북도",
                "경상남도",
                "부산광역시",
                "대구광역시",
                "인천광역시",
                "광주광역시",
                "대전광역시",
                "울산광역시",
                "제주도"
        ));

        candidates.remove(answer);
        Collections.shuffle(candidates);

        while (options.size() < 4 && !candidates.isEmpty()){
            options.add(candidates.remove(0));
        }

        Collections.shuffle(options);
        return options;
    }

    private GeoLocation extractGeoLocation (MultipartFile image){
        try (InputStream inputStream = image.getInputStream()){
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            if (gpsDirectory == null){
                return null;
            }

            GeoLocation geoLocation = gpsDirectory.getGeoLocation();

            if (geoLocation == null || geoLocation.isZero()){
                return null;
            }

            return geoLocation;
        } catch (Exception e){
            return null;
        }
    }

    private String getProvinceFromKaKao(double longitude, double latitude) throws Exception{
        RestClient restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoRestApiKey)
                .build();

        String responseBody = restClient.get()
                .uri("/v2/local/geo/coord2regioncode.json?x=" + longitude + "&y=" + latitude)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode documents = root.path("documents");

        if (!documents.isArray() || documents.isEmpty()){
            return null;
        }

        String province = documents.get(0).path("region_1depth_name").asString();

        if (province == null || province.isBlank()){
            return null;
        }

        return normalizeProvinceName(province);
    }

    private String normalizeProvinceName(String province){
        if (province.equals("강원특별자치도")){
            return "강원도";
        }
        if (province.equals("전북특별자치도")){
            return "전라북도";
        }
        if (province.equals("제주특별자치도")){
            return "제주도";
        }

        return province;
    }

}
