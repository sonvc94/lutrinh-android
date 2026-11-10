# Tìm địa điểm (quán cà phê, homestay, quán ăn, cây xăng): nhà cung cấp cho Việt Nam

Trạng thái: **chỉ nghiên cứu, 2026-11-10**. Không có mã ứng dụng nào thay đổi; R23/R24 (docs/04) vẫn
là tìm địa danh offline. Không tạo tài khoản, không tạo khoá API, không gọi API trả phí. Mọi giá và
điều khoản dưới đây đều có nguồn (mục 8, ngày truy cập 2026-11-10); câu nào không đọc được tận nơi
thì ghi "chưa kiểm chứng".

Yêu cầu (thuyền trưởng, nguyên văn):

> tạo một task nghiên cứu tìm nhà cung cấp dịch vụ tìm kiếm địa điểm tại việt nam, hiện tại data
> không đủ. ví dụ như việc tìm một quán cafe là không thể? Có đơn vị nào ở Việt Nam cung cấp giải
> pháp này không ?

Cách hiểu của chúng tôi (không phải lời thuyền trưởng): (1) đo xem dữ liệu hiện tại thiếu đến đâu
(quán cà phê ở Hà Giang / Ninh Bình), (2) liệt kê đơn vị Việt Nam (và quốc tế để so sánh) bán dịch
vụ tìm địa điểm/POI, (3) đề xuất cách gắn vào app qua một interface riêng như bản đồ (R35) và định
tuyến (R36).

## 1. Hôm nay: vì sao không tìm được quán cà phê

* Tìm địa danh (R23) chỉ đọc `app/src/main/assets/places/vn_places.tsv` (21 714 địa điểm + 34
  tỉnh, trích từ OSM ngày 2026-11-08), không bao giờ lên mạng.
* `tools/gazetteer/build_gazetteer.py` (`kind_of`) chỉ giữ: tỉnh, xã/phường, `place=*`, điểm du
  lịch (`tourism=attraction|viewpoint|camp_site|museum|theme_park|zoo`), tự nhiên (đỉnh, đèo, thác,
  hang, hồ, bãi biển, vịnh, đảo), di tích, nơi thờ tự có Wikidata. **Không có `amenity=cafe`,
  `restaurant`, `fuel`, cũng không có `tourism=hotel|guest_house|hostel`.** Vậy việc "tìm quán
  cafe là không thể" là do bộ lọc khi build, không phải do OSM trống.
* Số loại trong file hiện tại (lệnh `awk -F'\t' '$1!="R" && $1!~/^#/ && $2==26 {print $1}' … |
  sort | uniq -c`): vùng 26 Tuyên Quang (gồm Hà Giang cũ) 691 dòng, vùng 18 Ninh Bình (gồm Hà Nam,
  Nam Định) 437 dòng, toàn bộ là làng/xã/điểm du lịch.

### 1.1 Đo khoảng trống (café gap)

**Trong gazetteer** (lệnh chạy trên nhánh này):

```bash
T=app/src/main/assets/places/vn_places.tsv
awk -F'\t' '$1!="R" && $1!~/^#/ && ($2==26||$2==18)' $T \
  | grep -i -c -E 'cà phê|ca phe|cafe|café|coffee|homestay|xăng|petrol|nhà hàng|restaurant'
# → 0      (cả nước: 7 dòng có chữ cafe/coffee, đều là điểm du lịch "s"/bảo tàng, không phải quán)
```

**Trong OSM** (Overpass API công khai `overpass-api.de`, không khoá, User-Agent chung chung, mỗi
truy vấn một số đếm; dữ liệu OSM tại thời điểm `timestamp_osm_base` 2026-11-10T13:27Z–13:58Z). Tỉnh theo
ranh giới mới (relation 1903418 = Tỉnh Tuyên Quang, gồm Hà Giang cũ; 1900963 = Tỉnh Ninh Bình, gồm
Hà Nam + Nam Định) và hai hộp bao quanh tỉnh **cũ** (Hà Giang cũ: 22.13–23.40 N, 104.33–105.58 E;
Ninh Bình cũ: 19.84–20.48 N, 105.53–106.17 E; hộp chạm nhẹ sang tỉnh bên cạnh).

Truy vấn mẫu (các truy vấn khác chỉ thay bộ lọc / vùng; script: mục 7):

```
[out:json][timeout:90];area(3601903418)->.a;nwr["amenity"="cafe"](area.a);out count;
[out:json][timeout:90];nwr["amenity"="cafe"](22.13,104.33,23.40,105.58);out count;
```

| Bộ lọc OSM | Tuyên Quang (mới) | Ninh Bình (mới) | Hà Giang cũ (hộp) | Ninh Bình cũ (hộp) | Trong gazetteer |
| --- | ---: | ---: | ---: | ---: | ---: |
| `amenity=cafe` | 140 | 114 | 138 | 92 | 0 |
| `amenity=cafe` có `name` | 60 | 75 | 59 | 57 | 0 |
| `amenity=restaurant` | 216 | 299 | 223 | 293 | 0 |
| `amenity=fuel` | 101 | 137 | 106 | 133 | 0 |
| `tourism=guest_house\|hostel\|hotel\|motel\|apartment` | 480 | 327 | 496 | 327 | 0 |
| … trong đó `guest_house=homestay` | 1 | 0 | 1 | 0 | 0 |
| … trong đó tên chứa "homestay" | 86 | 74 | 87 | 74 | 0 |

Đọc bảng: OSM **có** vài trăm quán cà phê, nhà hàng, cây xăng và nơi lưu trú ở hai vùng, nhưng
(a) app không đưa chúng vào, (b) chỉ 43–66 % quán cà phê OSM có tên (60/140, 75/114, 57/92) (quán không tên chỉ tìm được
theo loại, không theo chữ), (c) so với thực tế (mỗi thị trấn vài chục quán) OSM vẫn mỏng.

**So sánh với dữ liệu mở Overture Maps Places** (bản 2026-10-21.0, đọc thẳng Parquet công khai trên
S3 bằng DuckDB, không khoá; cùng hai hộp ở trên; `basic_category`):

| Overture `basic_category` | Hà Giang cũ (hộp) | Ninh Bình cũ (hộp) |
| --- | ---: | ---: |
| `coffee_shop` + `cafe` | 149 + 89 = **238** (207 có confidence ≥ 0.5) | 640 + 306 = **946** (803 ≥ 0.5) |
| `restaurant` | 515 | 2 023 |
| `gas_station` | 35 | 63 |
| `hotel` + `lodging` + `bed_and_breakfast` | 328 + 206 + 8 | 759 + 180 + 8 |
| Tổng mọi loại | 5 703 | 25 001 |

Overture nhiều quán hơn OSM 1.7× (Hà Giang) đến ~10× (Ninh Bình), gần như toàn bộ từ nguồn
Meta (5 652 / 5 703 và 24 842 / 25 001 bản ghi, giấy phép CDLA-Permissive-2.0), nhưng nhiễu: trong
12 "quán cà phê" mẫu có "Thiết kế thi công trọn gói toàn quốc", "Xã Hạnh Phúc", "Long Khánh, Bảo
Yên, Lào Cai". Ít cây xăng hơn OSM. Tên tiếng Việt có dấu đầy đủ ("Cafe Trúc Quán", "Nhà Nga Tây –
Cà Phê & Hoa Quả").

**Kết luận đo đạc:** khoảng trống chính là **bộ lọc build** (0 so với 140 quán OSM trong Tuyên
Quang). Khoảng trống thứ hai là **độ phủ của OSM** (Overture cao hơn, các nhà cung cấp Việt Nam tự
nhận cao hơn nhiều, ví dụ Vietmap "9 362 472+ địa điểm" [S2], nhưng chưa đo được nếu không có khoá).

## 2. Tiêu chí đánh giá

Dự án: phi thương mại, dữ liệu trên máy, sau này có server; mọi dịch vụ bản đồ phải đi qua
interface của mình + adapter theo nhà cung cấp (R35, R36); bản đồ hiển thị là MapLibre + OpenFreeMap,
**không phải** bản đồ Google/Mapbox. Vì vậy các điểm quyết định:

1. **Độ phủ POI ở Việt Nam** (quán cà phê, homestay, quán ăn, cây xăng ở Hà Giang / Ninh Bình).
2. **Tiếng Việt**: tên có dấu, tìm không dấu ("ca phe" ra "Cà phê"), từ loại tiếng Việt ("cây xăng").
3. **Được lưu kết quả không** (bước của chuyến đi lưu toạ độ + tên vĩnh viễn trong Room) và **được
   dùng offline không**.
4. **Được hiển thị trên bản đồ không phải của họ** (MapLibre/OpenFreeMap).
5. **Giá / gói miễn phí**, đăng ký, khoá đặt trong app hay phải qua server.
6. **Hoàng Sa / Trường Sa** (R30) nếu thấy được.

## 3. Bảng so sánh

Ký hiệu: ✅ được / có; ❌ không; ⚠️ có điều kiện; ❓ không tìm thấy công khai (phải hỏi nhà cung cấp
hoặc cần khoá để thử). Giá quy đổi: chỉ để so sánh, theo đơn vị gốc của nguồn.

| Nhà cung cấp | Nhóm | Độ phủ POI VN (đo / tự nhận) | Tiếng Việt | API tìm | Miễn phí | Giá / 1 000 lượt | Lưu kết quả / offline | Bản đồ khác (MapLibre) | Ghi công | Đăng ký | Hoàng Sa / Trường Sa | Nguồn |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Vietmap Maps API** (VIETMAP) | VN, dữ liệu riêng | Tự nhận 9 362 472+ địa điểm, 18 168 574+ địa chỉ; chưa đo (cần khoá) | ✅ dữ liệu VN; có địa giới cũ + mới (`display_type=6`) | Autocomplete v4, Search v4, Place v4, Reverse v4; lọc `layers=POI`, `cats=<mã loại>`; GET, `apikey` trong query | Dùng thử 2 tháng: < 60 000 trans/tháng, 500 trans/ngày cho Autocomplete (trang chủ); trang tài liệu ghi "60,000 free transactions — around 3,000,000 ₫" | 50 ₫/trans (1–500 000/tháng) = **50 000 ₫**, giảm dần đến 24 ₫ | ⚠️ tài liệu: "You may implement caching"; khuyên lưu lat/lng, không lưu `ref_id` lâu dài; điều khoản dịch vụ không tìm thấy (link "Điều khoản" trỏ về chính sách riêng tư) ❓ offline | ❓ không ghi | ❓ | Có, không cần thẻ | ❓ trang chủ có mục "Địa lý địa phương" (yếu tố địa chính trị nhạy cảm), không nêu tên | [S1] [S2] [S3] [S4] [S5] |
| **Goong** (goong.io) | VN, dữ liệu riêng | Chưa đo (cần khoá); không công bố số POI | ✅ ("ho hoan kiem" trong ví dụ); khuôn API giống Google | `rsapi.goong.io/place/autocomplete` (`input`, `location`, `radius`, `limit`, `more_compound`), Place Detail, Geocode; `api_key` trong query | 100 USD tín dụng một lần khi đăng ký; gói Lite 30 000 lượt/tháng (1 000/ngày) | Autocomplete **1,40 USD**; PlaceDetail 7,00 USD; Forward geocode 3,50 USD | ❓ không tìm thấy điều khoản API (trang "terms" là EULA phần mềm dẫn đường, cấm dùng với "software program other than the Positioning System") | ❓ | ❓ | Có tài khoản; tắt khi chi phí ước tính > 25 % số dư | ❓ blog Goong cảnh báo phạt dữ liệu sai chủ quyền | [S6] [S7] [S8] [S9] |
| **Map4D** (IOTLink) | VN, dữ liệu riêng | Chưa đo (cần khoá) | ❓ (SDK VN) | Auto suggest, Geocode, Place search, Viewbox search, Nearby search, Place detail (trang API render bằng JS, không đọc được) | Bảng giá: "Đối với bản Beta (dùng thử), Map4D cung cấp dịch vụ miễn phí 100%" — không nêu hạn mức | Auto suggest **26 000 ₫**; Place search / Nearby **50 000 ₫**; Geocode 47 000 ₫ (1–500 000 lượt) | ❓ điều khoản không nói về lưu/cache | ❓ | Cấm xoá/che logo, thông báo pháp lý | Khoá tại `map.map4d.vn/user/access-key` | ❓ | [S10] [S11] [S12] |
| **TrackAsia** | VN | Chưa đo; không nêu nguồn dữ liệu | ✅ (địa giới cũ + mới, "Convert Address") | Autocomplete v2 ("Compatible with Google API"), Search v2, Nearby Search (lọc loại, bán kính), Place Detail v2, Reverse v2 | Không thấy gói miễn phí cho tìm kiếm | Theo gói tháng (M1: 100 000 lượt Search/Autocomplete/Geocode/Reverse); trang bảng giá 404 khi truy cập — **chưa kiểm chứng** số tiền | ❓ | ❓ | ❓ | Có | ❓ | [S13] [S14] |
| **eKGIS / eKMap** | VN, GIS cho nhà nước/doanh nghiệp | ❓ | ❓ | eKMap SaaS + API (không thấy tài liệu tìm địa điểm công khai) | Plugin WordPress: "liên hệ để được cung cấp khoá miễn phí" | ❓ | ❓ | ❓ | ❓ | Liên hệ | ❓ | [S15] |
| **Vietbando** | VN | ❓ | ❓ | "Maps API" cho web (không thấy tài liệu tìm địa điểm) | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | [S16] |
| **Viettel Maps** | VN | Ứng dụng có "tìm kiếm địa điểm, địa chỉ, danh mục" | ✅ (ứng dụng) | ❌ không thấy API công khai cho nhà phát triển | — | — | — | — | — | — | ❓ | [S17] |
| **VNPost Vpostcode / Nền tảng địa chỉ số** | VN, nhà nước | Mã địa chỉ (23 triệu địa chỉ, 2020), **không phải POI** | ✅ | Có nói "API mã địa chỉ" tích hợp cổng dịch vụ công; không thấy tài liệu công khai | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | [S18] [S19] |
| **Nền tảng bản đồ số quốc gia** (QĐ 1137/QĐ-BTTTT, 2022) | VN, nhà nước | Tiêu chí gồm dữ liệu điểm (địa chỉ, địa điểm) | ✅ | "Tích hợp không giới hạn … qua các cổng APIs" — không thấy cổng nhà phát triển | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | [S20] |
| **Zalo** | VN | — | — | ❌ không thấy API bản đồ/tìm địa điểm công khai | — | — | — | — | — | — | — | [S21] |
| **GrabMaps** (qua Amazon Location Service) | Đông Nam Á, dữ liệu từ xe Grab | VN là 1 trong 8 nước; chưa đo (cần tài khoản AWS) | ❓ | Amazon Location Places: SearchText, Suggest, GetPlace, Reverse; vùng ap-southeast-1/5 | 3 tháng đầu: 10 000 Suggest + 20 000 Search/Get Place mỗi tháng | Không đọc được bảng giá Places (trang dẫn sang developer guide) — **chưa kiểm chứng** | ❓ không đọc được điều khoản lưu kết quả cho GrabMaps — **chưa kiểm chứng** | ❓ | Theo điều khoản Grab (mục 9.23) | Tài khoản AWS + thẻ | ❓ | [S22] [S23] [S24] |
| **Google Places API (New)** | Quốc tế | Thường được coi là phủ dày nhất (nhận định, **chưa kiểm chứng**); chưa đo (cần khoá) | ✅ | Autocomplete, Text Search, Nearby Search, Place Details | 10 000 Autocomplete; 5 000 Text Search Pro; 10 000 Place Details Essentials / tháng | Autocomplete **2,83 USD**; Text Search Pro 32 USD; Nearby Pro 32 USD; Details Essentials 5 USD | ❌ chỉ lưu `place_id` vô hạn; lat/lng tối đa 30 ngày; cấm "pre-fetch, index, store" | ❌ **"Customer must not use Google Maps Content from the Places API in conjunction with a non-Google map"** | Logo Google Maps | Tài khoản Google Cloud + thẻ | ❓ | [S25] [S26] [S27] |
| **HERE Geocoding & Search** | Quốc tế | Chưa đo (cần khoá) | ❓ | Discover, Autosuggest, Browse, Lookup, Geocode | 30 000 giao dịch/tháng (Base Plan) | Discover/Search 0,66 EUR; Geocode 0,83 USD (theo trích đoạn tìm kiếm của trang Base Plan; trang không mở được từ mạng này — **chưa kiểm chứng**) | ❌ mặc định không lưu quá 30 ngày; "Permanent Geocoding" là giấy phép thêm, không có trong Base Plan | ❓ | ❓ | Tài khoản | ❓ | [S28] [S29] |
| **Foursquare Places API** | Quốc tế | Chưa đo (cần khoá) | ❓ | Place Search, Autocomplete, Place Details (Pro); Tips/Photos (Premium) | 0–500 lượt Pro/tháng 0 USD (trang khác ghi "up to 10,000 free calls on Pro") | Pro **15 USD**, Premium 18,75 USD | ❌ Pay-as-you-go/Sandbox: "no caching permitted" (chỉ ID được lưu) | ✅ không cấm | Bắt buộc ghi "Foursquare" | Tài khoản | ❓ | [S30] [S31] |
| **Mapbox Search Box** | Quốc tế | ❌ **"supports the United States, Canada, and Europe"** — không có VN | ❌ không có `vi` trong danh sách ngôn ngữ | `/suggest`, `/retrieve`, `/category` | 2 500 phiên hoặc 25 000 lượt/tháng (giá chuẩn) | 11,50 USD/1 000 phiên; 1,70 USD/1 000 lượt | ❌ "only available for temporary use" | ⚠️ Geocoding v6: "only … in conjunction with a Mapbox map" (Geocoding v6 không còn POI) | Có | Tài khoản | — | [S32] [S33] [S34] |
| **TomTom Search** | Quốc tế | Chưa đo (cần khoá) | ❓ | Fuzzy Search, POI Search, Nearby, Category | 2 500 lượt/tháng mỗi API, không cần thẻ | Không thấy đơn giá trên trang (chỉ bậc 2,5K/100K/500K/1M) — **chưa kiểm chứng** | ❓ điều khoản cần đăng nhập; bên thứ ba nói cấm lưu — **chưa kiểm chứng** | ❓ | ❓ | Tài khoản | ❓ | [S35] |
| **Nominatim** (OSMF, `nominatim.openstreetmap.org`) | OSM | = OSM (bảng 1.1) | ✅ tên OSM `name:vi` | `/search`, `/reverse`; ❌ **cấm autocomplete**: "you must not implement such a service on the client side" | Miễn phí, tối đa 1 lượt/giây | 0 | ✅ "Results must be cached on your side" (ODbL) | ✅ | ODbL "© OpenStreetMap" | Không | Dữ liệu OSM có tên hành chính TQ quanh Hoàng Sa (AGENTS Traps R30) → phải lọc | [S36] |
| **Photon** (komoot, `photon.komoot.io`) | OSM | = OSM; demo bên dưới | ⚠️ gõ không dấu khớp có dấu ("ca phe" → "Cà phê Cao Nguyên"); từ loại tiếng Việt **không** được hiểu ("cây xăng" ở Hà Giang: 0 kết quả; "coffee" thì ra cả quán không tên) | `/api?q=&lat=&lon=&bbox=&osm_tag=` — có gõ-đến-đâu-gợi-ý-đến-đó | Miễn phí, "extensive usage will be throttled", không cam kết sẵn sàng | 0 | ✅ ODbL | ✅ | ODbL | Không | "Trường Sa" → Việt Nam / Khánh Hòa; "Quần đảo Hoàng Sa" không tìm ra | [S37] |
| **Pelias** (tự host) / Geocode Earth (bản host) | OSM + OpenAddresses + WOF + Geonames + CSV | = dữ liệu mình nạp (có thể nạp cả Overture/CSV) | ⚠️ phụ thuộc phân tích tiếng Việt của Elasticsearch | `/v1/autocomplete`, `/v1/search`, `/v1/reverse` | Mã nguồn MIT; Geocode Earth: dùng thử 2 tuần | Geocode Earth Lite 100 USD/150 000 lượt (≈ 0,67 USD) | ✅ Geocode Earth: "Store geocoded results as long as you need" | ✅ | ODbL cho phần OSM | Tự host: không; Geocode Earth: khoá | Phụ thuộc dữ liệu nạp | [S38] [S39] |
| **Overpass API** | OSM | = OSM (đo ở 1.1) | ✅ (truy vấn theo thẻ, không theo chữ) | Ngôn ngữ truy vấn Overpass QL; không phải API tìm kiếm cho người dùng | Miễn phí, máy chủ hay 502/504 khi tải nặng (gặp 3 lần trong lúc đo) | 0 | ✅ ODbL | ✅ | ODbL | Không | Như OSM | [S40] |
| **Gói POI OSM offline** (mở rộng `build_gazetteer.py`) | OSM, trên máy | Hà Giang cũ ~138 quán cà phê, Ninh Bình cũ ~92 (bảng 1.1) | ✅ đã có bộ chuẩn hoá R23 (không dấu, đ→d) | `Gazetteer.search` hiện có + loại mới | Miễn phí | 0 | ✅ offline, ODbL (đã ghi công) | ✅ | ODbL (đã có) | Không | Đã có lọc R30 khi build | [S41] |
| **Overture Maps Places** (dữ liệu mở, offline) | Meta + Microsoft + Foursquare + AllThePlaces | Hà Giang cũ 238 quán cà phê, Ninh Bình cũ 946 (bảng 1.1); nhiễu | ✅ tên có dấu | File GeoParquet theo tháng (không phải API) | Miễn phí | 0 | ✅ CDLA-Permissive-2.0 (Foursquare: Apache-2.0) | ✅ | Ghi nguồn từng bộ dữ liệu; Foursquare: "Copyright 2024 Foursquare Labs, Inc." | Không | ❓ chưa khảo sát | [S42] [S43] |

## 4. Đánh giá ngắn

* **Đơn vị Việt Nam có bán dịch vụ này** — câu trả lời cho câu hỏi của thuyền trưởng là **có**:
  Vietmap, Goong, Map4D, TrackAsia đều có API gợi ý/tìm địa điểm (POI) cho Việt Nam, giá rẻ hơn
  Google nhiều (Vietmap 50 ₫, Map4D 26 ₫/lượt gợi ý, Goong 1,40 USD/1 000; Google 2,83 USD/1 000
  chỉ cho autocomplete). Viettel Maps, VNPost, Zalo, nền tảng nhà nước: không thấy API công khai.
* **Điều chưa biết với các đơn vị Việt Nam là điều khoản**: không đơn vị nào công bố rõ được lưu
  kết quả vĩnh viễn, dùng offline, hiển thị trên bản đồ khác và ghi công thế nào. Vietmap là đơn vị
  duy nhất viết "You may implement caching" và khuyên lưu lat/lng. Phải hỏi bằng văn bản trước khi
  dùng.
* **Khoá trong app**: Vietmap khuyên gọi qua backend ("Backend calls Autocomplete v4") và giữ khoá
  dịch vụ trên server; Goong cho phép giới hạn khoá theo IP/website. Một app Android không server
  sẽ phải nhúng khoá → lộ khoá. Hợp với quyết định "server sau".
* **Quốc tế**: Google tốt nhất về dữ liệu nhưng **cấm dùng với bản đồ không phải Google** và cấm lưu
  → không hợp với MapLibre + bước lưu trong Room. Mapbox Search Box **không phủ Việt Nam**. HERE và
  Foursquare cấm lưu lâu dài. TomTom, HERE chưa kiểm chứng đủ.
* **OSM**: miễn phí, được lưu, offline được, đã có ghi công; nhưng mỏng (140 quán cà phê ở cả Tuyên
  Quang, 60 có tên). Nominatim cấm autocomplete; Photon cho phép nhưng không cam kết và không hiểu từ
  loại tiếng Việt.
* **Overture**: dày hơn OSM (×1.7 ở Hà Giang), giấy phép cho lưu và offline, nhưng nhiễu và cần lọc
  theo `confidence` + loại; chưa khảo sát Hoàng Sa/Trường Sa.

## 5. Đề xuất

**Bước 1 — ngay, offline, không phụ thuộc ai (nhánh code sau):** mở rộng `build_gazetteer.py`
thêm các loại POI du lịch: `amenity=cafe|restaurant|fuel`, `tourism=hotel|guest_house|hostel|motel`
(+ tên chứa "homestay"), có lẽ chỉ khi có `name` (hoặc đặt tên theo loại "Quán cà phê" cho quán
không tên). Ước lượng thêm vài nghìn dòng cho cả nước (Tuyên Quang + Ninh Bình mới:
140+114 quán cà phê, 216+299 nhà hàng, 101+137 cây xăng, 480+327 nơi lưu
trú); cần đo lại kích thước APK. Thêm từ loại tiếng Việt vào tìm kiếm ("cà phê", "cafe", "quán ăn",
"cây xăng", "homestay", "nhà nghỉ") để "ca phe dong van" ra các quán ở Đồng Văn.

**Bước 2 — tuỳ chọn, offline:** thử trộn Overture Places (lọc `confidence ≥ 0.5`, loại du lịch,
bỏ trùng với OSM trong ~50 m) vào cùng file; cần quyết định về ghi công (CDLA + Apache) và kích
thước.

**Bước 3 — khi có server (theo quyết định "server sau"):** một `PlaceSearch` trực tuyến qua server
của mình, adapter đầu tiên là **một nhà cung cấp Việt Nam** (Vietmap hoặc Goong/Map4D) sau khi họ
trả lời bằng văn bản các câu hỏi ở mục 6. Không dùng Google Places/Mapbox (điều khoản) cho bản đồ
MapLibre.

### 5.1 Cắm vào app: interface `PlaceSearch` + adapter (giống R35/R36)

```
domain/places/                     (thuần Kotlin, không biết nhà cung cấp)
  PlaceSearch            interface: suspend fun search(q: PlaceQuery): List<PlaceHit>
                                    val source: PlaceSource (OFFLINE | ONLINE), val credit: String
  PlaceQuery             text, near (GeoPoint?), bbox?, kinds (CAFE, FOOD, FUEL, LODGING, …), limit
  PlaceHit               name, kind, point, province, sourceId (id của nhà cung cấp), credit
  CompositePlaceSearch   gọi offline trước (luôn có), rồi online nếu bật + có mạng; gộp trùng
                         (cùng tên chuẩn hoá trong 100 m), offline thắng khi bằng điểm
places/                            (adapter; chỉ ở đây có URL, khoá, User-Agent)
  offline/GazetteerPlaceSearch     bọc Gazetteer.search hiện tại (R23), không đổi hành vi
  vietmap/VietmapPlaceSearch       Autocomplete v4 (layers=POI, focus=điểm gần) → Place v4 khi chọn
  goong/GoongPlaceSearch           …
  PlaceProviders.kt                đăng ký + URL duy nhất (như Routers.kt); test ranh giới như
                                   RoutingBoundaryTest giữ tên host trong gói này
```

Quy tắc đề xuất cho phần online (cần thuyền trưởng duyệt trước khi thành R mới):

* **Offline trước**: R23 vẫn chạy trong chế độ máy bay; online chỉ thêm kết quả.
* **Một thông báo + công tắc** trong "Cài đặt" như R36: chỉ chữ đang gõ + vị trí gần (để xếp hạng)
  được gửi; không gửi khi tắt.
* **Debounce ≥ 300 ms, tối thiểu 2–3 ký tự**, chỉ gọi "chi tiết" khi người dùng chọn (Vietmap: Place
  v4 chỉ một lần cho kết quả được chọn).
* **Lưu**: bước chỉ lưu toạ độ + tên đã chọn (đã là dữ liệu của người dùng); không cache danh sách
  kết quả trừ khi điều khoản cho phép.
* **Ghi công**: dòng dưới danh sách kết quả theo nguồn ("Địa danh © OpenStreetMap (ODbL)" như nay,
  thêm "· Gợi ý: <nhà cung cấp>").
* **R30**: lọc kết quả có tên hành chính nước ngoài quanh Hoàng Sa/Trường Sa bằng
  `SovereigntyLabels` trước khi hiển thị, với mọi nguồn.

## 6. Câu hỏi còn mở (cần khoá / tài khoản / hỏi nhà cung cấp)

Không làm trong nhiệm vụ này (không tạo tài khoản, không khoá, không gọi trả phí). Mỗi việc dưới đây
cần thuyền trưởng quyết định:

| Nhà cung cấp | Cần gì | Sẽ cho biết |
| --- | --- | --- |
| Vietmap | khoá dùng thử (đăng ký, không thẻ) | số quán cà phê/homestay/cây xăng thật ở Đồng Văn, Tam Cốc; chất lượng gợi ý không dấu; tên ở Hoàng Sa/Trường Sa |
| Goong | tài khoản (100 USD tín dụng) | như trên + điều khoản API thật trong console |
| Map4D | khoá (bản Beta miễn phí) | như trên |
| TrackAsia | tài khoản | giá thật, nguồn dữ liệu (OSM hay riêng) |
| Google / HERE / TomTom / Foursquare / GrabMaps (AWS) | tài khoản + thẻ | độ phủ so sánh (chỉ để tham chiếu, điều khoản đã loại phần lớn) |
| Vietmap, Goong, Map4D (email) | câu hỏi bằng văn bản | được lưu kết quả vĩnh viễn trên máy? dùng offline? hiển thị trên MapLibre/OSM? ghi công thế nào? app phi thương mại có giá riêng không? |

## 7. Cách tái lập số liệu

* Script đếm Overpass (zsh; mỗi truy vấn một số, thử lại tối đa 4 lần, nghỉ 3 s):
  vùng `area(3601903418)` (Tuyên Quang), `area(3601900963)` (Ninh Bình), hộp
  `(22.13,104.33,23.40,105.58)` (Hà Giang cũ), `(19.84,105.53,20.48,106.17)` (Ninh Bình cũ); bộ lọc
  như bảng 1.1; `curl -A 'osm-poi-count/1.0 (research script)' --data-urlencode "data=<QL>"
  https://overpass-api.de/api/interpreter`. Id relation lấy bằng
  `rel["boundary"="administrative"]["admin_level"="4"]["name"~"Tuyên Quang|Ninh Bình"];out tags;`.
* Overture (Python + `duckdb`, không khoá):

  ```sql
  INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2';
  SELECT basic_category, count(*), count(*) FILTER (WHERE confidence >= 0.5)
  FROM read_parquet('s3://overturemaps-us-west-2/release/2026-10-21.0/theme=places/type=place/*',
                    hive_partitioning=1)
  WHERE bbox.xmin BETWEEN 104.33 AND 105.58 AND bbox.ymin BETWEEN 22.13 AND 23.40
  GROUP BY 1;
  ```
* Photon (demo không khoá, ≤ 1 lượt/giây):
  `https://photon.komoot.io/api/?q=coffee&bbox=104.33,22.13,105.58,23.40&limit=50` → 42 kết quả
  (30 `amenity=cafe`); `q=cafe` → 10; `q=cà phê` → 3; `q=cây xăng` → 0 (Hà Giang) và 6 (Ninh Bình,
  đều `amenity=fuel`); `q=homestay` → 50 (giới hạn).
* `nominatim.openstreetmap.org` không mở được từ mạng này (TLS bị reset, như `*.openstreetmap.org`
  trong AGENTS.md Traps) — không đo được.

## 8. Nguồn (tất cả truy cập 2026-11-10)

* [S1] Vietmap Maps API overview — https://maps.vietmap.vn/docs/map-api/overview/ ("60,000 free
  transactions", "You may implement caching", khoá dịch vụ chỉ dùng ở backend)
* [S2] Vietmap trang chủ, bảng giá + số liệu dữ liệu — https://maps.vietmap.vn/web (50 ₫/trans …,
  dùng thử 2 tháng, 9 362 472+ địa điểm, "Địa lý địa phương")
* [S3] Vietmap quy đổi request → transaction —
  https://maps.vietmap.vn/docs/vi/map-api/console/request-to-transaction/
* [S4] Vietmap Search & Geocoding (tài liệu cho agent: Autocomplete/Search/Place v4, `cats`,
  `layers`, luồng qua backend, không lưu `ref_id` lâu dài) —
  https://maps.vietmap.vn/docs/assets/agents/search-geocoding.txt
* [S5] Vietmap chính sách riêng tư (link "Điều khoản dịch vụ" trỏ về đây) —
  https://maps.vietmap.vn/web/privacy-policy
* [S6] Goong bảng giá — https://goong.io/pricing-plans/
* [S7] Goong trợ giúp (khoá, 100 USD, gói, giới hạn, tắt tài khoản 25 %) — https://help.goong.io/
* [S8] Goong Autocomplete API — https://help.goong.io/kb/rest-api/autocomplete/autocomplete-tu-dong-hoan-thanh-dia-diem/
* [S9] Goong "terms" (EULA phần mềm dẫn đường) — https://goong.io/en/terms/ ; chính sách riêng tư
  https://goong.io/en/policy/ ; `docs.goong.io` trả "Site not found · GitHub Pages" (404)
* [S10] Map4D / IOTLink bảng giá API (từ 01/06/2025) — https://www.iotlink.com.vn/bang-gia/
* [S11] Map4D / IOTLink tài liệu (mục lục) — https://iotlink.com.vn/tai-lieu (docs.map4d.vn chuyển
  hướng về đây; https://docs.map4d.vn/map4d-service/api render bằng JS)
* [S12] IOTLink điều khoản sử dụng — https://www.iotlink.com.vn/dieu-khoan-su-dung/
* [S13] TrackAsia tích hợp API — https://docs.track-asia.com/api-integration/
* [S14] TrackAsia "Top 10 maps API providers for Vietnam" (bài của TrackAsia, có thiên vị) —
  https://track-asia.com/en/news/news/top-10-maps-api-providers-for-vietnam-international-local-options ;
  trang giá https://track-asia.com/en/price-list trả 404
* [S15] eKMap Store Locator (WordPress, "liên hệ để được cung cấp khoá miễn phí") —
  https://wordpress.org/plugins/ekmap-store-locator/
* [S16] Vietbando (FPT Shop giới thiệu, có "Maps API") — https://fptshop.com.vn/tin-tuc/giai-tri/vietbando-171023
* [S17] Viettel Maps trên App Store — https://apps.apple.com/vn/app/viettel-maps/id1501635906
* [S18] Vpostcode (Bộ TT&TT, 23 triệu địa chỉ) —
  https://english.mic.gov.vn/vpostcode-system-provides-exact-addresses-based-on-national-database-197144652.htm
* [S19] Vpostcode, API mã địa chỉ — https://vietnamnet.vn/khai-thac-ma-dia-chi-buu-chinh-vpostcode-de-phuc-vu-cho-phat-trien-kinh-te-xa-hoi-704405.html
* [S20] Nền tảng bản đồ số quốc gia (QĐ 1137/QĐ-BTTTT) —
  https://lsvn.vn/thuc-day-phat-trien-va-su-dung-nen-tang-ban-do-so-quoc-gia1656059905-a120398.html
* [S21] Tìm "Zalo bản đồ API tìm kiếm địa điểm developers.zalo.me": không có kết quả chính thức (tìm
  kiếm web 2026-11-10; không có URL nào để dẫn)
* [S22] Amazon Location Service + GrabMaps (2026-03) —
  https://aws.amazon.com/about-aws/whats-new/2026/03/amazon-location-service-grabmaps
* [S23] Amazon Location pricing (gói miễn phí 3 tháng) — https://aws.amazon.com/location/pricing/
* [S24] Amazon Location GrabMaps developer guide (thao tác Places, điều khoản Grab 9.23) —
  https://docs.aws.amazon.com/location/latest/developerguide/GrabMaps.html
* [S25] Google Maps Platform giá — https://developers.google.com/maps/billing-and-pricing/pricing
* [S26] Google Maps Platform Service Specific Terms (14.2 "No use with a non-Google map") —
  https://cloud.google.com/maps-platform/terms/maps-service-terms ; ToS 3.2.3(a) "pre-fetch, index,
  store" — https://cloud.google.com/maps-platform/terms
* [S27] Google Places policies (place ID lưu vô hạn, lat/lng 30 ngày, ghi công) —
  https://developers.google.com/maps/documentation/places/web-service/policies
* [S28] HERE Base Plan (trang https://developers.here.com/plans lỗi TLS từ mạng này; số liệu từ trích
  đoạn công cụ tìm kiếm; https://www.here.com/pricing render bằng JS) — **chưa kiểm chứng tận nơi**
* [S29] HERE KB "Permanent Geocoding – Overview, Licensing, and Usage Rules" (30 ngày, không có
  trong Base Plan) — https://docs.here.com/here-kb/docs/permanent-geocoding-overview-licensing-and-usage-rules
  (WebFetch trả 404; nội dung từ trích đoạn tìm kiếm) — **chưa kiểm chứng tận nơi**
* [S30] Foursquare giá — https://docs.foursquare.com/developer/reference/pricing
* [S31] Foursquare usage guidelines (cache, ghi công) —
  https://docs.foursquare.com/developer/reference/usage-guidelines-personalization-apis
* [S32] Mapbox giá — https://www.mapbox.com/pricing
* [S33] Mapbox Search Box API (phủ US/Canada/châu Âu, ngôn ngữ, "temporary use") —
  https://docs.mapbox.com/api/search/search-box/
* [S34] Mapbox Geocoding API (temporary/permanent, "in conjunction with a Mapbox map", v6 không POI) —
  https://docs.mapbox.com/api/search/geocoding/
* [S35] TomTom giá (2 500 lượt/tháng/API, không thẻ) — https://docs.tomtom.com/pricing
* [S36] Nominatim usage policy — https://operations.osmfoundation.org/policies/nominatim/
* [S37] Photon (komoot) — https://photon.komoot.io/
* [S38] Pelias — https://github.com/pelias/pelias
* [S39] Geocode Earth (giá, "Store geocoded results as long as you need") — https://geocode.earth/
* [S40] Overpass API (máy chủ dùng để đo) — https://overpass-api.de/api/interpreter ; dữ liệu OSM ©
  OpenStreetMap contributors, ODbL — https://www.openstreetmap.org/copyright
* [S41] Gazetteer hiện tại — `tools/gazetteer/README.md`, `tools/gazetteer/build_gazetteer.py`
  (`kind_of`), docs/04 R23
* [S42] Overture Maps attribution/giấy phép — https://docs.overturemaps.org/attribution/
* [S43] Overture Places bản 2026-10-21.0 —
  `s3://overturemaps-us-west-2/release/2026-10-21.0/theme=places/type=place/`
  (danh sách bản: https://overturemaps-us-west-2.s3.amazonaws.com/?list-type=2&prefix=release/&delimiter=/)
