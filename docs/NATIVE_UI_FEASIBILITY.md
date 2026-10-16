# Tamamen native Instagram arayüzü: uygulanabilirlik

Değerlendirme tarihi: 16 Ekim 2026 (Europe/Berlin).

## Sonuç

Kişisel Instagram hesabıyla takip edilenlerin akışını, hikâyelerini ve mevcut
bireysel/grup konuşmalarını koruyan tamamen native bir istemci için, incelenen
resmî Instagram API'lerinde yeterli erişim bulunamadı. İlk proje şartları olan
özel API kullanmama, otomatik veri kazımama ve kimlik doğrulama kısıtlarını
aşmama korunursa, mevcut ürünün tamamını bu yöntemle yeniden uygulayamıyoruz.

Bu bir Kotlin/Android tasarım kısıtı değil, veri erişimi kısıtı. WebView içinde
Instagram'a giriş yapılmış olması, uygulamaya bu verileri resmî API üzerinden
okuma yetkisi kazandırmaz. Profesyonel hesaba geçmek veya bir backend kurmak
da eksik arkadaş akışı ve grup mesajlaşması erişimini sağlamaz.

## İstenen özelliklerin durumu

| İhtiyaç | Resmî erişim değerlendirmesi |
| --- | --- |
| Kişisel hesapla bağımsız native istemci | Belgelenen Instagram Login API'si Business/Creator hesaplarına yöneliktir. Facebook Login API'si consumer hesaplarına erişemediğini açıkça belirtir. |
| Takip edilen arkadaşların ana sayfa akışı | Bu kullanım için belgelenmiş resmî bir uç nokta bulunamadı. Bir hesabın kendi medyasını yönetmek, takip akışına erişmek değildir. |
| Takip edilen arkadaşların hikâyeleri | Bu kullanım için belgelenmiş resmî erişim bulunamadı. Bir profesyonel hesabın kendi hikâyelerini yönetmesi/yayınlaması farklı bir özelliktir. |
| Kişisel hesabın mevcut bireysel mesaj kutusu | Mesaj API'si profesyonel hesap için tasarlanmıştır; kişisel hesabın genel mesaj kutusunun yerine geçmez. |
| Mevcut grup konuşmaları | Belgelerde grup mesajlaşmasının desteklenmediği açıkça belirtilir. |

Mesaj API'si hiç yok değildir: uygun profesyonel hesap, desteklenen koşullarda
kendisini mesajla başlatan kullanıcılarla bire bir iletişim kurabilir. Bu
kişiler kişisel hesap sahibi olabilir. Kısıt, uygulamaya bağlanan hesabın türü
ve desteklenen konuşma modelidir; kişisel hesabın tüm Direct deneyimine erişim
olarak yorumlanmamalıdır.

## Kaynaklar ve kanıt sınırı

Meta'nın kendi Postman Instagram API koleksiyonunun arama dizinine alınmış
belge metinleri incelendi:

1. [Instagram API with Instagram Login](https://www.postman.com/meta/instagram/folder/1z5vxzu/instagram-api-with-instagram-login)
   — kapsam: profesyonel Business/Creator hesapları.
2. [Instagram API with Facebook Login](https://www.postman.com/meta/instagram/folder/u4g5a2a/instagram-api-with-facebook-login)
   — consumer hesaplarına erişim olmadığını açıkça belirtir.
3. [Meta Instagram API: mesajlaşma](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api?entity=request-23987686-3a63181c-e5c6-4890-96dd-e4d425bef5e0)
   — grup mesajlaşması desteklenmez; alıcının profesyonel hesaba daha önce mesaj
   göndermiş olması koşulu belirtilir.

Doğrudan developers.facebook.com belge sayfaları bu ortamda HTTP 429 döndürdü.
Postman sayfalarının doğrudan açılışı JavaScript kabuğunu gösterdi; ilgili
metinler arama dizinindeki Meta koleksiyonundan doğrulandı. Bu değerlendirme,
belgelenmiş API kapsamına dayanır; gerçek hesapla API yetki testi yapılmadı.
Takip akışı/hikâyeleri için sonuç, belgelerde bu kullanımın bulunmamasıdır;
Meta'nın gelecekte yeni erişim sunamayacağı iddia edilmez.

## Uygulanabilir tasarım sınırı

Bu sonuç, tamamen native Instagram istemcisinin teknik olarak imkânsız olduğu
anlamına gelmez. Sonuç, başlangıçtaki resmî API/özel API/veri kazıma/APK
değiştirmeme şartları birlikte korunurken mevcut özelliklerin tamamı için
yeterli resmî veri erişimi bulunmadığıdır. Özgün bir görsel tasarım ise web
tabanlı içerik üzerinde de uygulanabilir.

Şu parçalar tamamen bize ait native Android bileşenleri olabilir: uygulama üst
çubuğu, alt gezinme, filtre/ayar ekranları, tema, tipografi, simgeler ve
yükleme/hata durumları. WebView içindeki görünüm sınırlı, geri alınabilir
CSS kurallarıyla değiştirilebilir. Ancak akış, hikâye oynatıcısı ve Direct
etkileşimleri hâlâ Instagram'ın web öğeleridir; bu çözüm tamamen native değildir.

Web sayfasından gönderi/mesaj verilerini çıkarıp native kartlara aktarmak,
başlangıçtaki otomatik veri kazımama şartıyla çelişir. Ekranları sahte verilerle
çizmek de çalışan bir Instagram istemcisi sağlamaz. Bu yollar uygulanmadı.

Kullanıcı mevcut sürümün telefonunda düzgün çalıştığını bildirdi. Bu bildirim,
her bir özellik için ayrı ve tekrarlanabilir doğrulama yapıldığı anlamına
gelmez. Bu değerlendirmede çalışan uygulamanın kodu, oturumu veya görünümü
değiştirilmedi; yeni APK üretilmedi.

## Benzer uygulamalar: yöntem karşılaştırması

16 Ekim 2026'da kullanıcının verdiği örneklerin Play açıklamaları, geliştirici
belgeleri ve açık kaynak depoları incelendi. Uygulamalar kurulmadı ve gerçek
hesapla test edilmedi. Kapalı kaynak uygulamalarda yöntem açıklaması geliştirici
beyanıdır; bağımsız bir kod denetimi sayılmaz.

| Örnek | Gözlenen yöntem | Tamamen bize ait native istemci için anlamı |
| --- | --- | --- |
| [Awhile](https://awhile.app/) / [Google Play](https://play.google.com/store/apps/details?id=com.arckone.awhile) | Geliştirici FAQ'sı Instagram'ı uygulama içi tarayıcıda açıp CSS enjekte ettiğini açıkça belirtir. | Friends Only'nin WebView yaklaşımıyla aynı sınıftadır; bağımsız native akış/mesaj veri katmanının kanıtı değildir. |
| [ScrollGuard](https://scrollguard.app/) / [Google Play](https://play.google.com/store/apps/details?id=com.luderapp.scrollguard) | Android'de mevcut sosyal medya uygulamalarının üzerinde çalışır. Play açıklaması Accessibility Service ile kaydırma hareketleri ve kısa videoların algılanıp engellendiğini belirtir. | Instagram verisini alıp kendi akış/mesaj ekranlarında sunmaz; resmî uygulamanın ekranları kullanılmaya devam eder. |
| [FeurStagram](https://github.com/jean-voila/FeurStagram) | Depo resmî Instagram Android APK'sına yama uyguladığını açıkça belirtir. İçerik engelleme ve kendine ait ayarlar ekler. | Native davranışın kaynağı mevcut Instagram uygulamasıdır. Başlangıçtaki resmî APK'yı değiştirmeme/yeniden dağıtmama şartıyla uyuşmaz. |

Awhile'ın ücretli Following Only modu sunduğu doğrulandı; kapalı kaynak
uygulamada bunun tam yönlendirme/filtreleme uygulaması doğrulanmadı. Bu özellik,
arkadaş akışı için bağımsız bir resmî API açıldığını göstermez. FeurStagram'ın
README'si Following modunu resmî uygulamanın akış isteğini değiştiren bir yama
olarak açıklar; bu yöntem projeye uygulanmadı.

Gerçekten ayrı native ekran örneği olarak tarihsel Barinsta kodu da incelendi:
[native Direct ekranı](https://github.com/Android-Builds/barinsta/blob/master/app/src/main/java/awais/instagrabber/fragments/directmessages/DirectMessageInboxFragment.kt),
[ağ yapılandırması](https://github.com/Android-Builds/barinsta/blob/master/app/src/main/java/awais/instagrabber/webservices/RetrofitFactory.kt)
ve [mesaj servisi](https://github.com/Android-Builds/barinsta/blob/master/app/src/main/java/awais/instagrabber/repositories/DirectMessagesService.kt).
Kod Android Fragment/RecyclerView ekranları ve resmî geliştirici Graph API'si
yerine Instagram'ın uygulama içi/private uç noktalarını çağıran Retrofit
servisleri içerir. Bu, native arayüzün teknik olarak yapılabildiğini gösterir;
güncel Instagram sürümüyle çalıştığı veya ilk proje şartlarına uyduğu anlamına
gelmez. Bu servislere hiçbir istek gönderilmedi.

Öneri: Özgün görünüm hedefi için native gezinme/ayarlar ile yerinde web
biçimlendirmesini birlikte tasarlamak uygulanabilir. Tamamen bağımsız native
akış/hikâye/mesaj ekranları için, mevcut şartlarla uyuşan yeni bir veri erişimi
gerekecektir. Rekabet örneklerinin varlığı bu erişimi tek başına sağlamaz.
